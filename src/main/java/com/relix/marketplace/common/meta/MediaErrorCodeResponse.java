package com.relix.marketplace.common.meta;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable machine-readable error metadata for media workflows")
public record MediaErrorCodeResponse(
        @Schema(description = "Stable API error code", example = "IMAGE_TYPE_UNSUPPORTED",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(description = "Frontend-facing explanation of when the error is returned",
                example = "The uploaded bytes are not a supported JPEG, PNG, or WebP image",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String description) {
}
