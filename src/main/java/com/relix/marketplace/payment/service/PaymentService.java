package com.relix.marketplace.payment.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.engagement.validator.EngagementStateValidator;
import com.relix.marketplace.payment.dto.PaymentRequest;
import com.relix.marketplace.payment.dto.PaymentResponse;
import com.relix.marketplace.payment.entity.Payment;
import com.relix.marketplace.payment.gateway.MinorUnitConverter;
import com.relix.marketplace.payment.gateway.PaymentGateway;
import com.relix.marketplace.payment.gateway.PaymentGatewayException;
import com.relix.marketplace.payment.gateway.PaymentIntentRequest;
import com.relix.marketplace.payment.gateway.PaymentIntentResult;
import com.relix.marketplace.payment.repository.PaymentRepository;
import com.relix.marketplace.payment.repository.SupersededPaymentIntentRepository;
import com.relix.marketplace.payment.webhook.PaymentEventHandler;
import com.relix.marketplace.payment.webhook.RetryableWebhookException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentService {

    private static final int MAX_FAILURE_LENGTH = 500;
    private static final String PROVIDER_STATUS_CANCELED = "canceled";
    private static final String PROVIDER_CANCELED_MESSAGE =
            "The payment provider canceled this payment intent; start a new payment attempt.";
    private static final Set<String> CLIENT_CONFIRMABLE_PROVIDER_STATUSES = Set.of(
            "requires_payment_method",
            "requires_confirmation",
            "requires_action");

    private final PaymentRepository paymentRepository;
    private final SupersededPaymentIntentRepository supersededPaymentIntentRepository;
    private final EngagementRepository engagementRepository;
    private final PaymentGateway paymentGateway;
    private final AuditService auditService;

    @Transactional
    public PaymentResponse pay(Engagement engagement, PaymentRequest request) {
        validateRequest(request);

        Payment existing = paymentRepository.findByEngagement_Id(engagement.getId()).orElse(null);
        boolean requestIdMatchedBeforeCall = existing != null
                && Objects.equals(existing.getRequestId(), request.getRequestId());
        String replacedProviderRef = null;
        if (existing != null && existing.getStatus() == Payment.PaymentStatus.PENDING) {
            if (!requestIdMatchedBeforeCall) {
                throw new ConflictException(
                        "A different payment request is already pending for this engagement",
                        "PAYMENT_IN_PROGRESS",
                        Map.of("paymentId", existing.getId()));
            }
            PaymentIntentResult result = retrieveExistingIntent(existing);
            String providerStatus = normalizeProviderStatus(result.status());
            existing.setProviderStatus(providerStatus);
            if (PROVIDER_STATUS_CANCELED.equals(providerStatus)) {
                existing.setStatus(Payment.PaymentStatus.FAILED);
                existing.setFailureMessage(PROVIDER_CANCELED_MESSAGE);
            }
            return toResponse(existing, false, true, clientSecretFor(result));
        }
        if (existing != null && existing.getStatus() != Payment.PaymentStatus.FAILED) {
            if (existing.getStatus() == Payment.PaymentStatus.SUCCEEDED
                    && engagement.getStatus() == EngagementStatus.ACCEPTED) {
                fundEngagement(engagement);
            }
            return toResponse(existing, true, requestIdMatchedBeforeCall);
        }

        if (engagement.getStatus() != EngagementStatus.ACCEPTED) {
            throw new ConflictException(
                    "Only an accepted engagement can be paid",
                    "ENGAGEMENT_NOT_PAYABLE",
                    Map.of("status", engagement.getStatus().name()));
        }

        if (existing != null
                && hasProviderReference(existing)
                && paymentGateway.webhookDriven()) {
            PaymentIntentResult result = retrieveExistingIntent(existing);
            String providerStatus = normalizeProviderStatus(result.status());
            existing.setProviderStatus(providerStatus);
            if (!PROVIDER_STATUS_CANCELED.equals(providerStatus)) {
                existing.setRequestId(request.getRequestId());
                existing.setStatus(Payment.PaymentStatus.PENDING);
                existing.setFailureMessage(null);
                return toResponse(
                        existing,
                        false,
                        requestIdMatchedBeforeCall,
                        clientSecretFor(result));
            }
            replacedProviderRef = existing.getPaymentIntentId();
        }

        String currency = engagement.getTicket().getCurrency() == null
                ? "USD"
                : engagement.getTicket().getCurrency().trim().toUpperCase(Locale.ROOT);
        Payment payment = existing == null
                ? Payment.builder().engagement(engagement).build()
                : existing;
        resetForAttempt(payment, request.getRequestId(), engagement, currency);

        String idempotencyKey = gatewayIdempotencyKey(
                engagement.getId(),
                request.getRequestId(),
                replacedProviderRef);
        String transientClientSecret = null;
        try {
            PaymentIntentResult result = paymentGateway.createIntent(new PaymentIntentRequest(
                    engagement.getAmount(),
                    currency,
                    Map.of(
                            "engagementId", engagement.getId().toString(),
                            "clientId", engagement.getClient().getId().toString(),
                            "workerId", engagement.getWorker().getUser().getId().toString()),
                    idempotencyKey));

            if (replacedProviderRef != null
                    && !Objects.equals(replacedProviderRef, result.providerRef())) {
                recordSupersededPaymentIntent(payment, replacedProviderRef);
            }
            payment.setPaymentIntentId(result.providerRef());
            payment.setProviderStatus(result.status());

            if (paymentGateway.webhookDriven()) {
                payment.setStatus(Payment.PaymentStatus.PENDING);
                transientClientSecret = clientSecretFor(result);
            } else {
                payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
                payment.setPaidAt(Instant.now());
                fundEngagement(engagement);
            }
        } catch (PaymentGatewayException exception) {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setProviderStatus("gateway_error");
            payment.setFailureMessage(truncate(exception.getMessage()));
            log.warn("Payment gateway attempt failed for engagement {}: {}",
                    engagement.getId(), exception.getMessage());
        }

        payment = paymentRepository.save(payment);
        auditService.log(
                "ENGAGEMENT",
                engagement.getId(),
                payment.getStatus() == Payment.PaymentStatus.SUCCEEDED
                        ? "PAYMENT_SUCCEEDED"
                        : payment.getStatus() == Payment.PaymentStatus.FAILED
                                ? "PAYMENT_FAILED"
                                : "PAYMENT_PENDING",
                "CLIENT",
                engagement.getClient().getId(),
                Map.of(
                        "paymentId", payment.getId(),
                        "amount", payment.getAmount(),
                        "currency", payment.getCurrency()));
        return toResponse(payment, false, requestIdMatchedBeforeCall, transientClientSecret);
    }

    public PaymentResponse getPaymentByEngagementId(Long engagementId) {
        Payment payment = paymentRepository.findByEngagement_Id(engagementId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment for engagement",
                        engagementId));
        return toResponse(payment, false, false);
    }

    public boolean hasSucceededPayment(Long engagementId) {
        return paymentRepository.findByEngagement_Id(engagementId)
                .map(payment -> payment.getStatus() == Payment.PaymentStatus.SUCCEEDED)
                .orElse(false);
    }

    public boolean hasPendingPayment(Long engagementId) {
        return paymentRepository.findByEngagement_Id(engagementId)
                .map(payment -> payment.getStatus() == Payment.PaymentStatus.PENDING)
                .orElse(false);
    }

    @Transactional
    public void handlePaymentSucceeded(PaymentEventHandler.PaymentIntentSucceeded event) {
        Payment payment = paymentRepository.findByPaymentIntentIdForUpdate(event.paymentIntentId())
                .orElseGet(() -> {
                    if (supersededPaymentIntentRepository
                            .existsByPaymentIntentId(event.paymentIntentId())) {
                        throw new RetryableWebhookException(
                                "A superseded PaymentIntent reported success and requires reconciliation: "
                                        + event.paymentIntentId());
                    }
                    throw new RetryableWebhookException(
                            "Payment not found for PaymentIntent " + event.paymentIntentId());
                });
        validateSucceededEvent(payment, event);
        Engagement engagement = engagementRepository.findByIdForUpdate(payment.getEngagement().getId())
                .orElseThrow(() -> new RetryableWebhookException(
                        "Engagement not found for payment " + payment.getId()));

        if (payment.getStatus() != Payment.PaymentStatus.REFUNDED) {
            payment.setStatus(Payment.PaymentStatus.SUCCEEDED);
            payment.setPaidAt(payment.getPaidAt() == null ? Instant.now() : payment.getPaidAt());
            payment.setChargeId(event.latestChargeId());
            payment.setProviderStatus(event.providerStatus());
            payment.setFailureMessage(null);
        }

        if (engagement.getStatus() == EngagementStatus.ACCEPTED) {
            fundEngagement(engagement);
        }
        auditService.log(
                "ENGAGEMENT",
                engagement.getId(),
                "PAYMENT_SUCCEEDED",
                "SYSTEM",
                null,
                Map.of("paymentId", payment.getId(), "eventId", event.eventId()));
    }

    @Transactional
    public void handlePaymentFailed(PaymentEventHandler.PaymentIntentFailed event) {
        Payment payment = paymentRepository.findByPaymentIntentIdForUpdate(event.paymentIntentId())
                .orElse(null);
        if (payment == null) {
            if (supersededPaymentIntentRepository.existsByPaymentIntentId(event.paymentIntentId())) {
                log.info("Ignoring late failure for superseded PaymentIntent {}",
                        event.paymentIntentId());
                return;
            }
            throw new RetryableWebhookException(
                    "Payment not found for PaymentIntent " + event.paymentIntentId());
        }
        if (payment.getStatus() == Payment.PaymentStatus.SUCCEEDED
                || payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
            return;
        }

        payment.setStatus(Payment.PaymentStatus.FAILED);
        payment.setProviderStatus(event.providerStatus());
        payment.setFailureMessage(truncate(combineFailure(event.failureCode(), event.failureMessage())));
        auditService.log(
                "ENGAGEMENT",
                payment.getEngagement().getId(),
                "PAYMENT_FAILED",
                "SYSTEM",
                null,
                Map.of("paymentId", payment.getId(), "eventId", event.eventId()));
    }

    private void fundEngagement(Engagement engagement) {
        EngagementStateValidator.validateForOperation(
                engagement.getStatus(),
                EngagementStatus.FUNDED,
                "fund");
        engagement.setStatus(EngagementStatus.FUNDED);
        engagement.setFundedAt(Instant.now());
    }

    private void validateSucceededEvent(
            Payment payment,
            PaymentEventHandler.PaymentIntentSucceeded event) {
        MinorUnitConverter.MinorUnitAmount expected;
        try {
            expected = MinorUnitConverter.convert(payment.getAmount(), payment.getCurrency());
        } catch (IllegalArgumentException exception) {
            throw new RetryableWebhookException(
                    "Local payment amount or currency cannot be reconciled", exception);
        }

        if (event.amountReceived() == null || event.amountReceived() != expected.value()) {
            throw new RetryableWebhookException(
                    "PaymentIntent amount_received does not match the local payment");
        }
        if (event.currency() == null
                || !expected.currency().equals(event.currency().trim().toLowerCase(Locale.ROOT))) {
            throw new RetryableWebhookException(
                    "PaymentIntent currency does not match the local payment");
        }
    }

    private void recordSupersededPaymentIntent(Payment payment, String paymentIntentId) {
        if (payment.getId() == null) {
            throw new IllegalStateException(
                    "Cannot supersede a PaymentIntent before the local payment is persisted");
        }
        int inserted = supersededPaymentIntentRepository.insertIfAbsent(
                payment.getId(),
                paymentIntentId);
        if (inserted == 0 && !supersededPaymentIntentRepository
                .existsByPayment_IdAndPaymentIntentId(payment.getId(), paymentIntentId)) {
            throw new IllegalStateException(
                    "PaymentIntent is already superseded by a different payment: "
                            + paymentIntentId);
        }
    }

    private void resetForAttempt(
            Payment payment,
            String requestId,
            Engagement engagement,
            String currency) {
        payment.setEngagement(engagement);
        payment.setRequestId(requestId);
        payment.setAmount(engagement.getAmount());
        payment.setCurrency(currency);
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setPaidAt(null);
        payment.setChargeId(null);
        payment.setProviderStatus(null);
        payment.setFailureMessage(null);
    }

    private PaymentIntentResult retrieveExistingIntent(Payment payment) {
        if (!hasProviderReference(payment)) {
            throw new ConflictException(
                    "Pending payment has no provider reference",
                    "PAYMENT_PROVIDER_REFERENCE_MISSING",
                    Map.of("paymentId", payment.getId()));
        }
        return paymentGateway.retrieveIntent(payment.getPaymentIntentId());
    }

    private boolean hasProviderReference(Payment payment) {
        return payment.getPaymentIntentId() != null && !payment.getPaymentIntentId().isBlank();
    }

    private String clientSecretFor(PaymentIntentResult result) {
        String providerStatus = normalizeProviderStatus(result.status());
        return providerStatus != null && CLIENT_CONFIRMABLE_PROVIDER_STATUSES.contains(providerStatus)
                ? result.clientSecret()
                : null;
    }

    private String normalizeProviderStatus(String status) {
        return status == null ? null : status.trim().toLowerCase(Locale.ROOT);
    }

    private void validateRequest(PaymentRequest request) {
        if (request == null || request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new BusinessException("Request ID cannot be blank", "INVALID_REQUEST_ID");
        }
    }

    private String gatewayIdempotencyKey(
            Long engagementId,
            String requestId,
            String replacedProviderRef) {
        String baseKey = "engagement-pay:" + engagementId + ":" + requestId;
        return replacedProviderRef == null
                ? baseKey
                : baseKey + ":after:" + replacedProviderRef;
    }

    private String combineFailure(String code, String message) {
        if (code == null || code.isBlank()) {
            return message == null ? "Payment failed" : message;
        }
        return message == null || message.isBlank() ? code : code + ": " + message;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_FAILURE_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_FAILURE_LENGTH);
    }

    public PaymentResponse toResponse(
            Payment payment,
            boolean alreadyPaid,
            boolean requestIdMatched) {
        return toResponse(payment, alreadyPaid, requestIdMatched, null);
    }

    private PaymentResponse toResponse(
            Payment payment,
            boolean alreadyPaid,
            boolean requestIdMatched,
            String transientClientSecret) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .engagementId(payment.getEngagement().getId())
                .requestId(payment.getRequestId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus().name())
                .paidAt(payment.getPaidAt())
                .paymentIntentId(payment.getPaymentIntentId())
                .clientSecret(transientClientSecret)
                .providerStatus(payment.getProviderStatus())
                .failureMessage(payment.getFailureMessage())
                .alreadyPaid(alreadyPaid)
                .requestIdMatched(requestIdMatched)
                .build();
    }
}
