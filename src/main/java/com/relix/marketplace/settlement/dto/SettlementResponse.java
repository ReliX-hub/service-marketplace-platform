package com.relix.marketplace.settlement.dto;

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
@Schema(description = "Platform-fee calculation and worker payout record")
public class SettlementResponse {

    @Schema(example = "27", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "33", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long engagementId;
    @Schema(type = "string", example = "150.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;
    @Schema(type = "string", example = "15.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal platformFee;
    @Schema(type = "string", example = "135.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal workerPayout;
    @Schema(example = "PENDING", allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(type = "string", format = "date-time", example = "2026-08-10T09:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant settledAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-08T17:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
}
