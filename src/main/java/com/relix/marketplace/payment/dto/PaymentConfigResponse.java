package com.relix.marketplace.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Browser-safe payment provider configuration")
public record PaymentConfigResponse(
        @Schema(description = "Active payment gateway", example = "mock",
                allowableValues = {"mock", "stripe"}, requiredMode = Schema.RequiredMode.REQUIRED)
        String gateway,

        @Schema(description = "Stripe publishable key; null when the mock gateway is active",
                example = "pk_test_example", nullable = true,
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String publishableKey) {
}
