package com.relix.marketplace.refund.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.common.exception.ConflictException;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.payment.entity.Payment;
import com.relix.marketplace.payment.gateway.PaymentGateway;
import com.relix.marketplace.payment.gateway.RefundRequest;
import com.relix.marketplace.payment.gateway.RefundResult;
import com.relix.marketplace.payment.repository.PaymentRepository;
import com.relix.marketplace.payment.webhook.PaymentEventHandler;
import com.relix.marketplace.payment.webhook.RetryableWebhookException;
import com.relix.marketplace.refund.dto.RefundSummaryResponse;
import com.relix.marketplace.refund.entity.Refund;
import com.relix.marketplace.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private EngagementRepository engagementRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private AuditService auditService;
    @InjectMocks private RefundService refundService;

    private Engagement engagement;
    private Payment payment;

    @BeforeEach
    void setUp() {
        engagement = Engagement.builder()
                .status(EngagementStatus.CANCELLED)
                .amount(new BigDecimal("40.00"))
                .build();
        engagement.setId(10L);
        payment = Payment.builder()
                .engagement(engagement)
                .requestId("req")
                .amount(new BigDecimal("40.00"))
                .currency("USD")
                .paymentIntentId("pi_1")
                .status(Payment.PaymentStatus.SUCCEEDED)
                .build();
        payment.setId(20L);
    }

    @Test
    void mockCancellationRefundCompletesFinancialStateAndLeavesEngagementCancelled() {
        mockNewRefund();
        when(paymentGateway.webhookDriven()).thenReturn(false);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_mock_1", "succeeded"));

        Refund refund = refundService.createRefund(engagement, "cancelled");

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
        assertNotNull(refund.getRefundedAt());
    }

    @Test
    void mockDisputeRefundTransitionsEngagementToRefunded() {
        engagement.setStatus(EngagementStatus.DISPUTED);
        mockNewRefund();
        when(paymentGateway.webhookDriven()).thenReturn(false);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_mock_1", "succeeded"));

        Refund refund = refundService.createRefund(engagement, "client claim upheld");

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(EngagementStatus.REFUNDED, engagement.getStatus());
    }

    @Test
    void alreadyRefundedEngagementAllowsIdempotentCompletion() {
        engagement.setStatus(EngagementStatus.REFUNDED);
        mockNewRefund();
        when(paymentGateway.webhookDriven()).thenReturn(false);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_mock_1", "succeeded"));

        Refund refund = refundService.createRefund(engagement, "client claim upheld");

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(EngagementStatus.REFUNDED, engagement.getStatus());
    }

    @Test
    void mockRefundCompletionRejectsUnexpectedEngagementState() {
        engagement.setStatus(EngagementStatus.FUNDED);
        mockNewRefundCreation();
        when(paymentGateway.webhookDriven()).thenReturn(false);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_mock_1", "succeeded"));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> refundService.createRefund(engagement, "unexpected"));

        assertEquals("INVALID_ENGAGEMENT_STATE", exception.getCode());
        assertEquals(EngagementStatus.FUNDED, engagement.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
    }

    @Test
    void stripeRefundStaysPendingUntilWebhook() {
        mockNewRefund();
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_1", "pending"));

        Refund refund = refundService.createRefund(engagement, "cancelled");

        assertEquals(Refund.RefundStatus.PENDING, refund.getStatus());
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
        assertEquals("re_1", refund.getProviderRefundId());
    }

    @Test
    void synchronousStripeSuccessStillWaitsForSignedRefundWebhook() {
        mockNewRefund();
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_1", "succeeded"));

        Refund refund = refundService.createRefund(engagement, "cancelled");

        assertEquals(Refund.RefundStatus.PENDING, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("succeeded", refund.getProviderStatus());
    }

    @Test
    void synchronousProviderFailureIsRecordedWithoutCompletingRefund() {
        mockNewRefund();
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.refund(any())).thenReturn(new RefundResult(
                "re_failed", "failed", 4000L, "usd", "pi_1", null, "insufficient_funds"));

        Refund refund = refundService.createRefund(engagement, "cancelled");

        assertEquals(Refund.RefundStatus.FAILED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("insufficient_funds", refund.getFailureMessage());
    }

    @Test
    void failedProviderRefundIsRetrievedBeforeStableReplacementIsCreated() {
        Refund refund = failedRefund("re_old", "failed");
        mockExistingRefund(refund);
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.retrieveRefund("re_old"))
                .thenReturn(providerResult("re_old", "failed"));
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_new", "pending"));

        Refund recovered = refundService.createRefund(engagement, "retry");

        ArgumentCaptor<RefundRequest> request = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentGateway).refund(request.capture());
        assertEquals("engagement-refund:10:after:re_old", request.getValue().idempotencyKey());
        assertEquals("30", request.getValue().metadata().get("marketplaceRefundId"));
        assertEquals("re_old", request.getValue().metadata().get("marketplaceReplacesRefundId"));
        assertEquals("re_new", recovered.getProviderRefundId());
        assertEquals(Refund.RefundStatus.PENDING, recovered.getStatus());
    }

    @Test
    void failedLocalRefundWithPendingProviderStateDoesNotCreateDuplicate() {
        Refund refund = failedRefund("re_existing", null);
        mockExistingRefund(refund);
        when(paymentGateway.retrieveRefund("re_existing"))
                .thenReturn(providerResult("re_existing", "pending"));

        Refund recovered = refundService.createRefund(engagement, "retry");

        assertEquals(Refund.RefundStatus.PENDING, recovered.getStatus());
        verify(paymentGateway, never()).refund(any());
    }

    @Test
    void failedLocalRefundWithoutProviderReferenceReusesOriginalIdempotencyKey() {
        Refund refund = failedRefund(null, null);
        mockExistingRefund(refund);
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.refund(any())).thenReturn(providerResult("re_recovered", "pending"));

        refundService.createRefund(engagement, "retry");

        ArgumentCaptor<RefundRequest> request = ArgumentCaptor.forClass(RefundRequest.class);
        verify(paymentGateway).refund(request.capture());
        assertEquals("engagement-refund:10", request.getValue().idempotencyKey());
        verify(paymentGateway, never()).retrieveRefund(any());
    }

    @Test
    void aggregateChargeRefundedSignalDoesNotMutateLocalFinancialState() {
        Refund refund = pendingRefund();

        refundService.handleChargeRefunded(new PaymentEventHandler.ChargeRefunded(
                "evt_1", "ch_1", "pi_1", 4000L, "usd"));

        assertEquals(Refund.RefundStatus.PENDING, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
        verifyNoInteractions(refundRepository, engagementRepository, auditService);
    }

    @Test
    void refundObjectSucceededEventIsAuthoritativeCompletionSignal() {
        Refund refund = pendingRefund();
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        refundService.handleRefundChanged(refundEvent("refund.updated", "succeeded", 4000L));

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
    }

    @Test
    void refundFailedEventKeepsPaymentAndEngagementUnchanged() {
        Refund refund = pendingRefund();
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        refundService.handleRefundChanged(refundEvent("refund.failed", "failed", 4000L));

        assertEquals(Refund.RefundStatus.FAILED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
    }

    @Test
    void partialRefundObjectCannotCompleteFullLocalRefund() {
        Refund refund = pendingRefund();
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        refundService.handleRefundChanged(refundEvent("refund.updated", "succeeded", 1000L));

        assertEquals(Refund.RefundStatus.FAILED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        assertTrue(refund.getFailureMessage().contains("full local refund"));
    }

    @Test
    void unrelatedUnknownRefundIsSafelyIgnored() {
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.empty());

        refundService.handleRefundChanged(refundEvent("refund.updated", "succeeded", 4000L));

        verifyNoInteractions(engagementRepository, auditService);
    }

    @Test
    void metadataForUncommittedLocalRefundRequestsProviderRetry() {
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.empty());
        when(refundRepository.findByIdForWebhookUpdate(30L)).thenReturn(Optional.empty());
        PaymentEventHandler.RefundChanged event = new PaymentEventHandler.RefundChanged(
                "evt_1", "refund.created", "re_1", "pending", "pi_1", null,
                4000L, "USD", null, "30", "10", null);

        assertThrows(
                RetryableWebhookException.class,
                () -> refundService.handleRefundChanged(event));
    }

    @Test
    void disputeRefundWebhookTransitionsEngagementToRefunded() {
        engagement.setStatus(EngagementStatus.DISPUTED);
        Refund refund = pendingRefund();
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        refundService.handleRefundChanged(refundEvent("refund.updated", "succeeded", 4000L));

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(EngagementStatus.REFUNDED, engagement.getStatus());
    }

    @Test
    void partialChargeRefundedCompatibilitySignalDoesNotCompleteLocalRefund() {
        Refund refund = pendingRefund();

        refundService.handleChargeRefunded(new PaymentEventHandler.ChargeRefunded(
                "evt_partial", "ch_1", "pi_1", 1000L, "usd"));

        assertEquals(Refund.RefundStatus.PENDING, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        verifyNoInteractions(refundRepository, engagementRepository, auditService);
    }

    @Test
    void alreadyRefundedEngagementAllowsIdempotentWebhookCompletion() {
        engagement.setStatus(EngagementStatus.REFUNDED);
        Refund refund = pendingRefund();
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        refundService.handleRefundChanged(refundEvent("refund.updated", "succeeded", 4000L));

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(Payment.PaymentStatus.REFUNDED, payment.getStatus());
        assertEquals(EngagementStatus.REFUNDED, engagement.getStatus());
    }

    @Test
    void completedCancellationWebhookIsIdempotent() {
        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        Refund refund = pendingRefund();
        refund.setStatus(Refund.RefundStatus.COMPLETED);
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        refundService.handleRefundChanged(refundEvent("refund.updated", "succeeded", 4000L));

        assertEquals(Refund.RefundStatus.COMPLETED, refund.getStatus());
        assertEquals(EngagementStatus.CANCELLED, engagement.getStatus());
        verifyNoInteractions(auditService);
    }

    @Test
    void refundWebhookRejectsUnexpectedEngagementState() {
        engagement.setStatus(EngagementStatus.IN_PROGRESS);
        Refund refund = pendingRefund();
        refund.setProviderRefundId("re_1");
        when(refundRepository.findByProviderRefundIdForUpdate("re_1"))
                .thenReturn(Optional.of(refund));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> refundService.handleRefundChanged(
                        refundEvent("refund.updated", "succeeded", 4000L)));

        assertEquals("INVALID_ENGAGEMENT_STATE", exception.getCode());
        assertEquals(Refund.RefundStatus.PENDING, refund.getStatus());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals(EngagementStatus.IN_PROGRESS, engagement.getStatus());
    }

    @Test
    void emptyEngagementIdsReturnNoSummariesWithoutQueryingTheRepository() {
        Map<Long, RefundSummaryResponse> summaries = refundService.getSummariesByEngagementIds(Set.of());

        assertTrue(summaries.isEmpty());
        verifyNoInteractions(refundRepository);
    }

    @Test
    void batchSummaryLookupMapsMultipleEngagementsWithOneRepositoryQueryAndRedactsStaleFailures() {
        Engagement secondEngagement = Engagement.builder()
                .status(EngagementStatus.REFUNDED)
                .amount(new BigDecimal("55.00"))
                .build();
        secondEngagement.setId(11L);
        Payment secondPayment = Payment.builder()
                .engagement(secondEngagement)
                .requestId("req-2")
                .amount(new BigDecimal("55.00"))
                .currency("USD")
                .paymentIntentId("pi_2")
                .status(Payment.PaymentStatus.REFUNDED)
                .build();
        secondPayment.setId(21L);

        Refund pending = Refund.builder()
                .engagement(engagement)
                .payment(payment)
                .amount(new BigDecimal("40.00"))
                .reason("cancelled")
                .status(Refund.RefundStatus.PENDING)
                .failureMessage("stale provider diagnostic")
                .build();
        pending.setId(30L);
        pending.setUpdatedAt(Instant.parse("2026-08-05T13:00:00Z"));

        Refund failed = Refund.builder()
                .engagement(secondEngagement)
                .payment(secondPayment)
                .amount(new BigDecimal("55.00"))
                .reason("provider failed")
                .status(Refund.RefundStatus.FAILED)
                .failureMessage("insufficient_funds")
                .build();
        failed.setId(31L);
        failed.setRefundedAt(Instant.parse("2026-08-05T13:01:00Z"));
        failed.setUpdatedAt(Instant.parse("2026-08-05T13:02:00Z"));

        when(refundRepository.findAllByEngagement_IdIn(argThat(ids ->
                Set.copyOf(ids).equals(Set.of(10L, 11L)))))
                .thenReturn(List.of(pending, failed));

        Map<Long, RefundSummaryResponse> summaries = refundService.getSummariesByEngagementIds(
                Set.of(10L, 11L));

        assertEquals(2, summaries.size());
        assertEquals(Refund.RefundStatus.PENDING, summaries.get(10L).status());
        assertNull(summaries.get(10L).failureMessage());
        assertEquals(Refund.RefundStatus.FAILED, summaries.get(11L).status());
        assertEquals("insufficient_funds", summaries.get(11L).failureMessage());
        assertEquals(Instant.parse("2026-08-05T13:01:00Z"), summaries.get(11L).refundedAt());
        assertEquals(Instant.parse("2026-08-05T13:02:00Z"), summaries.get(11L).updatedAt());
        verify(refundRepository).findAllByEngagement_IdIn(argThat(ids ->
                Set.copyOf(ids).equals(Set.of(10L, 11L))));
    }

    @Test
    void truncatesLongReasonToDatabaseLimit() {
        assertEquals(500, refundService.truncateReason("x".repeat(700)).length());
    }

    private void mockNewRefund() {
        mockNewRefundCreation();
        when(refundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void mockNewRefundCreation() {
        when(refundRepository.findByEngagementIdForUpdate(10L))
                .thenReturn(Optional.empty());
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(payment));
        when(refundRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            refund.setId(30L);
            return refund;
        });
    }

    private Refund pendingRefund() {
        Refund refund = Refund.builder()
                .engagement(engagement)
                .payment(payment)
                .amount(payment.getAmount())
                .reason("cancelled")
                .status(Refund.RefundStatus.PENDING)
                .build();
        refund.setId(30L);
        return refund;
    }

    private Refund failedRefund(String providerRefundId, String providerStatus) {
        Refund refund = pendingRefund();
        refund.setStatus(Refund.RefundStatus.FAILED);
        refund.setProviderRefundId(providerRefundId);
        refund.setProviderStatus(providerStatus);
        return refund;
    }

    private void mockExistingRefund(Refund refund) {
        when(refundRepository.findByEngagementIdForUpdate(10L)).thenReturn(Optional.of(refund));
        when(refundRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RefundResult providerResult(String providerRef, String status) {
        return new RefundResult(
                providerRef,
                status,
                4000L,
                "usd",
                "pi_1",
                null,
                null);
    }

    private PaymentEventHandler.RefundChanged refundEvent(
            String eventType,
            String providerStatus,
            Long amount) {
        return new PaymentEventHandler.RefundChanged(
                "evt_1",
                eventType,
                "re_1",
                providerStatus,
                "pi_1",
                null,
                amount,
                "USD",
                "provider failure",
                null,
                null,
                null);
    }
}
