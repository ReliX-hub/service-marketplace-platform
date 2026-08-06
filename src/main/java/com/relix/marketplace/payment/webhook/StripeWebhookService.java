package com.relix.marketplace.payment.webhook;

import com.fasterxml.jackson.databind.JsonNode;
import com.relix.marketplace.payment.config.StripeProperties;
import com.relix.marketplace.payment.webhook.entity.WebhookEvent;
import com.relix.marketplace.payment.webhook.repository.WebhookEventRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {

    static final String PROVIDER = "STRIPE";

    private final StripeProperties stripeProperties;
    private final WebhookEventRepository webhookEventRepository;
    private final PaymentEventHandler paymentEventHandler;
    private final WebhookPayloadSanitizer payloadSanitizer;

    /**
     * The idempotency insert, business callback, and processed timestamp intentionally share one
     * transaction. A missing local payment/refund must make the callback throw, rolling back the
     * event insert so a later Stripe retry can process it normally.
     */
    @Transactional
    public WebhookProcessingResult process(String rawPayload, String stripeSignature) {
        Event stripeEvent = verify(rawPayload, stripeSignature);
        JsonNode payload = payloadSanitizer.parse(rawPayload);
        String eventId = requiredEventValue(stripeEvent.getId());
        String eventType = requiredEventValue(stripeEvent.getType());
        String sanitizedPayload = payloadSanitizer.sanitize(payload);

        int inserted = webhookEventRepository.insertIfAbsent(
                PROVIDER,
                eventId,
                eventType,
                sanitizedPayload);
        if (inserted == 0) {
            return duplicateResult(eventId, eventType);
        }

        try {
            dispatch(eventId, eventType, payload);
        } catch (RetryableWebhookException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Provider callbacks are not an HTTP resource lookup boundary. Any local-state failure
            // must remain a 5xx so Stripe retries instead of treating it as a client-side 4xx.
            throw new RetryableWebhookException("Webhook business callback failed", exception);
        }

        int marked = webhookEventRepository.markProcessed(PROVIDER, eventId, Instant.now());
        if (marked != 1) {
            throw new RetryableWebhookException("Webhook event could not be marked as processed");
        }
        return new WebhookProcessingResult(
                eventId,
                eventType,
                WebhookProcessingResult.Status.PROCESSED);
    }

    private Event verify(String rawPayload, String stripeSignature) {
        String webhookSecret = stripeProperties.getWebhookSecret();
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new RetryableWebhookException("Stripe webhook secret is not configured");
        }
        if (rawPayload == null || rawPayload.isBlank()
                || stripeSignature == null || stripeSignature.isBlank()) {
            throw new InvalidStripeWebhookException();
        }
        try {
            Event event = Webhook.constructEvent(rawPayload, stripeSignature, webhookSecret);
            if (event == null) {
                throw new InvalidStripeWebhookException();
            }
            return event;
        } catch (SignatureVerificationException | RuntimeException exception) {
            if (exception instanceof InvalidStripeWebhookException invalid) {
                throw invalid;
            }
            throw new InvalidStripeWebhookException();
        }
    }

    private WebhookProcessingResult duplicateResult(String eventId, String eventType) {
        WebhookEvent existing = webhookEventRepository
                .findByProviderAndEventId(PROVIDER, eventId)
                .orElseThrow(() -> new RetryableWebhookException(
                        "Webhook idempotency conflict could not be reconciled"));
        if (existing.getProcessedAt() == null) {
            throw new RetryableWebhookException(
                    "Webhook event exists but processing has not completed");
        }
        return new WebhookProcessingResult(
                eventId,
                eventType,
                WebhookProcessingResult.Status.DUPLICATE);
    }

    private void dispatch(String eventId, String eventType, JsonNode payload) {
        JsonNode object = payload.path("data").path("object");
        switch (eventType) {
            case "payment_intent.succeeded" -> paymentEventHandler.onPaymentIntentSucceeded(
                    new PaymentEventHandler.PaymentIntentSucceeded(
                            eventId,
                            requiredObjectId(object, "payment intent"),
                            referencedId(object.path("latest_charge")),
                            textOrNull(object.path("status")),
                            longOrNull(object.path("amount_received")),
                            currency(object.path("currency"))));
            case "payment_intent.payment_failed" -> paymentEventHandler.onPaymentIntentFailed(
                    new PaymentEventHandler.PaymentIntentFailed(
                            eventId,
                            requiredObjectId(object, "payment intent"),
                            textOrNull(object.path("status")),
                            textOrNull(object.path("last_payment_error").path("code")),
                            truncate(textOrNull(object.path("last_payment_error").path("message")), 500)));
            case "refund.created", "refund.updated", "refund.failed" ->
                    paymentEventHandler.onRefundChanged(
                            new PaymentEventHandler.RefundChanged(
                                    eventId,
                                    eventType,
                                    requiredObjectId(object, "refund"),
                                    textOrNull(object.path("status")),
                                    referencedId(object.path("payment_intent")),
                                    referencedId(object.path("charge")),
                                    longOrNull(object.path("amount")),
                                    currency(object.path("currency")),
                                    truncate(textOrNull(object.path("failure_reason")), 500),
                                    textOrNull(object.path("metadata").path("marketplaceRefundId")),
                                    textOrNull(object.path("metadata").path("marketplaceEngagementId")),
                                    textOrNull(object.path("metadata").path("marketplaceReplacesRefundId"))));
            case "charge.refunded" -> paymentEventHandler.onChargeRefunded(
                    new PaymentEventHandler.ChargeRefunded(
                            eventId,
                            requiredObjectId(object, "charge"),
                            referencedId(object.path("payment_intent")),
                            longOrNull(object.path("amount_refunded")),
                            currency(object.path("currency"))));
            default -> {
                // Stripe sends many event types. A signed, irrelevant event is acknowledged once.
            }
        }
    }

    private String requiredObjectId(JsonNode object, String objectType) {
        String id = textOrNull(object.path("id"));
        if (id == null) {
            throw new RetryableWebhookException("Signed webhook is missing its " + objectType + " id");
        }
        return id;
    }

    private String requiredEventValue(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidStripeWebhookException();
        }
        return value;
    }

    private String referencedId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return textOrNull(node);
        }
        return textOrNull(node.path("id"));
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? null : value;
    }

    private Long longOrNull(JsonNode node) {
        if (node == null || !node.canConvertToLong()) {
            return null;
        }
        return node.longValue();
    }

    private String currency(JsonNode node) {
        String value = textOrNull(node);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength);
    }
}
