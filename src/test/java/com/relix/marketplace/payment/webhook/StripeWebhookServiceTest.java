package com.relix.marketplace.payment.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.payment.config.StripeProperties;
import com.relix.marketplace.payment.webhook.entity.WebhookEvent;
import com.relix.marketplace.payment.webhook.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StripeWebhookServiceTest {

    private static final String WEBHOOK_SECRET = "whsec_unit_test_only";

    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private PaymentEventHandler paymentEventHandler;

    private StripeWebhookService stripeWebhookService;

    @BeforeEach
    void setUp() {
        StripeProperties properties = new StripeProperties();
        properties.setWebhookSecret(WEBHOOK_SECRET);
        ObjectMapper objectMapper = new ObjectMapper();
        stripeWebhookService = new StripeWebhookService(
                properties,
                webhookEventRepository,
                paymentEventHandler,
                new WebhookPayloadSanitizer(objectMapper));
    }

    @Test
    void validSucceededEventDispatchesLatestChargeAndStoresOnlySanitizedPayload() {
        String payload = succeededPayload();
        when(webhookEventRepository.insertIfAbsent(
                eq("STRIPE"), eq("evt_success"), eq("payment_intent.succeeded"), anyString()))
                .thenReturn(1);
        when(webhookEventRepository.markProcessed(eq("STRIPE"), eq("evt_success"), any(Instant.class)))
                .thenReturn(1);

        WebhookProcessingResult result = stripeWebhookService.process(payload, signature(payload));

        assertThat(result.status()).isEqualTo(WebhookProcessingResult.Status.PROCESSED);
        ArgumentCaptor<PaymentEventHandler.PaymentIntentSucceeded> callback =
                ArgumentCaptor.forClass(PaymentEventHandler.PaymentIntentSucceeded.class);
        verify(paymentEventHandler).onPaymentIntentSucceeded(callback.capture());
        assertThat(callback.getValue().paymentIntentId()).isEqualTo("pi_success");
        assertThat(callback.getValue().latestChargeId()).isEqualTo("ch_success");
        assertThat(callback.getValue().providerStatus()).isEqualTo("succeeded");
        assertThat(callback.getValue().amountReceived()).isEqualTo(4000L);
        assertThat(callback.getValue().currency()).isEqualTo("USD");

        ArgumentCaptor<String> storedPayload = ArgumentCaptor.forClass(String.class);
        verify(webhookEventRepository).insertIfAbsent(
                eq("STRIPE"),
                eq("evt_success"),
                eq("payment_intent.succeeded"),
                storedPayload.capture());
        assertThat(storedPayload.getValue())
                .contains("pi_success", "ch_success")
                .doesNotContain("client_secret", "clientSecret", "pi_success_secret_valuable");
    }

    @Test
    void validFailedEventDispatchesMachineCodeAndFailureMessage() {
        String payload = failedPayload();
        acceptNewEvent("evt_failed", "payment_intent.payment_failed");

        stripeWebhookService.process(payload, signature(payload));

        ArgumentCaptor<PaymentEventHandler.PaymentIntentFailed> callback =
                ArgumentCaptor.forClass(PaymentEventHandler.PaymentIntentFailed.class);
        verify(paymentEventHandler).onPaymentIntentFailed(callback.capture());
        assertThat(callback.getValue().paymentIntentId()).isEqualTo("pi_failed");
        assertThat(callback.getValue().providerStatus()).isEqualTo("requires_payment_method");
        assertThat(callback.getValue().failureCode()).isEqualTo("card_declined");
        assertThat(callback.getValue().failureMessage()).isEqualTo("Your card was declined.");
    }

    @Test
    void validChargeRefundedEventDispatchesProviderReferences() {
        String payload = refundedPayload();
        acceptNewEvent("evt_refunded", "charge.refunded");

        stripeWebhookService.process(payload, signature(payload));

        ArgumentCaptor<PaymentEventHandler.ChargeRefunded> callback =
                ArgumentCaptor.forClass(PaymentEventHandler.ChargeRefunded.class);
        verify(paymentEventHandler).onChargeRefunded(callback.capture());
        assertThat(callback.getValue().chargeId()).isEqualTo("ch_refunded");
        assertThat(callback.getValue().paymentIntentId()).isEqualTo("pi_refunded");
        assertThat(callback.getValue().amountRefunded()).isEqualTo(2500L);
        assertThat(callback.getValue().currency()).isEqualTo("USD");
    }

    @Test
    void validRefundUpdatedEventDispatchesStrictSnapshotAndLocalMetadata() {
        String payload = refundUpdatedPayload();
        acceptNewEvent("evt_refund_updated", "refund.updated");

        stripeWebhookService.process(payload, signature(payload));

        ArgumentCaptor<PaymentEventHandler.RefundChanged> callback =
                ArgumentCaptor.forClass(PaymentEventHandler.RefundChanged.class);
        verify(paymentEventHandler).onRefundChanged(callback.capture());
        assertThat(callback.getValue().eventType()).isEqualTo("refund.updated");
        assertThat(callback.getValue().refundId()).isEqualTo("re_updated");
        assertThat(callback.getValue().providerStatus()).isEqualTo("succeeded");
        assertThat(callback.getValue().paymentIntentId()).isEqualTo("pi_refunded");
        assertThat(callback.getValue().chargeId()).isEqualTo("ch_refunded");
        assertThat(callback.getValue().amount()).isEqualTo(4000L);
        assertThat(callback.getValue().currency()).isEqualTo("USD");
        assertThat(callback.getValue().localRefundId()).isEqualTo("30");
        assertThat(callback.getValue().localEngagementId()).isEqualTo("10");
        assertThat(callback.getValue().replacesRefundId()).isEqualTo("re_failed");
    }

    @Test
    void validRefundFailedEventDispatchesFailureReason() {
        String payload = refundFailedPayload();
        acceptNewEvent("evt_refund_failed", "refund.failed");

        stripeWebhookService.process(payload, signature(payload));

        ArgumentCaptor<PaymentEventHandler.RefundChanged> callback =
                ArgumentCaptor.forClass(PaymentEventHandler.RefundChanged.class);
        verify(paymentEventHandler).onRefundChanged(callback.capture());
        assertThat(callback.getValue().providerStatus()).isEqualTo("failed");
        assertThat(callback.getValue().failureReason()).isEqualTo("insufficient_funds");
    }

    @Test
    void forgedSignatureIsRejectedBeforeAnyDatabaseWrite() {
        String payload = succeededPayload();

        assertThatThrownBy(() -> stripeWebhookService.process(
                payload,
                "t=" + Instant.now().getEpochSecond() + ",v1=forged"))
                .isInstanceOf(InvalidStripeWebhookException.class);

        verifyNoInteractions(webhookEventRepository, paymentEventHandler);
    }

    @Test
    void alreadyProcessedDuplicateReturnsSuccessWithoutRepeatingCallback() {
        String payload = succeededPayload();
        when(webhookEventRepository.insertIfAbsent(
                eq("STRIPE"), eq("evt_success"), eq("payment_intent.succeeded"), anyString()))
                .thenReturn(0);
        when(webhookEventRepository.findByProviderAndEventId("STRIPE", "evt_success"))
                .thenReturn(Optional.of(WebhookEvent.builder()
                        .provider("STRIPE")
                        .eventId("evt_success")
                        .eventType("payment_intent.succeeded")
                        .processedAt(Instant.now())
                        .build()));

        WebhookProcessingResult result = stripeWebhookService.process(payload, signature(payload));

        assertThat(result.status()).isEqualTo(WebhookProcessingResult.Status.DUPLICATE);
        verifyNoInteractions(paymentEventHandler);
        verify(webhookEventRepository, never()).markProcessed(anyString(), anyString(), any());
    }

    @Test
    void conflictWithUnprocessedRowReturnsRetryableFailure() {
        String payload = succeededPayload();
        when(webhookEventRepository.insertIfAbsent(
                eq("STRIPE"), eq("evt_success"), eq("payment_intent.succeeded"), anyString()))
                .thenReturn(0);
        when(webhookEventRepository.findByProviderAndEventId("STRIPE", "evt_success"))
                .thenReturn(Optional.of(WebhookEvent.builder()
                        .provider("STRIPE")
                        .eventId("evt_success")
                        .eventType("payment_intent.succeeded")
                        .processedAt(null)
                        .build()));

        assertThatThrownBy(() -> stripeWebhookService.process(payload, signature(payload)))
                .isInstanceOf(RetryableWebhookException.class);

        verifyNoInteractions(paymentEventHandler);
        verify(webhookEventRepository, never()).markProcessed(anyString(), anyString(), any());
    }

    @Test
    void outOfOrderEventBubblesCallbackFailureAndNeverMarksEventProcessed() throws Exception {
        String payload = succeededPayload();
        when(webhookEventRepository.insertIfAbsent(
                eq("STRIPE"), eq("evt_success"), eq("payment_intent.succeeded"), anyString()))
                .thenReturn(1);
        doThrow(new IllegalStateException("Local payment does not exist yet"))
                .when(paymentEventHandler)
                .onPaymentIntentSucceeded(any(PaymentEventHandler.PaymentIntentSucceeded.class));

        assertThatThrownBy(() -> stripeWebhookService.process(payload, signature(payload)))
                .isInstanceOf(RetryableWebhookException.class)
                .hasMessage("Webhook business callback failed")
                .hasCauseInstanceOf(IllegalStateException.class);

        verify(webhookEventRepository, never()).markProcessed(anyString(), anyString(), any());
        assertThat(StripeWebhookService.class
                .getMethod("process", String.class, String.class)
                .isAnnotationPresent(Transactional.class)).isTrue();
    }

    private void acceptNewEvent(String eventId, String eventType) {
        when(webhookEventRepository.insertIfAbsent(
                eq("STRIPE"), eq(eventId), eq(eventType), anyString()))
                .thenReturn(1);
        when(webhookEventRepository.markProcessed(eq("STRIPE"), eq(eventId), any(Instant.class)))
                .thenReturn(1);
    }

    private String signature(String payload) {
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signedPayload = timestamp + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String digest = HexFormat.of().formatHex(
                    mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8)));
            return "t=" + timestamp + ",v1=" + digest;
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String succeededPayload() {
        return """
                {"id":"evt_success","object":"event","type":"payment_intent.succeeded","data":{"object":{"id":"pi_success","object":"payment_intent","latest_charge":"ch_success","status":"succeeded","amount_received":4000,"currency":"usd","client_secret":"pi_success_secret_valuable","metadata":{"clientSecret":"nested-secret"}}}}
                """.trim();
    }

    private String failedPayload() {
        return """
                {"id":"evt_failed","object":"event","type":"payment_intent.payment_failed","data":{"object":{"id":"pi_failed","object":"payment_intent","status":"requires_payment_method","last_payment_error":{"code":"card_declined","message":"Your card was declined."}}}}
                """.trim();
    }

    private String refundedPayload() {
        return """
                {"id":"evt_refunded","object":"event","type":"charge.refunded","data":{"object":{"id":"ch_refunded","object":"charge","payment_intent":"pi_refunded","amount_refunded":2500,"currency":"usd"}}}
                """.trim();
    }

    private String refundUpdatedPayload() {
        return """
                {"id":"evt_refund_updated","object":"event","type":"refund.updated","data":{"object":{"id":"re_updated","object":"refund","status":"succeeded","payment_intent":"pi_refunded","charge":"ch_refunded","amount":4000,"currency":"usd","metadata":{"marketplaceRefundId":"30","marketplaceEngagementId":"10","marketplaceReplacesRefundId":"re_failed"}}}}
                """.trim();
    }

    private String refundFailedPayload() {
        return """
                {"id":"evt_refund_failed","object":"event","type":"refund.failed","data":{"object":{"id":"re_failed","object":"refund","status":"failed","payment_intent":"pi_refunded","charge":"ch_refunded","amount":4000,"currency":"usd","failure_reason":"insufficient_funds","metadata":{"marketplaceRefundId":"30","marketplaceEngagementId":"10"}}}}
                """.trim();
    }
}
