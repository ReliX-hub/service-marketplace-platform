package com.relix.marketplace.refund.dto;

import com.relix.marketplace.refund.entity.Refund;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Compact refund state included with an engagement for its client and worker.
 * Provider references intentionally remain available only through the refund API.
 */
@Schema(description = "Compact refund lifecycle state associated with an engagement")
public record RefundSummaryResponse(
        @Schema(example = "15", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,

        @Schema(type = "string", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount,

        @Schema(description = "Refund lifecycle state", example = "PENDING",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Refund.RefundStatus status,

        @Schema(type = "string", format = "date-time", example = "2026-08-05T13:00:00Z",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant refundedAt,

        @Schema(description = "Failure diagnostic, populated only when the refund has failed",
                example = "The payment provider could not complete the refund",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String failureMessage,

        @Schema(type = "string", format = "date-time", example = "2026-08-05T13:00:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant updatedAt) {
}
