package com.relix.marketplace.engagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Administrative decision for a disputed engagement")
public class DisputeResolutionRequest {

    @NotNull(message = "Resolution is required")
    @Schema(description = "Final outcome of the dispute", example = "REFUNDED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Resolution resolution;

    @Size(max = 500, message = "Reason must be at most 500 characters")
    @Schema(description = "Administrative explanation recorded in the audit trail",
            example = "Evidence supports a full client refund.", maxLength = 500,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String reason;

    @Schema(description = "Allowed terminal dispute outcomes")
    public enum Resolution {
        COMPLETED,
        REFUNDED
    }
}
