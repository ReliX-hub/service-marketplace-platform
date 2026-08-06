package com.relix.marketplace.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Asynchronous payment refund record")
public class RefundResponse {

    @Schema(example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "33", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long engagementId;
    @Schema(example = "81", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long paymentId;
    @Schema(type = "string", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
    @Schema(example = "Engagement cancelled after funding", requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
    @Schema(description = "Refund provider lifecycle state", example = "PENDING",
            allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(example = "re_3Example", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String providerRefundId;
    @Schema(example = "pending", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String providerStatus;
    @Schema(example = "insufficient_funds", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String failureMessage;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T13:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant refundedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T13:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant updatedAt;
}
