package com.relix.marketplace.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Idempotent payment initiation request")
public class PaymentRequest {

    @NotBlank(message = "Request ID is required")
    @Size(max = 64, message = "Request ID must be at most 64 characters")
    @Schema(description = "Client-generated idempotency key; reuse it when retrying the same payment",
            example = "pay-33-20260805-001", maxLength = 64,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
}
