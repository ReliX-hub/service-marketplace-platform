package com.relix.marketplace.refund.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ForbiddenException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.engagement.validator.EngagementStateValidator;
import com.relix.marketplace.payment.entity.Payment;
import com.relix.marketplace.payment.gateway.MinorUnitConverter;
import com.relix.marketplace.payment.gateway.PaymentGateway;
import com.relix.marketplace.payment.gateway.PaymentGatewayException;
import com.relix.marketplace.payment.gateway.RefundRequest;
import com.relix.marketplace.payment.gateway.RefundResult;
import com.relix.marketplace.payment.repository.PaymentRepository;
import com.relix.marketplace.payment.webhook.PaymentEventHandler;
import com.relix.marketplace.payment.webhook.RetryableWebhookException;
import com.relix.marketplace.refund.dto.RefundResponse;
import com.relix.marketplace.refund.dto.RefundSummaryResponse;
import com.relix.marketplace.refund.entity.Refund;
import com.relix.marketplace.refund.repository.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RefundService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final Set<Refund.RefundStatus> UNBOUND_WEBHOOK_STATES = Set.of(
            Refund.RefundStatus.PENDING,
            Refund.RefundStatus.PROCESSING);
    private static final String REFUND_ID_METADATA = "marketplaceRefundId";
    private static final String ENGAGEMENT_ID_METADATA = "marketplaceEngagementId";
    private static final String REPLACES_REFUND_ID_METADATA = "marketplaceReplacesRefundId";

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final EngagementRepository engagementRepository;
    private final PaymentGateway paymentGateway;
    private final AuditService auditService;

    @Transactional
    public Refund createRefund(Engagement engagement, String reason) {
        Refund refund = refundRepository.findByEngagementIdForUpdate(engagement.getId())
                .orElse(null);
        if (refund != null && refund.getStatus() != Refund.RefundStatus.FAILED) {
            return refund;
        }

        Payment payment = refund == null
                ? paymentRepository.findByEngagement_Id(engagement.getId())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Payment for engagement",
                                engagement.getId()))
                : refund.getPayment();
        assertRefundable(payment);

        if (refund == null) {
            refund = Refund.builder()
                    .engagement(engagement)
                    .payment(payment)
                    .amount(payment.getAmount())
                    .reason(truncateReason(reason))
                    .status(Refund.RefundStatus.PENDING)
                    .build();
            refund = refundRepository.saveAndFlush(refund);
        }

        String baseIdempotencyKey = "engagement-refund:" + engagement.getId();
        String replacedProviderRefundId = null;
        if (hasText(refund.getProviderRefundId())) {
            RefundResult providerState;
            try {
                providerState = paymentGateway.retrieveRefund(refund.getProviderRefundId());
            } catch (PaymentGatewayException exception) {
                return persistGatewayFailure(refund, engagement, exception.getMessage());
            }

            String validationFailure = validateProviderSnapshot(
                    refund,
                    providerState,
                    refund.getProviderRefundId());
            if (validationFailure != null) {
                return persistGatewayFailure(refund, engagement, validationFailure);
            }
            applyProviderDiagnostics(refund, providerState.status(), providerState.failureReason());

            String providerStatus = normalizeProviderStatus(providerState.status());
            if (!isProviderTerminalFailure(providerStatus)) {
                setAwaitingWebhookStatus(refund, providerStatus);
                refund = refundRepository.save(refund);
                auditRefund(refund, engagement, null);
                return refund;
            }
            replacedProviderRefundId = refund.getProviderRefundId();
        }

        String idempotencyKey = replacedProviderRefundId == null
                ? baseIdempotencyKey
                : baseIdempotencyKey + ":after:" + replacedProviderRefundId;
        Map<String, String> metadata = new HashMap<>();
        metadata.put(REFUND_ID_METADATA, refund.getId().toString());
        metadata.put(ENGAGEMENT_ID_METADATA, engagement.getId().toString());
        if (replacedProviderRefundId != null) {
            metadata.put(REPLACES_REFUND_ID_METADATA, replacedProviderRefundId);
        }

        try {
            RefundResult result = paymentGateway.refund(new RefundRequest(
                    payment.getPaymentIntentId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    metadata,
                    idempotencyKey));
            String validationFailure = validateProviderSnapshot(
                    refund,
                    result,
                    result == null ? null : result.providerRef());
            if (validationFailure != null) {
                if (result != null && hasText(result.providerRef())) {
                    refund.setProviderRefundId(result.providerRef());
                }
                applyProviderDiagnostics(
                        refund,
                        result == null ? null : result.status(),
                        validationFailure);
                refund.setStatus(Refund.RefundStatus.FAILED);
            } else {
                refund.setProviderRefundId(result.providerRef());
                applyProviderDiagnostics(refund, result.status(), result.failureReason());
                applySynchronousProviderResult(refund, engagement, payment, result.status());
            }
        } catch (PaymentGatewayException exception) {
            return persistGatewayFailure(refund, engagement, exception.getMessage());
        }

        refund = refundRepository.save(refund);
        auditRefund(refund, engagement, null);
        return refund;
    }

    public PageResponse<RefundResponse> getRefundsByClientId(Long clientId, Pageable pageable) {
        Page<Refund> refunds = refundRepository.findByEngagement_Client_Id(clientId, pageable);
        return PageResponse.of(refunds, this::toResponse);
    }

    public PageResponse<RefundResponse> getAllRefunds(Pageable pageable) {
        return PageResponse.of(refundRepository.findAllBy(pageable), this::toResponse);
    }

    /**
     * Returns the single full-refund lifecycle associated with an engagement, when one exists.
     */
    public Optional<RefundSummaryResponse> getSummaryByEngagementId(Long engagementId) {
        return refundRepository.findFirstByEngagement_IdOrderByCreatedAtAsc(engagementId)
                .map(this::toSummary);
    }

    /**
     * Loads summaries for one page of engagements in one query. The schema enforces one refund
     * lifecycle per engagement, so every map key has at most one value.
     */
    public Map<Long, RefundSummaryResponse> getSummariesByEngagementIds(Set<Long> engagementIds) {
        if (engagementIds == null || engagementIds.isEmpty()) {
            return Map.of();
        }
        return refundRepository.findAllByEngagement_IdIn(engagementIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        refund -> refund.getEngagement().getId(),
                        this::toSummary,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
    }

    public RefundResponse getRefundById(Long id, Long currentUserId, boolean isAdmin) {
        Refund refund = refundRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Refund", id));
        if (!isAdmin && !refund.getEngagement().getClient().getId().equals(currentUserId)) {
            throw new ForbiddenException("Access denied to this refund");
        }
        return toResponse(refund);
    }

    @Transactional
    public void handleRefundChanged(PaymentEventHandler.RefundChanged event) {
        Refund refund = resolveRefundEvent(event);
        if (refund == null) {
            return;
        }
        Engagement engagement = engagementRepository.findByIdForUpdate(refund.getEngagement().getId())
                .orElseThrow(() -> new RetryableWebhookException(
                        "Engagement not found for refund " + refund.getId()));

        String validationFailure = validateRefundEvent(refund, event);
        if (validationFailure != null) {
            applyProviderDiagnostics(refund, event.providerStatus(), validationFailure);
            refund.setStatus(Refund.RefundStatus.FAILED);
            auditRefund(refund, engagement, event.eventId());
            log.warn("Refund event {} failed local financial validation: {}",
                    event.eventId(), validationFailure);
            return;
        }
        if (refund.getStatus() == Refund.RefundStatus.COMPLETED
                && isCompletedRefundState(engagement.getStatus())) {
            return;
        }

        String providerStatus = normalizeProviderStatus(event.providerStatus());
        if (providerStatus == null && "refund.failed".equals(event.eventType())) {
            providerStatus = "failed";
        }
        boolean terminalFailureRecorded = refund.getStatus() == Refund.RefundStatus.FAILED
                && isProviderTerminalFailure(normalizeProviderStatus(refund.getProviderStatus()));
        if (terminalFailureRecorded
                && !"succeeded".equals(providerStatus)
                && !isProviderTerminalFailure(providerStatus)) {
            return;
        }
        applyProviderDiagnostics(refund, providerStatus, event.failureReason());

        switch (providerStatus == null ? "unknown" : providerStatus) {
            case "succeeded" -> completeRefund(refund, engagement, refund.getPayment());
            case "failed", "canceled" -> refund.setStatus(Refund.RefundStatus.FAILED);
            case "pending" -> refund.setStatus(Refund.RefundStatus.PENDING);
            case "requires_action" -> refund.setStatus(Refund.RefundStatus.PROCESSING);
            default -> refund.setStatus(Refund.RefundStatus.PROCESSING);
        }
        auditRefund(refund, engagement, event.eventId());
    }

    @Transactional
    public void handleChargeRefunded(PaymentEventHandler.ChargeRefunded event) {
        // This event reports the aggregate amount refunded on a Charge, including external and
        // partial refunds. It cannot prove that our specific provider Refund object succeeded.
        // Refund-object events are authoritative; acknowledge this signed compatibility signal
        // without mutating the marketplace ledger.
        log.info("Acknowledging aggregate charge.refunded event {} without local state changes",
                event.eventId());
    }

    private Refund resolveRefundEvent(PaymentEventHandler.RefundChanged event) {
        Refund byProviderReference = refundRepository
                .findByProviderRefundIdForUpdate(event.refundId())
                .orElse(null);
        if (byProviderReference != null) {
            return byProviderReference;
        }

        Long metadataRefundId = parsePositiveLong(event.localRefundId());
        Long metadataEngagementId = parsePositiveLong(event.localEngagementId());
        if (metadataRefundId != null && metadataEngagementId != null) {
            Refund byMetadata = refundRepository.findByIdForWebhookUpdate(metadataRefundId)
                    .orElseThrow(() -> new RetryableWebhookException(
                            "Metadata refund " + metadataRefundId + " is not committed yet"));
            if (!metadataEngagementId.equals(byMetadata.getEngagement().getId())) {
                log.warn("Ignoring refund event {} with mismatched engagement metadata",
                        event.eventId());
                return null;
            }

            String currentProviderRef = byMetadata.getProviderRefundId();
            if (!hasText(currentProviderRef)) {
                byMetadata.setProviderRefundId(event.refundId());
                return byMetadata;
            }
            if (currentProviderRef.equals(event.refundId())) {
                return byMetadata;
            }
            if (byMetadata.getStatus() == Refund.RefundStatus.FAILED
                    && currentProviderRef.equals(event.replacesRefundId())) {
                byMetadata.setProviderRefundId(event.refundId());
                return byMetadata;
            }

            log.info("Ignoring stale refund event {} for superseded provider refund {}",
                    event.eventId(), event.refundId());
            return null;
        }

        if (hasText(event.paymentIntentId())
                && refundRepository
                        .existsByPayment_PaymentIntentIdAndProviderRefundIdIsNullAndStatusIn(
                                event.paymentIntentId(),
                                UNBOUND_WEBHOOK_STATES)) {
            throw new RetryableWebhookException(
                    "An unbound local refund may still be waiting for provider reference "
                            + event.refundId());
        }

        log.info("Ignoring unrelated provider refund {}", event.refundId());
        return null;
    }

    private void assertRefundable(Payment payment) {
        if (payment.getStatus() != Payment.PaymentStatus.SUCCEEDED) {
            throw new ConflictException(
                    "Only a successful payment can be refunded",
                    "PAYMENT_NOT_REFUNDABLE",
                    Map.of("paymentStatus", payment.getStatus().name()));
        }
        if (!hasText(payment.getPaymentIntentId())) {
            throw new ConflictException(
                    "Payment has no provider reference",
                    "PAYMENT_PROVIDER_REFERENCE_MISSING");
        }
    }

    private void applySynchronousProviderResult(
            Refund refund,
            Engagement engagement,
            Payment payment,
            String rawProviderStatus) {
        String providerStatus = normalizeProviderStatus(rawProviderStatus);
        if (paymentGateway.webhookDriven()) {
            if (isProviderTerminalFailure(providerStatus)) {
                refund.setStatus(Refund.RefundStatus.FAILED);
            } else {
                // Even a synchronous "succeeded" result is not financial confirmation. Only
                // a signed provider event advances a webhook-driven refund to COMPLETED.
                setAwaitingWebhookStatus(refund, providerStatus);
            }
            return;
        }

        if ("succeeded".equals(providerStatus)) {
            completeRefund(refund, engagement, payment);
        } else if (isProviderTerminalFailure(providerStatus)) {
            refund.setStatus(Refund.RefundStatus.FAILED);
        } else {
            setAwaitingWebhookStatus(refund, providerStatus);
        }
    }

    private void setAwaitingWebhookStatus(Refund refund, String providerStatus) {
        refund.setStatus("requires_action".equals(providerStatus)
                ? Refund.RefundStatus.PROCESSING
                : Refund.RefundStatus.PENDING);
    }

    private Refund persistGatewayFailure(
            Refund refund,
            Engagement engagement,
            String failureMessage) {
        refund.setStatus(Refund.RefundStatus.FAILED);
        refund.setFailureMessage(truncateReason(failureMessage));
        refund = refundRepository.save(refund);
        log.warn("Refund gateway attempt failed for engagement {}: {}",
                engagement.getId(), failureMessage);
        auditRefund(refund, engagement, null);
        return refund;
    }

    private String validateProviderSnapshot(
            Refund refund,
            RefundResult providerState,
            String expectedProviderRef) {
        if (providerState == null || !hasText(providerState.providerRef())) {
            return "Provider refund response is missing its refund id";
        }
        if (hasText(expectedProviderRef)
                && !expectedProviderRef.equals(providerState.providerRef())) {
            return "Provider refund id does not match the local refund";
        }
        return validateFinancialAssociation(
                refund,
                providerState.paymentIntentId(),
                providerState.chargeId(),
                providerState.amount(),
                providerState.currency());
    }

    private String validateRefundEvent(
            Refund refund,
            PaymentEventHandler.RefundChanged event) {
        if (!event.refundId().equals(refund.getProviderRefundId())) {
            return "Provider refund event id does not match the local refund";
        }
        return validateFinancialAssociation(
                refund,
                event.paymentIntentId(),
                event.chargeId(),
                event.amount(),
                event.currency());
    }

    private String validateFinancialAssociation(
            Refund refund,
            String providerPaymentIntentId,
            String providerChargeId,
            Long providerAmount,
            String providerCurrency) {
        Payment payment = refund.getPayment();
        boolean associationMatched = false;
        if (hasText(providerPaymentIntentId)
                && !providerPaymentIntentId.equals(payment.getPaymentIntentId())) {
            return "Provider refund references a different PaymentIntent";
        }
        if (hasText(providerPaymentIntentId)) {
            associationMatched = true;
        }
        if (hasText(providerChargeId)
                && hasText(payment.getChargeId())
                && !providerChargeId.equals(payment.getChargeId())) {
            return "Provider refund references a different charge";
        }
        if (hasText(providerChargeId) && hasText(payment.getChargeId())) {
            associationMatched = true;
        }
        if (!associationMatched) {
            return "Provider refund has no verifiable payment association";
        }

        MinorUnitConverter.MinorUnitAmount expected;
        try {
            expected = MinorUnitConverter.convert(refund.getAmount(), payment.getCurrency());
        } catch (IllegalArgumentException exception) {
            return "Local refund amount or currency cannot be represented by the provider";
        }
        if (providerAmount == null) {
            return "Provider refund is missing its amount";
        }
        if (providerAmount != expected.value()) {
            return "Provider refund amount does not match the full local refund";
        }
        if (!hasText(providerCurrency)
                || !expected.currency().equals(providerCurrency.trim().toLowerCase(Locale.ROOT))) {
            return "Provider refund currency does not match the local payment";
        }
        return null;
    }

    private void applyProviderDiagnostics(
            Refund refund,
            String providerStatus,
            String failureMessage) {
        refund.setProviderStatus(normalizeProviderStatus(providerStatus));
        refund.setFailureMessage(failureMessage == null ? null : truncateReason(failureMessage));
    }

    private void auditRefund(Refund refund, Engagement engagement, String eventId) {
        Map<String, Object> details = new HashMap<>();
        details.put("refundId", refund.getId());
        details.put("amount", refund.getAmount());
        if (eventId != null) {
            details.put("eventId", eventId);
        }
        if (refund.getProviderStatus() != null) {
            details.put("providerStatus", refund.getProviderStatus());
        }
        auditService.log(
                "ENGAGEMENT",
                engagement.getId(),
                refund.getStatus() == Refund.RefundStatus.COMPLETED
                        ? "REFUND_COMPLETED"
                        : refund.getStatus() == Refund.RefundStatus.FAILED
                                ? "REFUND_FAILED"
                                : "REFUND_PENDING",
                "SYSTEM",
                null,
                details);
    }

    private boolean isProviderTerminalFailure(String providerStatus) {
        return "failed".equals(providerStatus) || "canceled".equals(providerStatus);
    }

    private String normalizeProviderStatus(String providerStatus) {
        return providerStatus == null || providerStatus.isBlank()
                ? null
                : providerStatus.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Long parsePositiveLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void completeRefund(Refund refund, Engagement engagement, Payment payment) {
        completeEngagementRefundState(engagement);
        refund.setStatus(Refund.RefundStatus.COMPLETED);
        refund.setRefundedAt(refund.getRefundedAt() == null ? Instant.now() : refund.getRefundedAt());
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        if (payment.getPaidAt() == null) {
            payment.setPaidAt(Instant.now());
        }
    }

    private void completeEngagementRefundState(Engagement engagement) {
        EngagementStatus currentStatus = engagement.getStatus();
        if (currentStatus == EngagementStatus.CANCELLED
                || currentStatus == EngagementStatus.REFUNDED) {
            return;
        }

        EngagementStateValidator.validateForOperation(
                currentStatus,
                EngagementStatus.REFUNDED,
                "complete refund");
        engagement.setStatus(EngagementStatus.REFUNDED);
    }

    private boolean isCompletedRefundState(EngagementStatus status) {
        return status == EngagementStatus.CANCELLED || status == EngagementStatus.REFUNDED;
    }

    String truncateReason(String reason) {
        String normalized = reason == null || reason.isBlank() ? "Refund requested" : reason.trim();
        return normalized.length() > MAX_REASON_LENGTH
                ? normalized.substring(0, MAX_REASON_LENGTH)
                : normalized;
    }

    public RefundResponse toResponse(Refund refund) {
        return RefundResponse.builder()
                .id(refund.getId())
                .engagementId(refund.getEngagement().getId())
                .paymentId(refund.getPayment().getId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .status(refund.getStatus().name())
                .providerRefundId(refund.getProviderRefundId())
                .providerStatus(refund.getProviderStatus())
                .failureMessage(refund.getFailureMessage())
                .refundedAt(refund.getRefundedAt())
                .createdAt(refund.getCreatedAt())
                .updatedAt(refund.getUpdatedAt())
                .build();
    }

    private RefundSummaryResponse toSummary(Refund refund) {
        return new RefundSummaryResponse(
                refund.getId(),
                refund.getAmount(),
                refund.getStatus(),
                refund.getRefundedAt(),
                refund.getStatus() == Refund.RefundStatus.FAILED
                        ? refund.getFailureMessage()
                        : null,
                refund.getUpdatedAt());
    }
}
