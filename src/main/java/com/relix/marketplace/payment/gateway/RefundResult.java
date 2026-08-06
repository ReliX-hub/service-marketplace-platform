package com.relix.marketplace.payment.gateway;

public record RefundResult(
        String providerRef,
        String status,
        Long amount,
        String currency,
        String paymentIntentId,
        String chargeId,
        String failureReason) {
}
