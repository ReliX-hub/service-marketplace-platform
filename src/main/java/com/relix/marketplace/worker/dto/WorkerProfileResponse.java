package com.relix.marketplace.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Public worker profile and reputation summary")
public class WorkerProfileResponse {

    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    @Schema(example = "Alex Home Services", requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;
    @Schema(example = "Reliable help for moves and home projects", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String headline;
    @Schema(example = "Experienced local worker available evenings and weekends.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;
    @Schema(example = "1200 Market Street, Chicago, IL", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String address;
    @Schema(type = "string", example = "4.92", minimum = "0", maximum = "5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal rating;
    @Schema(example = "38", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer reviewCount;
    @Schema(description = "Whether the public worker profile is platform verified", example = "true",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean verified;
    @Schema(example = "54", minimum = "0", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer completedJobs;
    @Schema(type = "string", example = "25.00", minimum = "0",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal serviceRadiusKm;
    @Builder.Default
    @Schema(description = "Images from the worker's most recent public service offers",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RecentWorkItem> recentWork = List.of();
    @Schema(type = "string", format = "date-time", example = "2026-06-15T14:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
}
