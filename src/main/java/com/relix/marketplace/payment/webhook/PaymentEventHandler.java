package com.relix.marketplace.payment.webhook;

/**
 * Transactional callback boundary between Stripe delivery concerns and payment-domain state changes.
 * Implementations should throw when the referenced local record does not exist so Stripe receives a
 * 5xx response and retries the event later.
 */
public interface PaymentEventHandler {

    void onPaymentIntentSucceeded(PaymentIntentSucceeded event);

    void onPaymentIntentFailed(PaymentIntentFailed event);

    void onRefundChanged(RefundChanged event);

    void onChargeRefunded(ChargeRefunded event);

    record PaymentIntentSucceeded(
            String eventId,
            String paymentIntentId,
            String latestChargeId,
            String providerStatus,
            Long amountReceived,
            String currency) {
    }

    record PaymentIntentFailed(
            String eventId,
            String paymentIntentId,
            String providerStatus,
            String failureCode,
            String failureMessage) {
    }

    record RefundChanged(
            String eventId,
            String eventType,
            String refundId,
            String providerStatus,
            String paymentIntentId,
            String chargeId,
            Long amount,
            String currency,
            String failureReason,
            String localRefundId,
            String localEngagementId,
            String replacesRefundId) {
    }

    record ChargeRefunded(
            String eventId,
            String chargeId,
            String paymentIntentId,
            Long amountRefunded,
            String currency) {
    }
}
