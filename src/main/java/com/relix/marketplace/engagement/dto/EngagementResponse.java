package com.relix.marketplace.engagement.dto;

import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.refund.dto.RefundSummaryResponse;
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
@Schema(description = "Matched service engagement and escrow lifecycle timestamps")
public class EngagementResponse {

    @Schema(example = "33", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ticketId;
    @Schema(example = "91", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long applicationId;
    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long clientId;
    @Schema(example = "Alex Morgan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String clientName;
    @Schema(description = "Worker profile ID", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workerId;
    @Schema(description = "Worker account ID", example = "18", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workerUserId;
    @Schema(example = "Jordan Moving Services", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workerDisplayName;
    @Schema(description = "Escrow and delivery lifecycle state", example = "FUNDED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private EngagementStatus status;
    @Schema(description = "Agreed engagement amount", type = "string", example = "150.00",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
    @Schema(example = "Use the rear loading entrance.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String notes;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T14:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant scheduledStart;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant scheduledEnd;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant acceptedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant fundedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T14:05:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant startedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T16:45:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant deliveredAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant approvedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant completedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant disputedAt;
    @Schema(example = "The delivered work is incomplete.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String disputeReason;
    @Schema(type = "string", format = "date-time", example = "2026-08-06T09:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant cancelledAt;
    @Schema(example = "Schedule changed before work began.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String cancellationReason;
    @Schema(description = "Number of private delivery-evidence images attached to this engagement",
            example = "2", minimum = "0", maximum = "8", requiredMode = Schema.RequiredMode.REQUIRED)
    private int deliverableCount;
    @Schema(description = "Refund lifecycle summary when this engagement has a refund; null otherwise",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private RefundSummaryResponse refundSummary;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant updatedAt;
}
