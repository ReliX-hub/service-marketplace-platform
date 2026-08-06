package com.relix.marketplace.ticket.service;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.storage.service.ManagedImageIngestService;
import com.relix.marketplace.storage.service.StagedImageFiles;
import com.relix.marketplace.storage.service.StoredFileOwnershipService;
import com.relix.marketplace.ticket.dto.TicketImageResponse;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.ticket.entity.TicketImage;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketImageRepository;
import com.relix.marketplace.ticket.repository.TicketRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TicketImageService {

    private static final String POSITION_CONSTRAINT = "uk_ticket_images_position";

    private final TicketRepository ticketRepository;
    private final TicketImageRepository ticketImageRepository;
    private final CurrentUserService currentUserService;
    private final ManagedImageIngestService managedImageIngestService;
    private final StoredFileOwnershipService storedFileOwnershipService;
    private final FileUrlService fileUrlService;
    private final ImageProperties imageProperties;
    private final EntityManager entityManager;

    public List<TicketImageResponse> listResponses(Long ticketId) {
        return ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(ticketId).stream()
                .map(image -> TicketImageResponse.from(image, fileUrlService))
                .toList();
    }

    @Transactional
    public TicketImageResponse addImage(Long ticketId, MultipartFile upload, String caption) {
        Long uploaderId = currentUserService.getCurrentUserId();
        Ticket readable = findTicket(ticketId);
        assertAuthor(readable, uploaderId);
        assertImagesMutable(readable);
        assertCachedCapacity(readable);
        String normalizedCaption = normalizeCaption(caption);

        StagedImageFiles staged = managedImageIngestService.stagePair(
                upload,
                FileOwnerType.TICKET_IMAGE,
                uploaderId);
        Ticket ticket = findLockedTicket(ticketId);
        entityManager.refresh(ticket);
        assertAuthor(ticket, uploaderId);
        assertImagesMutable(ticket);

        int currentCount = Math.toIntExact(ticketImageRepository.countByTicket_Id(ticketId));
        if (ticket.getImageCount() == null || ticket.getImageCount().intValue() != currentCount) {
            ticket.setImageCount((short) currentCount);
        }
        assertCapacity(currentCount);
        FileVisibility visibility = ticket.getStatus() == TicketStatus.OPEN
                ? FileVisibility.PUBLIC
                : FileVisibility.PRIVATE;
        Map<Long, StoredFile> attached = storedFileOwnershipService.attach(
                staged.ids(),
                FileOwnerType.TICKET_IMAGE,
                ticket.getId(),
                visibility);

        TicketImage image = TicketImage.builder()
                .ticket(ticket)
                .thumb(requireAttached(attached, staged.thumbId()))
                .large(requireAttached(attached, staged.largeId()))
                .caption(normalizedCaption)
                .position((short) currentCount)
                .build();
        image = ticketImageRepository.save(image);

        ticket.setImageCount((short) (currentCount + 1));
        if (currentCount == 0) {
            setCover(ticket, image);
        }
        ticketRepository.save(ticket);
        return TicketImageResponse.from(image, fileUrlService);
    }

    @Transactional
    public void deleteImage(Long ticketId, Long imageId) {
        Ticket ticket = findLockedTicket(ticketId);
        assertAuthor(ticket);
        assertImagesMutable(ticket);

        List<TicketImage> images = ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(ticketId);
        TicketImage target = images.stream()
                .filter(image -> Objects.equals(image.getId(), imageId))
                .findFirst()
                .orElseThrow(() -> imageNotFound(ticketId, imageId));

        deferPositionConstraint();
        ticketImageRepository.delete(target);
        List<TicketImage> remaining = images.stream()
                .filter(image -> !Objects.equals(image.getId(), imageId))
                .toList();
        applyPositions(remaining);
        storedFileOwnershipService.detach(List.of(target.getThumb(), target.getLarge()));
        syncTicketCaches(ticket, remaining);
        ticketImageRepository.flush();
        ticketRepository.save(ticket);
    }

    @Transactional
    public List<TicketImageResponse> reorderImages(Long ticketId, List<Long> requestedOrder) {
        Ticket ticket = findLockedTicket(ticketId);
        assertAuthor(ticket);
        assertImagesMutable(ticket);

        List<TicketImage> images = ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(ticketId);
        validateExactPermutation(images, requestedOrder);
        Map<Long, TicketImage> byId = images.stream()
                .collect(java.util.stream.Collectors.toMap(TicketImage::getId, image -> image));
        List<TicketImage> reordered = requestedOrder.stream().map(byId::get).toList();

        deferPositionConstraint();
        applyPositions(reordered);
        syncTicketCaches(ticket, reordered);
        ticketImageRepository.flush();
        ticketRepository.save(ticket);
        return reordered.stream()
                .map(image -> TicketImageResponse.from(image, fileUrlService))
                .toList();
    }

    /**
     * Participates in TicketService.publishTicket so publication and file visibility
     * change commit or roll back as one unit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void promoteForPublish(Ticket ticket) {
        List<TicketImage> images = ticketImageRepository.findByTicket_IdOrderByPositionAscIdAsc(ticket.getId());
        for (TicketImage image : images) {
            assertAttachedToTicket(image.getThumb(), ticket.getId());
            assertAttachedToTicket(image.getLarge(), ticket.getId());
            image.getThumb().setVisibility(FileVisibility.PUBLIC);
            image.getLarge().setVisibility(FileVisibility.PUBLIC);
        }
    }

    private Ticket findLockedTicket(Long ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private Ticket findTicket(Long ticketId) {
        return ticketRepository.findDetailedById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket", ticketId));
    }

    private void assertAuthor(Ticket ticket) {
        assertAuthor(ticket, currentUserService.getCurrentUserId());
    }

    private void assertAuthor(Ticket ticket, Long currentUserId) {
        if (!Objects.equals(ticket.getAuthor().getId(), currentUserId)) {
            throw new ForbiddenException(
                    "Only the ticket author can manage its images",
                    "TICKET_AUTHOR_REQUIRED");
        }
    }

    private void assertImagesMutable(Ticket ticket) {
        if (ticket.getStatus() != TicketStatus.DRAFT && ticket.getStatus() != TicketStatus.OPEN) {
            throw new ConflictException(
                    "Ticket images are frozen after the listing leaves draft or open status",
                    "TICKET_IMAGES_IMMUTABLE");
        }
        if (ticket.getStatus() == TicketStatus.OPEN && ticket.isEffectivelyExpiredAt(Instant.now())) {
            throw new ConflictException(
                    "This ticket is no longer accepting changes",
                    "TICKET_EXPIRED");
        }
    }

    private void assertCachedCapacity(Ticket ticket) {
        if (ticket.getImageCount() != null) {
            assertCapacity(ticket.getImageCount());
        }
    }

    private void assertCapacity(int currentCount) {
        if (currentCount >= imageProperties.getMaxPerTicket()) {
            throw new ConflictException(
                    "A ticket cannot contain more images",
                    "IMAGE_LIMIT_EXCEEDED",
                    Map.of("ownerType", FileOwnerType.TICKET_IMAGE.name(),
                            "max", imageProperties.getMaxPerTicket()));
        }
    }

    private StoredFile requireAttached(Map<Long, StoredFile> attached, Long fileId) {
        StoredFile file = attached.get(fileId);
        if (file == null) {
            throw new IllegalStateException("Attached file is missing from the ownership result");
        }
        return file;
    }

    private String normalizeCaption(String caption) {
        if (caption == null) {
            return null;
        }
        String normalized = caption.trim();
        if (normalized.length() > 160) {
            throw new BusinessException(
                    "Image caption cannot exceed 160 characters",
                    "IMAGE_CAPTION_TOO_LONG",
                    "caption",
                    Map.of("maxLength", 160));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private void validateExactPermutation(List<TicketImage> images, List<Long> requestedOrder) {
        if (requestedOrder == null || requestedOrder.size() != images.size()
                || requestedOrder.stream().anyMatch(Objects::isNull)) {
            throw invalidOrder(images);
        }
        Set<Long> requestedIds = new HashSet<>(requestedOrder);
        Set<Long> currentIds = images.stream().map(TicketImage::getId).collect(java.util.stream.Collectors.toSet());
        if (requestedIds.size() != requestedOrder.size() || !requestedIds.equals(currentIds)) {
            throw invalidOrder(images);
        }
    }

    private BusinessException invalidOrder(List<TicketImage> images) {
        return new BusinessException(
                "Image order must contain every ticket image exactly once",
                "INVALID_IMAGE_ORDER",
                Map.of("expectedImageIds", images.stream().map(TicketImage::getId).toList()));
    }

    private void deferPositionConstraint() {
        entityManager.createNativeQuery("SET CONSTRAINTS " + POSITION_CONSTRAINT + " DEFERRED")
                .executeUpdate();
    }

    private void applyPositions(List<TicketImage> images) {
        for (int index = 0; index < images.size(); index++) {
            images.get(index).setPosition((short) index);
        }
    }

    private void syncTicketCaches(Ticket ticket, List<TicketImage> orderedImages) {
        ticket.setImageCount((short) orderedImages.size());
        if (orderedImages.isEmpty()) {
            ticket.setCoverImageUrl(null);
            ticket.setCoverImageLargeUrl(null);
        } else {
            setCover(ticket, orderedImages.get(0));
        }
    }

    private void setCover(Ticket ticket, TicketImage image) {
        ticket.setCoverImageUrl(fileUrlService.pathForKey(image.getThumb().getStorageKey()));
        ticket.setCoverImageLargeUrl(fileUrlService.pathForKey(image.getLarge().getStorageKey()));
    }

    private void assertAttachedToTicket(StoredFile file, Long ticketId) {
        if (file.getOwnerType() != FileOwnerType.TICKET_IMAGE
                || !Objects.equals(file.getOwnerId(), ticketId)) {
            throw new ConflictException(
                    "Ticket image file ownership is inconsistent",
                    "FILE_OWNERSHIP_MISMATCH");
        }
    }

    private ResourceNotFoundException imageNotFound(Long ticketId, Long imageId) {
        return new ResourceNotFoundException(
                "Ticket image not found",
                "TICKET_IMAGE_NOT_FOUND",
                Map.of("ticketId", ticketId, "imageId", imageId));
    }
}
