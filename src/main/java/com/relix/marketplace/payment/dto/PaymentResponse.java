package com.relix.marketplace.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Local payment record and provider confirmation data")
public class PaymentResponse {

    @Schema(example = "81", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long paymentId;
    @Schema(example = "33", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long engagementId;
    @Schema(example = "pay-33-20260805-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
    @Schema(type = "string", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
    @Schema(example = "USD", pattern = "[A-Z]{3}", requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;
    @Schema(description = "Local payment state", example = "PENDING",
            allowableValues = {"PENDING", "SUCCEEDED", "FAILED", "REFUNDED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant paidAt;
    @Schema(description = "Stripe PaymentIntent ID or mock provider reference", example = "pi_3Example",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String paymentIntentId;
    @Schema(description = "Transient Stripe Elements confirmation secret; returned only while recovering or creating a pending payment and never persisted locally",
            example = "pi_3Example_secret_example", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @ToString.Exclude
    private String clientSecret;
    @Schema(description = "Raw provider lifecycle state", example = "requires_payment_method",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String providerStatus;
    @Schema(description = "Provider failure explanation", example = "Your card was declined.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String failureMessage;
    @Schema(description = "Whether the engagement already had a successful payment", example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean alreadyPaid;
    @Schema(description = "Whether this response replays the same request ID", example = "false",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean requestIdMatched;
}
