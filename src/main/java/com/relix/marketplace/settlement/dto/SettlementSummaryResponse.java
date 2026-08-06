package com.relix.marketplace.settlement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Aggregated settlement totals for the accessible scope")
public class SettlementSummaryResponse {

    @Schema(type = "string", example = "1425.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalEarnings;
    @Schema(type = "string", example = "1200.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal completedAmount;
    @Schema(type = "string", example = "225.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal pendingAmount;
    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalCount;
    @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private long completedCount;
    @Schema(example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private long pendingCount;
    @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private long failedCount;
}
