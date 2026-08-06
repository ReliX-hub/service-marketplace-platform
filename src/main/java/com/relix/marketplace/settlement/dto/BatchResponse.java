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
@Schema(description = "Administrative settlement batch execution summary")
public class BatchResponse {

    @Schema(example = "9", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "SETTLE-20260805-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String batchId;
    @Schema(example = "COMPLETED", allowableValues = {"PENDING", "PROCESSING", "COMPLETED", "FAILED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalCount;
    @Schema(example = "10", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer successCount;
    @Schema(example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer failedCount;
    @Schema(type = "string", example = "1350.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T09:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant startedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T09:00:04Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant completedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T09:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
}
