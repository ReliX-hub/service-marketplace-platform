package com.relix.marketplace.engagement.service;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.dto.DeliverableResponse;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementDeliverable;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementDeliverableRepository;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.storage.dto.ImageVariants;
import com.relix.marketplace.storage.entity.FileOwnerType;
import com.relix.marketplace.storage.entity.FileVisibility;
import com.relix.marketplace.storage.entity.StoredFile;
import com.relix.marketplace.storage.service.FileUrlService;
import com.relix.marketplace.storage.service.ManagedImageIngestService;
import com.relix.marketplace.storage.service.StagedImageFiles;
import com.relix.marketplace.storage.service.StoredFileOwnershipService;
import com.relix.marketplace.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeliverableService {

    private static final int NOTE_MAX_LENGTH = 300;

    private final EngagementRepository engagementRepository;
    private final EngagementDeliverableRepository deliverableRepository;
    private final CurrentUserService currentUserService;
    private final ManagedImageIngestService imageIngestService;
    private final StoredFileOwnershipService ownershipService;
    private final FileUrlService fileUrlService;
    private final ImageProperties imageProperties;
    private final EntityManager entityManager;

    @Transactional
    public DeliverableResponse upload(Long engagementId, MultipartFile file, String note) {
        User actor = currentUserService.getCurrentUser();

        // Reject unauthorized and obviously invalid requests before doing image work.
        Engagement readable = findDetailed(engagementId);
        assertWorker(readable, actor.getId());
        assertUploadable(readable);
        String normalizedNote = normalizeNote(note);

        StagedImageFiles staged = imageIngestService.stagePair(
                file,
                FileOwnerType.ENGAGEMENT_DELIVERABLE,
                actor.getId());

        // The row lock serializes concurrent uploads so the cached count and the
        // next position cannot both pass the eight-image ceiling.
        Engagement engagement = lock(engagementId);
        assertWorker(engagement, actor.getId());
        assertUploadable(engagement);

        int count = Math.toIntExact(deliverableRepository.countByEngagement_Id(engagementId));
        if (count != deliverableCount(engagement)) {
            engagement.setDeliverableCount((short) count);
        }
        if (count >= imageProperties.getMaxPerDeliverable()) {
            throw new ConflictException(
                    "An engagement cannot contain more delivery images",
                    "IMAGE_LIMIT_EXCEEDED",
                    Map.of("ownerType", FileOwnerType.ENGAGEMENT_DELIVERABLE.name(),
                            "max", imageProperties.getMaxPerDeliverable()));
        }

        Map<Long, StoredFile> attached = ownershipService.attach(
                staged.ids(),
                FileOwnerType.ENGAGEMENT_DELIVERABLE,
                engagement.getId(),
                FileVisibility.PRIVATE);
        EngagementDeliverable deliverable = EngagementDeliverable.builder()
                .engagement(engagement)
                .thumb(requireAttached(attached, staged.thumbId()))
                .large(requireAttached(attached, staged.largeId()))
                .note(normalizedNote)
                .position((short) count)
                .submittedBy(actor)
                .build();
        deliverable = deliverableRepository.save(deliverable);
        engagement.setDeliverableCount((short) (count + 1));
        return toResponse(deliverable);
    }

    public List<DeliverableResponse> getDeliverables(Long engagementId) {
        Engagement engagement = findDetailed(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertParticipantOrAdmin(engagement, actor.getId());
        return deliverableRepository.findByEngagement_IdOrderByPositionAscIdAsc(engagementId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void delete(Long engagementId, Long deliverableId) {
        Engagement engagement = lock(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertWorker(engagement, actor.getId());
        assertDeletable(engagement);

        EngagementDeliverable deliverable = deliverableRepository
                .findByIdAndEngagement_Id(deliverableId, engagementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Engagement deliverable", deliverableId));

        deliverableRepository.deferPositionConstraint();
        List<EngagementDeliverable> ordered = deliverableRepository
                .findByEngagement_IdOrderByPositionAscIdAsc(engagementId);
        deliverableRepository.delete(deliverable);

        int nextPosition = 0;
        for (EngagementDeliverable remaining : ordered) {
            if (!Objects.equals(remaining.getId(), deliverableId)) {
                remaining.setPosition((short) nextPosition++);
            }
        }
        engagement.setDeliverableCount((short) nextPosition);
        ownershipService.detach(List.of(deliverable.getThumb(), deliverable.getLarge()));
    }

    private Engagement findDetailed(Long engagementId) {
        return engagementRepository.findDetailedById(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement", engagementId));
    }

    private Engagement lock(Long engagementId) {
        Engagement engagement = engagementRepository.findByIdForUpdate(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement", engagementId));
        // upload() performs an authorization read before image normalization. If
        // this entity is already present in the persistence context, explicitly
        // refresh it after taking the row lock so a concurrent upload's committed
        // count and lifecycle state cannot be hidden by the first-level cache.
        entityManager.refresh(engagement);
        return engagement;
    }

    private void assertUploadable(Engagement engagement) {
        if (engagement.getStatus() != EngagementStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Delivery evidence can only be submitted while work is in progress",
                    "DELIVERABLE_STATE_INVALID",
                    Map.of("requiredStatus", EngagementStatus.IN_PROGRESS.name(),
                            "actualStatus", engagement.getStatus().name()));
        }
    }

    private void assertDeletable(Engagement engagement) {
        if (engagement.getStatus() == EngagementStatus.IN_PROGRESS) {
            return;
        }
        if (engagement.getStatus() == EngagementStatus.DELIVERED
                || engagement.getStatus() == EngagementStatus.COMPLETED
                || engagement.getStatus() == EngagementStatus.DISPUTED
                || engagement.getStatus() == EngagementStatus.REFUNDED) {
            throw new ConflictException(
                    "Delivery evidence is immutable after delivery",
                    "DELIVERABLE_IMMUTABLE");
        }
        throw new ConflictException(
                "Delivery evidence can only be deleted while work is in progress",
                "DELIVERABLE_STATE_INVALID",
                Map.of("requiredStatus", EngagementStatus.IN_PROGRESS.name(),
                        "actualStatus", engagement.getStatus().name()));
    }

    private void assertWorker(Engagement engagement, Long userId) {
        if (!Objects.equals(engagement.getWorker().getUser().getId(), userId)) {
            throw new ForbiddenException(
                    "Only the engagement worker can manage delivery evidence",
                    "ENGAGEMENT_WORKER_REQUIRED");
        }
    }

    private void assertParticipantOrAdmin(Engagement engagement, Long userId) {
        if (currentUserService.isAdmin()) {
            return;
        }
        boolean client = Objects.equals(engagement.getClient().getId(), userId);
        boolean worker = Objects.equals(engagement.getWorker().getUser().getId(), userId);
        if (!client && !worker) {
            throw new ForbiddenException(
                    "You are not a participant in this engagement",
                    "ENGAGEMENT_PARTICIPANT_REQUIRED");
        }
    }

    private int deliverableCount(Engagement engagement) {
        return engagement.getDeliverableCount() == null ? 0 : engagement.getDeliverableCount();
    }

    private StoredFile requireAttached(Map<Long, StoredFile> attached, Long fileId) {
        StoredFile file = attached.get(fileId);
        if (file == null) {
            throw new IllegalStateException("Attached file is missing from the ownership result");
        }
        return file;
    }

    private String normalizeNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String normalized = note.trim();
        if (normalized.length() > NOTE_MAX_LENGTH) {
            throw new BusinessException(
                    "Delivery note cannot exceed 300 characters",
                    "DELIVERABLE_NOTE_TOO_LONG",
                    "note",
                    Map.of("maxLength", NOTE_MAX_LENGTH));
        }
        return normalized;
    }

    private DeliverableResponse toResponse(EngagementDeliverable deliverable) {
        return new DeliverableResponse(
                deliverable.getId(),
                new ImageVariants(
                        fileUrlService.toExternalUrl(deliverable.getThumb()),
                        fileUrlService.toExternalUrl(deliverable.getLarge())),
                deliverable.getPosition(),
                deliverable.getNote(),
                deliverable.getSubmittedBy().getId(),
                deliverable.getCreatedAt());
    }
}
