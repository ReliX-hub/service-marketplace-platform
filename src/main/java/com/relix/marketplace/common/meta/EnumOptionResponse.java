package com.relix.marketplace.common.meta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Frontend-ready metadata for one API enum value")
public record EnumOptionResponse(
        @Schema(description = "Stable API value", example = "OPEN",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String value,

        @Schema(description = "Human-readable English label", example = "Open",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String label,

        @Schema(description = "Short explanation of when the value applies",
                example = "Published and accepting applications",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String description,

        @Schema(description = "Suggested semantic frontend palette token", example = "emerald",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String colorHint) {
}
