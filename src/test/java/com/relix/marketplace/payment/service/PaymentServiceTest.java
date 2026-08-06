package com.relix.marketplace.payment.service;

import com.relix.marketplace.audit.service.AuditService;
import com.relix.marketplace.engagement.entity.Engagement;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.payment.dto.PaymentRequest;
import com.relix.marketplace.payment.dto.PaymentResponse;
import com.relix.marketplace.payment.entity.Payment;
import com.relix.marketplace.payment.gateway.PaymentGateway;
import com.relix.marketplace.payment.gateway.PaymentIntentRequest;
import com.relix.marketplace.payment.gateway.PaymentIntentResult;
import com.relix.marketplace.payment.repository.PaymentRepository;
import com.relix.marketplace.payment.repository.SupersededPaymentIntentRepository;
import com.relix.marketplace.payment.webhook.PaymentEventHandler;
import com.relix.marketplace.payment.webhook.RetryableWebhookException;
import com.relix.marketplace.ticket.entity.Ticket;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.worker.entity.WorkerProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private SupersededPaymentIntentRepository supersededPaymentIntentRepository;
    @Mock private EngagementRepository engagementRepository;
    @Mock private PaymentGateway paymentGateway;
    @Mock private AuditService auditService;
    @InjectMocks private PaymentService paymentService;

    private Engagement engagement;

    @BeforeEach
    void setUp() {
        User client = user(1L, "Client");
        User workerUser = user(2L, "Worker");
        WorkerProfile worker = WorkerProfile.builder().user(workerUser).displayName("Worker").build();
        worker.setId(20L);
        Ticket ticket = Ticket.builder().currency("USD").build();
        ticket.setId(30L);
        engagement = Engagement.builder()
                .client(client)
                .worker(worker)
                .ticket(ticket)
                .amount(new BigDecimal("40.00"))
                .status(EngagementStatus.ACCEPTED)
                .build();
        engagement.setId(10L);
    }

    @Test
    void mockGatewayFundsImmediately() {
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.empty());
        when(paymentGateway.webhookDriven()).thenReturn(false);
        when(paymentGateway.createIntent(any())).thenReturn(
                new PaymentIntentResult("pi_mock_1", "secret", "succeeded"));
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(50L);
            return payment;
        });

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        assertEquals("SUCCEEDED", response.getStatus());
        assertNull(response.getClientSecret());
        assertFalse(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
        assertEquals(EngagementStatus.FUNDED, engagement.getStatus());
        assertNotNull(engagement.getFundedAt());
        ArgumentCaptor<PaymentIntentRequest> captor = ArgumentCaptor.forClass(PaymentIntentRequest.class);
        verify(paymentGateway).createIntent(captor.capture());
        assertEquals("engagement-pay:10:req-1", captor.getValue().idempotencyKey());
    }

    @Test
    void webhookGatewayStaysPendingAndReturnsTransientClientSecret() {
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.empty());
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.createIntent(any())).thenReturn(
                new PaymentIntentResult("pi_stripe_1", "secret-1", "requires_payment_method"));
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(51L);
            return payment;
        });

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        assertEquals("PENDING", response.getStatus());
        assertEquals("secret-1", response.getClientSecret());
        assertFalse(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        assertThrows(NoSuchFieldException.class,
                () -> Payment.class.getDeclaredField("clientSecret"));
    }

    @Test
    void matchingPendingRetryRetrievesSecretWithoutAdvancingLifecycle() {
        Payment existing = payment(Payment.PaymentStatus.PENDING, "req-1");
        existing.setPaymentIntentId("pi_stripe_1");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(existing));
        when(paymentGateway.retrieveIntent("pi_stripe_1")).thenReturn(
                new PaymentIntentResult("pi_stripe_1", "retrieved-secret", "requires_action"));

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        assertEquals("retrieved-secret", response.getClientSecret());
        assertEquals("requires_action", response.getProviderStatus());
        assertFalse(response.isAlreadyPaid());
        assertTrue(response.isRequestIdMatched());
        assertEquals(Payment.PaymentStatus.PENDING, existing.getStatus());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verify(paymentGateway).retrieveIntent("pi_stripe_1");
        verify(paymentGateway, never()).createIntent(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"processing", "succeeded"})
    void pendingReplayDoesNotExposeSecretForNonConfirmableProviderStatus(String providerStatus) {
        Payment existing = payment(Payment.PaymentStatus.PENDING, "req-1");
        existing.setPaymentIntentId("pi_stripe_1");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(existing));
        when(paymentGateway.retrieveIntent("pi_stripe_1")).thenReturn(
                new PaymentIntentResult("pi_stripe_1", "must-not-leak", providerStatus));

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        assertNull(response.getClientSecret());
        assertEquals(providerStatus, response.getProviderStatus());
        assertFalse(response.isAlreadyPaid());
        assertTrue(response.isRequestIdMatched());
        assertEquals(Payment.PaymentStatus.PENDING, existing.getStatus());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verify(paymentGateway, never()).createIntent(any());
    }

    @Test
    void canceledPendingIntentBecomesFailedThenAllowsAReplacementRequest() {
        Payment existing = payment(Payment.PaymentStatus.PENDING, "req-1");
        existing.setPaymentIntentId("pi_stripe_canceled");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(existing));
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.retrieveIntent("pi_stripe_canceled")).thenReturn(
                new PaymentIntentResult("pi_stripe_canceled", "must-not-leak", "canceled"));
        when(paymentGateway.createIntent(any())).thenReturn(
                new PaymentIntentResult(
                        "pi_stripe_replacement",
                        "replacement-secret",
                        "requires_payment_method"));
        when(supersededPaymentIntentRepository.insertIfAbsent(50L, "pi_stripe_canceled"))
                .thenReturn(1);
        when(paymentRepository.save(existing)).thenReturn(existing);

        PaymentResponse canceled = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        assertEquals(Payment.PaymentStatus.FAILED, existing.getStatus());
        assertEquals("FAILED", canceled.getStatus());
        assertEquals("canceled", canceled.getProviderStatus());
        assertTrue(canceled.getFailureMessage().contains("canceled"));
        assertNull(canceled.getClientSecret());
        assertFalse(canceled.isAlreadyPaid());
        assertTrue(canceled.isRequestIdMatched());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());

        PaymentResponse replacement = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-2").build());

        ArgumentCaptor<PaymentIntentRequest> requestCaptor =
                ArgumentCaptor.forClass(PaymentIntentRequest.class);
        verify(paymentGateway).createIntent(requestCaptor.capture());
        assertEquals(
                "engagement-pay:10:req-2:after:pi_stripe_canceled",
                requestCaptor.getValue().idempotencyKey());
        assertEquals("pi_stripe_replacement", existing.getPaymentIntentId());
        assertEquals(Payment.PaymentStatus.PENDING, existing.getStatus());
        assertEquals("replacement-secret", replacement.getClientSecret());
        assertFalse(replacement.isAlreadyPaid());
        assertFalse(replacement.isRequestIdMatched());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verify(supersededPaymentIntentRepository)
                .insertIfAbsent(50L, "pi_stripe_canceled");
    }

    @Test
    void terminalPaymentNeverReturnsOrRetrievesClientSecret() {
        engagement.setStatus(EngagementStatus.FUNDED);
        Payment existing = payment(Payment.PaymentStatus.SUCCEEDED, "req-1");
        existing.setPaymentIntentId("pi_stripe_1");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        assertEquals("SUCCEEDED", response.getStatus());
        assertNull(response.getClientSecret());
        assertTrue(response.isAlreadyPaid());
        assertTrue(response.isRequestIdMatched());
        verify(paymentGateway, never()).retrieveIntent(any());
    }

    @Test
    void refundedPaymentIsAlreadyPaidWithoutRetrievingASecret() {
        engagement.setStatus(EngagementStatus.REFUNDED);
        Payment existing = payment(Payment.PaymentStatus.REFUNDED, "req-1");
        existing.setPaymentIntentId("pi_stripe_1");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-2").build());

        assertTrue(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
        assertNull(response.getClientSecret());
        verify(paymentGateway, never()).retrieveIntent(any());
    }

    @Test
    void differentRequestConflictsWhilePaymentPending() {
        when(paymentRepository.findByEngagement_Id(10L))
                .thenReturn(Optional.of(payment(Payment.PaymentStatus.PENDING, "req-1")));

        assertThrows(RuntimeException.class, () -> paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-2").build()));
        verify(paymentGateway, never()).createIntent(any());
    }

    @Test
    void failedPaymentCanRetryUsingSameRow() {
        Payment failed = payment(Payment.PaymentStatus.FAILED, "req-1");
        failed.setFailureMessage("declined");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(failed));
        when(paymentGateway.webhookDriven()).thenReturn(false);
        when(paymentGateway.createIntent(any())).thenReturn(
                new PaymentIntentResult("pi_mock_retry", "retry-secret", "succeeded"));
        when(paymentRepository.save(failed)).thenReturn(failed);

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-2").build());

        assertEquals(50L, response.getPaymentId());
        assertNull(response.getClientSecret());
        assertFalse(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
        assertEquals("req-2", failed.getRequestId());
        assertEquals(Payment.PaymentStatus.SUCCEEDED, failed.getStatus());
    }

    @Test
    void failedStripePaymentReusesIntentAndLateSuccessStillFindsTheSamePayment() {
        Payment failed = payment(Payment.PaymentStatus.FAILED, "req-1");
        failed.setPaymentIntentId("pi_stripe_original");
        failed.setProviderStatus("requires_payment_method");
        failed.setFailureMessage("card_declined");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(failed));
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.retrieveIntent("pi_stripe_original")).thenReturn(
                new PaymentIntentResult(
                        "pi_stripe_original",
                        "pi_stripe_original_secret_retry",
                        "requires_payment_method"));
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_stripe_original"))
                .thenReturn(Optional.of(failed));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        PaymentResponse retry = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-2").build());

        assertEquals("pi_stripe_original", failed.getPaymentIntentId());
        assertEquals("req-2", failed.getRequestId());
        assertEquals(Payment.PaymentStatus.PENDING, failed.getStatus());
        assertEquals("pi_stripe_original_secret_retry", retry.getClientSecret());
        assertFalse(retry.isAlreadyPaid());
        assertFalse(retry.isRequestIdMatched());
        assertNull(failed.getFailureMessage());
        verify(paymentGateway, never()).createIntent(any());

        paymentService.handlePaymentSucceeded(new PaymentEventHandler.PaymentIntentSucceeded(
                "evt_late_success",
                "pi_stripe_original",
                "ch_late_success",
                "succeeded",
                4000L,
                "usd"));

        assertEquals(Payment.PaymentStatus.SUCCEEDED, failed.getStatus());
        assertEquals("ch_late_success", failed.getChargeId());
        assertEquals(EngagementStatus.FUNDED, engagement.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"processing", "succeeded"})
    void failedStripePaymentRestoresPendingWithoutSecretForProviderProgress(String providerStatus) {
        Payment failed = payment(Payment.PaymentStatus.FAILED, "req-1");
        failed.setPaymentIntentId("pi_stripe_original");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(failed));
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.retrieveIntent("pi_stripe_original")).thenReturn(
                new PaymentIntentResult("pi_stripe_original", "must-not-leak", providerStatus));

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-2").build());

        assertEquals(Payment.PaymentStatus.PENDING, failed.getStatus());
        assertEquals("pi_stripe_original", failed.getPaymentIntentId());
        assertEquals(providerStatus, failed.getProviderStatus());
        assertNull(response.getClientSecret());
        assertFalse(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verify(paymentGateway, never()).createIntent(any());
    }

    @Test
    void canceledStripeIntentAllowsAReplacementWithADistinctIdempotencyKey() {
        Payment failed = payment(Payment.PaymentStatus.FAILED, "req-1");
        failed.setPaymentIntentId("pi_stripe_canceled");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(failed));
        when(paymentGateway.webhookDriven()).thenReturn(true);
        when(paymentGateway.retrieveIntent("pi_stripe_canceled")).thenReturn(
                new PaymentIntentResult("pi_stripe_canceled", "old-secret", "canceled"));
        when(paymentGateway.createIntent(any())).thenReturn(
                new PaymentIntentResult("pi_stripe_replacement", "replacement-secret", "requires_payment_method"));
        when(supersededPaymentIntentRepository.insertIfAbsent(50L, "pi_stripe_canceled"))
                .thenReturn(1);
        when(paymentRepository.save(failed)).thenReturn(failed);

        PaymentResponse response = paymentService.pay(
                engagement,
                PaymentRequest.builder().requestId("req-1").build());

        ArgumentCaptor<PaymentIntentRequest> requestCaptor =
                ArgumentCaptor.forClass(PaymentIntentRequest.class);
        verify(paymentGateway).createIntent(requestCaptor.capture());
        assertEquals(
                "engagement-pay:10:req-1:after:pi_stripe_canceled",
                requestCaptor.getValue().idempotencyKey());
        assertEquals("pi_stripe_replacement", failed.getPaymentIntentId());
        assertEquals(Payment.PaymentStatus.PENDING, failed.getStatus());
        assertEquals("replacement-secret", response.getClientSecret());
        assertFalse(response.isAlreadyPaid());
        assertTrue(response.isRequestIdMatched());
    }

    @Test
    void lateFailureForSupersededIntentIsAcknowledgedWithoutMutatingCurrentPayment() {
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_old"))
                .thenReturn(Optional.empty());
        when(supersededPaymentIntentRepository.existsByPaymentIntentId("pi_old"))
                .thenReturn(true);

        paymentService.handlePaymentFailed(new PaymentEventHandler.PaymentIntentFailed(
                "evt_old_failed", "pi_old", "requires_payment_method",
                "card_declined", "Old attempt declined"));

        verifyNoInteractions(engagementRepository, auditService);
    }

    @Test
    void lateSuccessForSupersededIntentFailsClosedWithoutFundingEngagement() {
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_old"))
                .thenReturn(Optional.empty());
        when(supersededPaymentIntentRepository.existsByPaymentIntentId("pi_old"))
                .thenReturn(true);

        RetryableWebhookException exception = assertThrows(
                RetryableWebhookException.class,
                () -> paymentService.handlePaymentSucceeded(
                        new PaymentEventHandler.PaymentIntentSucceeded(
                                "evt_old_success", "pi_old", "ch_old",
                                "succeeded", 4000L, "usd")));

        assertTrue(exception.getMessage().contains("superseded"));
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verifyNoInteractions(engagementRepository, auditService);
    }

    @Test
    void trulyUnknownFailedIntentRemainsRetryableForEarlyDelivery() {
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_new_uncommitted"))
                .thenReturn(Optional.empty());
        when(supersededPaymentIntentRepository.existsByPaymentIntentId("pi_new_uncommitted"))
                .thenReturn(false);

        assertThrows(
                RetryableWebhookException.class,
                () -> paymentService.handlePaymentFailed(
                        new PaymentEventHandler.PaymentIntentFailed(
                                "evt_early", "pi_new_uncommitted", "requires_payment_method",
                                "card_declined", "Declined")));
    }

    @Test
    void paymentLookupHasNoRequestReplayContext() {
        Payment existing = payment(Payment.PaymentStatus.SUCCEEDED, "req-1");
        when(paymentRepository.findByEngagement_Id(10L)).thenReturn(Optional.of(existing));

        PaymentResponse response = paymentService.getPaymentByEngagementId(10L);

        assertFalse(response.isAlreadyPaid());
        assertFalse(response.isRequestIdMatched());
        assertNull(response.getClientSecret());
    }

    @Test
    void successWebhookFundsAcceptedEngagement() {
        Payment pending = payment(Payment.PaymentStatus.PENDING, "req-1");
        pending.setPaymentIntentId("pi_1");
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_1"))
                .thenReturn(Optional.of(pending));
        when(engagementRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(engagement));

        paymentService.handlePaymentSucceeded(new PaymentEventHandler.PaymentIntentSucceeded(
                "evt_1", "pi_1", "ch_1", "succeeded", 4000L, "usd"));

        assertEquals(Payment.PaymentStatus.SUCCEEDED, pending.getStatus());
        assertEquals("ch_1", pending.getChargeId());
        assertEquals(EngagementStatus.FUNDED, engagement.getStatus());
    }

    @Test
    void successWebhookWithWrongAmountFailsClosed() {
        Payment pending = payment(Payment.PaymentStatus.PENDING, "req-1");
        pending.setPaymentIntentId("pi_1");
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_1"))
                .thenReturn(Optional.of(pending));

        RetryableWebhookException exception = assertThrows(
                RetryableWebhookException.class,
                () -> paymentService.handlePaymentSucceeded(
                        new PaymentEventHandler.PaymentIntentSucceeded(
                                "evt_wrong_amount", "pi_1", "ch_1", "succeeded", 3999L, "usd")));

        assertTrue(exception.getMessage().contains("amount_received"));
        assertEquals(Payment.PaymentStatus.PENDING, pending.getStatus());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verifyNoInteractions(engagementRepository, auditService);
    }

    @Test
    void successWebhookWithMissingAmountFailsClosed() {
        Payment pending = payment(Payment.PaymentStatus.PENDING, "req-1");
        pending.setPaymentIntentId("pi_1");
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_1"))
                .thenReturn(Optional.of(pending));

        assertThrows(
                RetryableWebhookException.class,
                () -> paymentService.handlePaymentSucceeded(
                        new PaymentEventHandler.PaymentIntentSucceeded(
                                "evt_missing_amount", "pi_1", "ch_1", "succeeded", null, "usd")));

        assertEquals(Payment.PaymentStatus.PENDING, pending.getStatus());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verifyNoInteractions(engagementRepository, auditService);
    }

    @Test
    void successWebhookWithWrongCurrencyFailsClosed() {
        Payment pending = payment(Payment.PaymentStatus.PENDING, "req-1");
        pending.setPaymentIntentId("pi_1");
        when(paymentRepository.findByPaymentIntentIdForUpdate("pi_1"))
                .thenReturn(Optional.of(pending));

        RetryableWebhookException exception = assertThrows(
                RetryableWebhookException.class,
                () -> paymentService.handlePaymentSucceeded(
                        new PaymentEventHandler.PaymentIntentSucceeded(
                                "evt_wrong_currency", "pi_1", "ch_1", "succeeded", 4000L, "eur")));

        assertTrue(exception.getMessage().contains("currency"));
        assertEquals(Payment.PaymentStatus.PENDING, pending.getStatus());
        assertEquals(EngagementStatus.ACCEPTED, engagement.getStatus());
        verifyNoInteractions(engagementRepository, auditService);
    }

    private Payment payment(Payment.PaymentStatus status, String requestId) {
        Payment payment = Payment.builder()
                .engagement(engagement)
                .requestId(requestId)
                .amount(engagement.getAmount())
                .currency("USD")
                .status(status)
                .build();
        payment.setId(50L);
        return payment;
    }

    private User user(Long id, String name) {
        User user = User.builder()
                .email(name.toLowerCase() + "@test.com")
                .passwordHash("hash")
                .name(name)
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .build();
        user.setId(id);
        return user;
    }
}
