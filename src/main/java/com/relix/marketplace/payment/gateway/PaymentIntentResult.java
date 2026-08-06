package com.relix.marketplace.payment.gateway;

public record PaymentIntentResult(
        String providerRef,
        String clientSecret,
        String status) {

    @Override
    public String toString() {
        return "PaymentIntentResult[providerRef=" + providerRef
                + ", clientSecret=<redacted>, status=" + status + "]";
    }
}
