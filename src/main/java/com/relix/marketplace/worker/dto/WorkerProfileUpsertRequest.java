package com.relix.marketplace.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create or replace the current user's worker profile")
public class WorkerProfileUpsertRequest {

    @NotBlank(message = "Display name is required")
    @Size(max = 200, message = "Display name must be at most 200 characters")
    @Schema(example = "Alex Home Services", maxLength = 200,
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String displayName;

    @Size(max = 255, message = "Headline must be at most 255 characters")
    @Schema(example = "Reliable help for moves and home projects", maxLength = 255,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String headline;

    @Schema(example = "Experienced local worker available evenings and weekends.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Size(max = 500, message = "Address must be at most 500 characters")
    @Schema(example = "1200 Market Street, Chicago, IL", maxLength = 500,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String address;

    @Schema(description = "Maximum on-site travel radius in kilometers", type = "string",
            example = "25.00", minimum = "0", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private BigDecimal serviceRadiusKm;
}
