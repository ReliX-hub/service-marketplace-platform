package com.relix.marketplace.engagement.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.dto.DisputeResolutionRequest;
import com.relix.marketplace.engagement.dto.EngagementResponse;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.engagement.validator.EngagementStateValidator;
import com.relix.marketplace.payment.dto.PaymentRequest;
import com.relix.marketplace.payment.dto.PaymentResponse;
import com.relix.marketplace.payment.service.PaymentService;
import com.relix.marketplace.refund.dto.RefundSummaryResponse;
import com.relix.marketplace.refund.service.RefundService;
import com.relix.marketplace.settlement.service.SettlementService;
import com.relix.marketplace.storage.config.ImageProperties;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EngagementService {

    private final EngagementRepository engagementRepository;
    private final CurrentUserService currentUserService;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final SettlementService settlementService;
    private final WorkerProfileRepository workerProfileRepository;
    private final AuditService auditService;
    private final ImageProperties imageProperties;

    public PageResponse<EngagementResponse> getMyEngagements(
            String role,
            EngagementStatus status,
            Pageable pageable) {
        Long userId = currentUserService.getCurrentUser().getId();
        String normalizedRole = role == null ? "client" : role.trim().toLowerCase(Locale.ROOT);
        Page<Engagement> engagements;
        if ("client".equals(normalizedRole)) {
            engagements = status == null
                    ? engagementRepository.findByClient_Id(userId, pageable)
                    : engagementRepository.findByClient_IdAndStatus(userId, status, pageable);
        } else if ("worker".equals(normalizedRole)) {
            engagements = status == null
                    ? engagementRepository.findByWorker_User_Id(userId, pageable)
                    : engagementRepository.findByWorker_User_IdAndStatus(userId, status, pageable);
        } else {
            throw new BusinessException(
                    "Role must be either client or worker",
                    "INVALID_ENGAGEMENT_ROLE",
                    "role",
                    Map.of("allowed", new String[]{"client", "worker"}));
        }
        Set<Long> engagementIds = engagements.getContent().stream()
                .map(Engagement::getId)
                .collect(java.util.stream.Collectors.toSet());
        Map<Long, RefundSummaryResponse> refundSummaries =
                refundService.getSummariesByEngagementIds(engagementIds);
        return PageResponse.of(engagements,
                engagement -> toResponse(engagement, refundSummaries.get(engagement.getId())));
    }

    public EngagementResponse getEngagement(Long engagementId) {
        Engagement engagement = engagementRepository.findDetailedById(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement", engagementId));
        User currentUser = currentUserService.getCurrentUser();
        assertParticipantOrAdmin(engagement, currentUser.getId());
        return toResponseWithRefundSummary(engagement);
    }

    @Transactional
    public PaymentResponse pay(Long engagementId, PaymentRequest request) {
        Engagement engagement = lock(engagementId);
        assertClient(engagement, currentUserService.getCurrentUser().getId());
        return paymentService.pay(engagement, request);
    }

    @Transactional
    public EngagementResponse start(Long engagementId) {
        Engagement engagement = lock(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertWorker(engagement, actor.getId());
        transition(engagement, EngagementStatus.IN_PROGRESS, "start");
        engagement.setStartedAt(Instant.now());
        audit(engagement, "ENGAGEMENT_STARTED", "WORKER", actor.getId());
        return toResponseWithRefundSummary(engagement);
    }

    @Transactional
    public EngagementResponse deliver(Long engagementId) {
        Engagement engagement = lock(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertWorker(engagement, actor.getId());
        if (engagement.getStatus() == EngagementStatus.IN_PROGRESS
                && imageProperties.isRequireDeliverableOnDeliver()
                && (engagement.getDeliverableCount() == null
                || engagement.getDeliverableCount() < 1)) {
            throw new ConflictException(
                    "At least one delivery-evidence image is required before delivery",
                    "DELIVERABLE_REQUIRED",
                    Map.of("minimum", 1));
        }
        transition(engagement, EngagementStatus.DELIVERED, "deliver");
        engagement.setDeliveredAt(Instant.now());
        audit(engagement, "ENGAGEMENT_DELIVERED", "WORKER", actor.getId());
        return toResponseWithRefundSummary(engagement);
    }

    @Transactional
    public EngagementResponse approve(Long engagementId) {
        Engagement engagement = lock(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertClient(engagement, actor.getId());
        complete(engagement, "approve");
        audit(engagement, "ENGAGEMENT_APPROVED", "CLIENT", actor.getId());
        return toResponseWithRefundSummary(engagement);
    }

    @Transactional
    public EngagementResponse dispute(Long engagementId, String reason) {
        Engagement engagement = lock(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertClient(engagement, actor.getId());
        String normalizedReason = requireReason(reason, "DISPUTE_REASON_REQUIRED");
        transition(engagement, EngagementStatus.DISPUTED, "dispute");
        engagement.setDisputedAt(Instant.now());
        engagement.setDisputeReason(normalizedReason);
        audit(engagement, "ENGAGEMENT_DISPUTED", "CLIENT", actor.getId());
        return toResponseWithRefundSummary(engagement);
    }

    @Transactional
    public EngagementResponse cancel(Long engagementId, String reason) {
        Engagement engagement = lock(engagementId);
        User actor = currentUserService.getCurrentUser();
        assertParticipantOrAdmin(engagement, actor.getId());

        if (engagement.getStatus() == EngagementStatus.CANCELLED
                || engagement.getStatus() == EngagementStatus.REFUNDED) {
            return toResponseWithRefundSummary(engagement);
        }
        if (paymentService.hasPendingPayment(engagementId)
                && !paymentService.hasSucceededPayment(engagementId)) {
            throw new ConflictException(
                    "A pending payment must finish before this engagement can be cancelled",
                    "PAYMENT_IN_PROGRESS");
        }

        boolean funded = paymentService.hasSucceededPayment(engagementId);
        transition(engagement, EngagementStatus.CANCELLED, "cancel");
        engagement.setCancelledAt(Instant.now());
        engagement.setCancellationReason(normalizeOptionalReason(reason, "Engagement cancelled"));
        if (funded) {
            refundService.createRefund(engagement, engagement.getCancellationReason());
        }
        audit(
                engagement,
                funded ? "ENGAGEMENT_CANCELLED_REFUND_INITIATED" : "ENGAGEMENT_CANCELLED",
                actorType(engagement, actor.getId()),
                actor.getId());
        return toResponseWithRefundSummary(engagement);
    }

    @Transactional
    public EngagementResponse resolveDispute(
            Long engagementId,
            DisputeResolutionRequest request) {
        if (!currentUserService.isAdmin()) {
            throw new ForbiddenException("Admin access is required", "ADMIN_REQUIRED");
        }
        Engagement engagement = lock(engagementId);
        if (engagement.getStatus() != EngagementStatus.DISPUTED) {
            throw new ConflictException(
                    "Only a disputed engagement can be resolved",
                    "ENGAGEMENT_NOT_DISPUTED");
        }

        User admin = currentUserService.getCurrentUser();
        if (request.getResolution() == DisputeResolutionRequest.Resolution.COMPLETED) {
            complete(engagement, "resolve dispute as completed");
            audit(engagement, "DISPUTE_RESOLVED_COMPLETED", "ADMIN", admin.getId());
        } else {
            refundService.createRefund(
                    engagement,
                    normalizeOptionalReason(request.getReason(), "Dispute resolved in client's favor"));
            audit(engagement, "DISPUTE_RESOLVED_REFUND_INITIATED", "ADMIN", admin.getId());
        }
        return toResponseWithRefundSummary(engagement);
    }

    private void complete(Engagement engagement, String operation) {
        transition(engagement, EngagementStatus.COMPLETED, operation);
        Instant now = Instant.now();
        engagement.setApprovedAt(now);
        engagement.setCompletedAt(now);
        settlementService.createSettlement(engagement);

        WorkerProfile worker = engagement.getWorker();
        worker.setCompletedJobs((worker.getCompletedJobs() == null ? 0 : worker.getCompletedJobs()) + 1);
        workerProfileRepository.save(worker);
    }

    private Engagement lock(Long engagementId) {
        return engagementRepository.findByIdForUpdate(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException("Engagement", engagementId));
    }

    private void transition(Engagement engagement, EngagementStatus target, String operation) {
        EngagementStateValidator.validateForOperation(engagement.getStatus(), target, operation);
        engagement.setStatus(target);
    }

    private void assertClient(Engagement engagement, Long userId) {
        if (!engagement.getClient().getId().equals(userId)) {
            throw new ForbiddenException(
                    "Only the engagement client can perform this action",
                    "ENGAGEMENT_CLIENT_REQUIRED");
        }
    }

    private void assertWorker(Engagement engagement, Long userId) {
        if (!engagement.getWorker().getUser().getId().equals(userId)) {
            throw new ForbiddenException(
                    "Only the engagement worker can perform this action",
                    "ENGAGEMENT_WORKER_REQUIRED");
        }
    }

    private void assertParticipantOrAdmin(Engagement engagement, Long userId) {
        if (currentUserService.isAdmin()) {
            return;
        }
        boolean client = engagement.getClient().getId().equals(userId);
        boolean worker = engagement.getWorker().getUser().getId().equals(userId);
        if (!client && !worker) {
            throw new ForbiddenException(
                    "You are not a participant in this engagement",
                    "ENGAGEMENT_PARTICIPANT_REQUIRED");
        }
    }

    private String actorType(Engagement engagement, Long userId) {
        return engagement.getClient().getId().equals(userId) ? "CLIENT" : "WORKER";
    }

    private String requireReason(String reason, String code) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("A reason is required", code);
        }
        return normalizeOptionalReason(reason, "Reason required");
    }

    private String normalizeOptionalReason(String reason, String fallback) {
        String normalized = reason == null || reason.isBlank() ? fallback : reason.trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private void audit(Engagement engagement, String action, String actorType, Long actorId) {
        auditService.log(
                "ENGAGEMENT",
                engagement.getId(),
                action,
                actorType,
                actorId,
                Map.of("status", engagement.getStatus().name()));
    }

    public EngagementResponse toResponse(Engagement engagement) {
        return toResponseWithRefundSummary(engagement);
    }

    private EngagementResponse toResponseWithRefundSummary(Engagement engagement) {
        RefundSummaryResponse refundSummary = refundService.getSummaryByEngagementId(engagement.getId())
                .orElse(null);
        return toResponse(engagement, refundSummary);
    }

    private EngagementResponse toResponse(
            Engagement engagement,
            RefundSummaryResponse refundSummary) {
        return EngagementResponse.builder()
                .id(engagement.getId())
                .ticketId(engagement.getTicket().getId())
                .applicationId(engagement.getApplication() == null ? null : engagement.getApplication().getId())
                .clientId(engagement.getClient().getId())
                .clientName(engagement.getClient().getName())
                .workerId(engagement.getWorker().getId())
                .workerUserId(engagement.getWorker().getUser().getId())
                .workerDisplayName(engagement.getWorker().getDisplayName())
                .status(engagement.getStatus())
                .amount(engagement.getAmount())
                .notes(engagement.getNotes())
                .scheduledStart(engagement.getScheduledStart())
                .scheduledEnd(engagement.getScheduledEnd())
                .acceptedAt(engagement.getAcceptedAt())
                .fundedAt(engagement.getFundedAt())
                .startedAt(engagement.getStartedAt())
                .deliveredAt(engagement.getDeliveredAt())
                .approvedAt(engagement.getApprovedAt())
                .completedAt(engagement.getCompletedAt())
                .disputedAt(engagement.getDisputedAt())
                .disputeReason(engagement.getDisputeReason())
                .cancelledAt(engagement.getCancelledAt())
                .cancellationReason(engagement.getCancellationReason())
                .deliverableCount(engagement.getDeliverableCount() == null
                        ? 0
                        : engagement.getDeliverableCount())
                .refundSummary(refundSummary)
                .createdAt(engagement.getCreatedAt())
                .updatedAt(engagement.getUpdatedAt())
                .build();
    }
}
