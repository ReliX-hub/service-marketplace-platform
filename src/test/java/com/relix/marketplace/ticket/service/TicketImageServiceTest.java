package com.relix.marketplace.ticket.service;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.config.StorageProperties;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.storage.service.ManagedImageIngestService;
import com.relix.marketplace.storage.service.StagedImageFiles;
import com.relix.marketplace.storage.service.StoredFileOwnershipService;
import com.relix.marketplace.ticket.dto.TicketImageResponse;
import com.relix.marketplace.ticket.entity.LocationMode;
import com.relix.marketplace.ticket.entity.PricingMode;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketImage;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketImageRepository;
import com.relix.marketplace.ticket.repository.TicketRepository;
import com.relix.marketplace.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketImageServiceTest {

    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private TicketImageRepository ticketImageRepository;
    @Mock
    private CurrentUserService currentUserService;
    @Mock
    private ManagedImageIngestService managedImageIngestService;
    @Mock
    private StoredFileOwnershipService storedFileOwnershipService;
    @Mock
    private EntityManager entityManager;
    @Mock
    private Query nativeQuery;
    @Mock
    private MultipartFile upload;

    private TicketImageService service;
    private ImageProperties imageProperties;
    private FileUrlService fileUrlService;
    private User author;

    @BeforeEach
    void setUp() {
        imageProperties = new ImageProperties();
        fileUrlService = new FileUrlService(new StorageProperties());
        service = new TicketImageService(
                ticketRepository,
                ticketImageRepository,
                currentUserService,
                managedImageIngestService,
                storedFileOwnershipService,
                fileUrlService,
                imageProperties,
                entityManager);
        author = User.builder()
                .email("ticket-images@example.com")
                .passwordHash("hash")
                .name("Ticket Image Author")
                .build();
        author.setId(10L);
    }

    @Test
    void firstDraftImageIsPrivateAndBecomesTheCachedCover() {
        Ticket ticket = ticket(42L, TicketStatus.DRAFT, 0);
        StoredFile thumb = file(101L, "thumb.jpg", FileVariant.THUMB, null, FileVisibility.PRIVATE);
        StoredFile large = file(102L, "large.jpg", FileVariant.LARGE, null, FileVisibility.PRIVATE);
        StagedImageFiles staged = new StagedImageFiles(101L, 102L);
        stubUploadAuthor(ticket);
        when(managedImageIngestService.stagePair(upload, FileOwnerType.TICKET_IMAGE, 10L))
                .thenReturn(staged);
        when(storedFileOwnershipService.attach(
                staged.ids(), FileOwnerType.TICKET_IMAGE, 42L, FileVisibility.PRIVATE))
                .thenAnswer(invocation -> {
                    thumb.setOwnerId(42L);
                    large.setOwnerId(42L);
                    return Map.of(101L, thumb, 102L, large);
                });
        when(ticketImageRepository.save(any(TicketImage.class))).thenAnswer(invocation -> {
            TicketImage image = invocation.getArgument(0);
            image.setId(91L);
            return image;
        });

        TicketImageResponse response = service.addImage(42L, upload, "  Leak under valve  ");

        assertThat(response.id()).isEqualTo(91L);
        assertThat(response.position()).isZero();
        assertThat(response.caption()).isEqualTo("Leak under valve");
        assertThat(response.image().thumb()).isEqualTo("/api/files/thumb.jpg");
        assertThat(ticket.getImageCount()).isEqualTo((short) 1);
        assertThat(ticket.getCoverImageUrl()).isEqualTo("/api/files/thumb.jpg");
        assertThat(ticket.getCoverImageLargeUrl()).isEqualTo("/api/files/large.jpg");
        verify(ticketRepository).save(ticket);
    }

    @Test
    void imageAddedToOpenTicketIsImmediatelyPublic() {
        Ticket ticket = ticket(42L, TicketStatus.OPEN, 1);
        StagedImageFiles staged = new StagedImageFiles(101L, 102L);
        StoredFile thumb = file(101L, "thumb.jpg", FileVariant.THUMB, null, FileVisibility.PRIVATE);
        StoredFile large = file(102L, "large.jpg", FileVariant.LARGE, null, FileVisibility.PRIVATE);
        stubUploadAuthor(ticket);
        when(managedImageIngestService.stagePair(upload, FileOwnerType.TICKET_IMAGE, 10L)).thenReturn(staged);
        when(storedFileOwnershipService.attach(
                staged.ids(), FileOwnerType.TICKET_IMAGE, 42L, FileVisibility.PUBLIC))
                .thenReturn(Map.of(101L, thumb, 102L, large));
        when(ticketImageRepository.save(any(TicketImage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketImageResponse response = service.addImage(42L, upload, null);

        assertThat(response.position()).isEqualTo(1);
        assertThat(ticket.getImageCount()).isEqualTo((short) 2);
        verify(storedFileOwnershipService).attach(
                staged.ids(), FileOwnerType.TICKET_IMAGE, 42L, FileVisibility.PUBLIC);
    }

    @Test
    void limitIsRejectedBeforeImageBytesAreIngested() {
        Ticket ticket = ticket(42L, TicketStatus.DRAFT, 8);
        when(ticketRepository.findDetailedById(42L)).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());

        assertThatThrownBy(() -> service.addImage(42L, upload, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("IMAGE_LIMIT_EXCEEDED");

        verify(managedImageIngestService, never()).stagePair(any(MultipartFile.class), any(), any());
    }

    @Test
    void nonAuthorAndMatchedTicketCannotCreateOrphanUploads() {
        Ticket draft = ticket(42L, TicketStatus.DRAFT, 0);
        when(ticketRepository.findDetailedById(42L)).thenReturn(Optional.of(draft));
        when(currentUserService.getCurrentUserId()).thenReturn(999L);

        assertThatThrownBy(() -> service.addImage(42L, upload, null))
                .isInstanceOf(ForbiddenException.class)
                .extracting("code")
                .isEqualTo("TICKET_AUTHOR_REQUIRED");

        Ticket matched = ticket(43L, TicketStatus.MATCHED, 0);
        when(ticketRepository.findDetailedById(43L)).thenReturn(Optional.of(matched));
        when(currentUserService.getCurrentUserId()).thenReturn(10L);
        assertThatThrownBy(() -> service.addImage(43L, upload, null))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("TICKET_IMAGES_IMMUTABLE");
        verify(managedImageIngestService, never()).stagePair(any(MultipartFile.class), any(), any());
    }

    @Test
    void deletingTheCoverCompactsPositionsAndRebuildsCaches() {
        Ticket ticket = ticket(42L, TicketStatus.OPEN, 2);
        TicketImage first = image(91L, ticket, 0, "first");
        TicketImage second = image(92L, ticket, 1, "second");
        stubLockedAuthor(ticket);
        when(ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(42L))
                .thenReturn(List.of(first, second));
        stubDeferredConstraint();

        service.deleteImage(42L, 91L);

        assertThat(second.getPosition()).isZero();
        assertThat(ticket.getImageCount()).isEqualTo((short) 1);
        assertThat(ticket.getCoverImageUrl()).isEqualTo("/api/files/second-thumb.jpg");
        assertThat(ticket.getCoverImageLargeUrl()).isEqualTo("/api/files/second-large.jpg");
        verify(ticketImageRepository).delete(first);
        verify(storedFileOwnershipService).detach(List.of(first.getThumb(), first.getLarge()));
        verify(nativeQuery).executeUpdate();
    }

    @Test
    void reorderRequiresExactPermutationAndWritesOnlyFinalNonNegativePositions() {
        Ticket ticket = ticket(42L, TicketStatus.DRAFT, 2);
        TicketImage first = image(91L, ticket, 0, "first");
        TicketImage second = image(92L, ticket, 1, "second");
        stubLockedAuthor(ticket);
        when(ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(42L))
                .thenReturn(List.of(first, second));

        assertThatThrownBy(() -> service.reorderImages(42L, List.of(91L, 91L)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_IMAGE_ORDER");
        verify(entityManager, never()).createNativeQuery(any(String.class));

        stubDeferredConstraint();
        List<TicketImageResponse> response = service.reorderImages(42L, List.of(92L, 91L));

        assertThat(response).extracting(TicketImageResponse::id).containsExactly(92L, 91L);
        assertThat(second.getPosition()).isZero();
        assertThat(first.getPosition()).isEqualTo((short) 1);
        assertThat(first.getPosition()).isNotNegative();
        assertThat(second.getPosition()).isNotNegative();
        assertThat(ticket.getCoverImageUrl()).isEqualTo("/api/files/second-thumb.jpg");
        verify(entityManager).createNativeQuery("SET CONSTRAINTS uk_ticket_images_position DEFERRED");
    }

    @Test
    void publishPromotesEveryAttachedVariantToPublic() {
        Ticket ticket = ticket(42L, TicketStatus.DRAFT, 1);
        TicketImage image = image(91L, ticket, 0, "first");
        when(ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(42L)).thenReturn(List.of(image));

        service.promoteForPublish(ticket);

        assertThat(image.getThumb().getVisibility()).isEqualTo(FileVisibility.PUBLIC);
        assertThat(image.getLarge().getVisibility()).isEqualTo(FileVisibility.PUBLIC);
    }

    private void stubLockedAuthor(Ticket ticket) {
        when(ticketRepository.findByIdForUpdate(ticket.getId())).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
    }

    private void stubUploadAuthor(Ticket ticket) {
        when(ticketRepository.findDetailedById(ticket.getId())).thenReturn(Optional.of(ticket));
        when(ticketRepository.findByIdForUpdate(ticket.getId())).thenReturn(Optional.of(ticket));
        when(currentUserService.getCurrentUserId()).thenReturn(author.getId());
        when(ticketImageRepository.countByTicket_Id(ticket.getId()))
                .thenReturn((long) ticket.getImageCount());
    }

    private void stubDeferredConstraint() {
        when(entityManager.createNativeQuery("SET CONSTRAINTS uk_ticket_images_position DEFERRED"))
                .thenReturn(nativeQuery);
        when(nativeQuery.executeUpdate()).thenReturn(0);
    }

    private Ticket ticket(Long id, TicketStatus status, int imageCount) {
        Ticket ticket = Ticket.builder()
                .kind(TicketKind.REQUEST)
                .author(author)
                .title("Move a sofa")
                .pricingMode(PricingMode.FIXED)
                .price(new BigDecimal("80.00"))
                .currency("USD")
                .locationMode(LocationMode.ON_SITE)
                .status(status)
                .imageCount((short) imageCount)
                .build();
        ticket.setId(id);
        return ticket;
    }

    private TicketImage image(Long id, Ticket ticket, int position, String keyPrefix) {
        StoredFile thumb = file(
                id * 10,
                keyPrefix + "-thumb.jpg",
                FileVariant.THUMB,
                ticket.getId(),
                FileVisibility.PRIVATE);
        StoredFile large = file(
                id * 10 + 1,
                keyPrefix + "-large.jpg",
                FileVariant.LARGE,
                ticket.getId(),
                FileVisibility.PRIVATE);
        TicketImage image = TicketImage.builder()
                .ticket(ticket)
                .thumb(thumb)
                .large(large)
                .position((short) position)
                .build();
        image.setId(id);
        return image;
    }

    private StoredFile file(
            Long id,
            String storageKey,
            FileVariant variant,
            Long ownerId,
            FileVisibility visibility) {
        StoredFile file = StoredFile.builder()
                .storageKey(storageKey)
                .variant(variant)
                .visibility(visibility)
                .ownerType(FileOwnerType.TICKET_IMAGE)
                .ownerId(ownerId)
                .uploader(author)
                .contentType("image/jpeg")
                .byteSize(100L)
                .width(100)
                .height(100)
                .build();
        file.setId(id);
        return file;
    }
}
