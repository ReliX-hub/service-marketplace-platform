package com.relix.marketplace.engagement.service;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.engagement.dto.DeliverableResponse;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementDeliverable;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementDeliverableRepository;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVariant;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.storage.service.ManagedImageIngestService;
import com.relix.marketplace.storage.service.StagedImageFiles;
import com.relix.marketplace.storage.service.StoredFileOwnershipService;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliverableServiceTest {

    @Mock private EngagementRepository engagementRepository;
    @Mock private EngagementDeliverableRepository deliverableRepository;
    @Mock private CurrentUserService currentUserService;
    @Mock private ManagedImageIngestService imageIngestService;
    @Mock private StoredFileOwnershipService ownershipService;
    @Mock private FileUrlService fileUrlService;
    @Mock private ImageProperties imageProperties;
    @Mock private EntityManager entityManager;

    private DeliverableService service;
    private User client;
    private User workerUser;
    private User stranger;
    private Engagement engagement;
    private StoredFile thumb;
    private StoredFile large;

    @BeforeEach
    void setUp() {
        service = new DeliverableService(
                engagementRepository,
                deliverableRepository,
                currentUserService,
                imageIngestService,
                ownershipService,
                fileUrlService,
                imageProperties,
                entityManager);
        client = user(1L, "Client");
        workerUser = user(2L, "Worker");
        stranger = user(3L, "Stranger");
        WorkerProfile worker = WorkerProfile.builder()
                .user(workerUser)
                .displayName("Worker")
                .build();
        worker.setId(20L);
        Ticket ticket = Ticket.builder().build();
        ticket.setId(100L);
        engagement = Engagement.builder()
                .client(client)
                .worker(worker)
                .ticket(ticket)
                .status(EngagementStatus.IN_PROGRESS)
                .deliverableCount((short) 0)
                .build();
        engagement.setId(500L);
        thumb = storedFile(41L, FileVariant.THUMB, "thumb.jpg");
        large = storedFile(42L, FileVariant.LARGE, "large.jpg");
    }

    @Test
    void assignedWorkerUploadsPrivateEvidenceAndAdvancesCachedCount() {
        MockMultipartFile upload = upload();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(engagement));
        when(imageIngestService.stagePair(
                upload, FileOwnerType.ENGAGEMENT_DELIVERABLE, 2L))
                .thenReturn(new StagedImageFiles(41L, 42L));
        when(engagementRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(engagement));
        when(deliverableRepository.countByEngagement_Id(500L)).thenReturn(0L);
        when(imageProperties.getMaxPerDeliverable()).thenReturn(8);
        when(ownershipService.attach(
                List.of(41L, 42L),
                FileOwnerType.ENGAGEMENT_DELIVERABLE,
                500L,
                FileVisibility.PRIVATE)).thenReturn(Map.of(41L, thumb, 42L, large));
        when(deliverableRepository.save(any())).thenAnswer(invocation -> {
            EngagementDeliverable saved = invocation.getArgument(0);
            saved.setId(73L);
            saved.setCreatedAt(Instant.parse("2026-08-08T16:45:00Z"));
            return saved;
        });
        when(fileUrlService.toExternalUrl(thumb)).thenReturn("/api/files/thumb.jpg");
        when(fileUrlService.toExternalUrl(large)).thenReturn("/api/files/large.jpg");

        DeliverableResponse response = service.upload(500L, upload, "  Leak tested.  ");

        assertEquals(73L, response.id());
        assertEquals("/api/files/thumb.jpg", response.image().thumb());
        assertEquals("/api/files/large.jpg", response.image().large());
        assertEquals(0, response.position());
        assertEquals("Leak tested.", response.note());
        assertEquals(2L, response.submittedBy());
        assertEquals((short) 1, engagement.getDeliverableCount());
        verify(engagementRepository).findByIdForUpdate(500L);
        verify(entityManager).refresh(engagement);
    }

    @Test
    void nonWorkerIsRejectedBeforeImageBytesAreStored() {
        when(currentUserService.getCurrentUser()).thenReturn(stranger);
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(engagement));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> service.upload(500L, upload(), null));

        assertEquals("ENGAGEMENT_WORKER_REQUIRED", exception.getCode());
        verify(imageIngestService, never()).stagePair(
                any(MultipartFile.class), any(), any());
    }

    @Test
    void postLockCountCheckPreventsConcurrentNinthImage() {
        Engagement readableSnapshot = copyEngagement((short) 7);
        engagement.setDeliverableCount((short) 8);
        MockMultipartFile upload = upload();
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(readableSnapshot));
        when(imageIngestService.stagePair(
                upload, FileOwnerType.ENGAGEMENT_DELIVERABLE, 2L))
                .thenReturn(new StagedImageFiles(41L, 42L));
        when(engagementRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(engagement));
        when(deliverableRepository.countByEngagement_Id(500L)).thenReturn(8L);
        when(imageProperties.getMaxPerDeliverable()).thenReturn(8);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.upload(500L, upload, null));

        assertEquals("IMAGE_LIMIT_EXCEEDED", exception.getCode());
        verify(ownershipService, never()).attach(any(), any(), any(), any());
        verify(deliverableRepository, never()).save(any());
    }

    @Test
    void deliveredEvidenceCannotBeDeleted() {
        engagement.setStatus(EngagementStatus.DELIVERED);
        when(engagementRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(engagement));
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> service.delete(500L, 73L));

        assertEquals("DELIVERABLE_IMMUTABLE", exception.getCode());
        verify(deliverableRepository, never()).delete(any());
        verify(ownershipService, never()).detach(any());
    }

    @Test
    void deleteDetachesFilesAndCompactsPositions() {
        engagement.setDeliverableCount((short) 3);
        EngagementDeliverable first = deliverable(71L, (short) 0, thumb, large);
        StoredFile middleThumb = storedFile(51L, FileVariant.THUMB, "middle-thumb.jpg");
        StoredFile middleLarge = storedFile(52L, FileVariant.LARGE, "middle-large.jpg");
        EngagementDeliverable middle = deliverable(72L, (short) 1, middleThumb, middleLarge);
        StoredFile lastThumb = storedFile(61L, FileVariant.THUMB, "last-thumb.jpg");
        StoredFile lastLarge = storedFile(62L, FileVariant.LARGE, "last-large.jpg");
        EngagementDeliverable last = deliverable(73L, (short) 2, lastThumb, lastLarge);
        when(engagementRepository.findByIdForUpdate(500L)).thenReturn(Optional.of(engagement));
        when(currentUserService.getCurrentUser()).thenReturn(workerUser);
        when(deliverableRepository.findByIdAndEngagement_Id(72L, 500L))
                .thenReturn(Optional.of(middle));
        when(deliverableRepository.findByEngagement_IdOrderByPositionAscIdAsc(500L))
                .thenReturn(List.of(first, middle, last));

        service.delete(500L, 72L);

        verify(deliverableRepository).deferPositionConstraint();
        verify(deliverableRepository).delete(middle);
        verify(ownershipService).detach(List.of(middleThumb, middleLarge));
        assertEquals((short) 0, first.getPosition());
        assertEquals((short) 1, last.getPosition());
        assertEquals((short) 2, engagement.getDeliverableCount());
    }

    @Test
    void participantsCanListEvidenceButStrangersCannot() {
        EngagementDeliverable deliverable = deliverable(73L, (short) 0, thumb, large);
        when(engagementRepository.findDetailedById(500L)).thenReturn(Optional.of(engagement));
        when(currentUserService.getCurrentUser()).thenReturn(client, stranger);
        when(currentUserService.isAdmin()).thenReturn(false);
        when(deliverableRepository.findByEngagement_IdOrderByPositionAscIdAsc(500L))
                .thenReturn(List.of(deliverable));
        when(fileUrlService.toExternalUrl(thumb)).thenReturn("/api/files/thumb.jpg");
        when(fileUrlService.toExternalUrl(large)).thenReturn("/api/files/large.jpg");

        List<DeliverableResponse> clientView = service.getDeliverables(500L);
        assertEquals(1, clientView.size());

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> service.getDeliverables(500L));
        assertEquals("ENGAGEMENT_PARTICIPANT_REQUIRED", exception.getCode());
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "evidence.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private User user(Long id, String name) {
        User user = User.builder()
                .email(name.toLowerCase() + "@test.com")
                .passwordHash("hash")
                .name(name)
                .status(User.UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return user;
    }

    private StoredFile storedFile(Long id, FileVariant variant, String key) {
        StoredFile file = StoredFile.builder()
                .storageKey(key)
                .variant(variant)
                .visibility(FileVisibility.PRIVATE)
                .ownerType(FileOwnerType.ENGAGEMENT_DELIVERABLE)
                .ownerId(500L)
                .uploader(workerUser)
                .contentType("image/jpeg")
                .byteSize(100L)
                .width(100)
                .height(100)
                .build();
        file.setId(id);
        return file;
    }

    private EngagementDeliverable deliverable(
            Long id,
            short position,
            StoredFile deliverableThumb,
            StoredFile deliverableLarge) {
        EngagementDeliverable deliverable = EngagementDeliverable.builder()
                .engagement(engagement)
                .thumb(deliverableThumb)
                .large(deliverableLarge)
                .position(position)
                .submittedBy(workerUser)
                .createdAt(Instant.parse("2026-08-08T16:45:00Z"))
                .build();
        deliverable.setId(id);
        return deliverable;
    }

    private Engagement copyEngagement(short deliverableCount) {
        Engagement copy = Engagement.builder()
                .client(engagement.getClient())
                .worker(engagement.getWorker())
                .ticket(engagement.getTicket())
                .status(EngagementStatus.IN_PROGRESS)
                .deliverableCount(deliverableCount)
                .build();
        copy.setId(500L);
        return copy;
    }
}
