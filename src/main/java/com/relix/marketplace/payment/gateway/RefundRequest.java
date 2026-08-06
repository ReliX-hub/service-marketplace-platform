package com.relix.marketplace.payment.gateway;

import java.math.BigDecimal;
import java.util.Map;

public record RefundRequest(
        String paymentIntentId,
        BigDecimal amount,
        String currency,
        Map<String, String> metadata,
        String idempotencyKey) {

    public RefundRequest {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
