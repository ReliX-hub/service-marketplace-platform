package com.relix.marketplace.payment.gateway;

final class GatewayRequestValidator {

    private static final int STRIPE_IDEMPOTENCY_KEY_MAX_LENGTH = 255;

    private GatewayRequestValidator() {
    }

    static void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }
        if (idempotencyKey.length() > STRIPE_IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new IllegalArgumentException("Idempotency key must not exceed 255 characters");
        }
    }

    static void validatePaymentIntentId(String paymentIntentId) {
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new IllegalArgumentException("Payment intent provider reference is required");
        }
    }

    static void validateRefundId(String refundId) {
        if (refundId == null || refundId.isBlank()) {
            throw new IllegalArgumentException("Refund provider reference is required");
        }
    }
}
