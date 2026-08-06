package com.relix.marketplace.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "A managed private file or retained legacy external reference")
public record FileReferenceResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String url,
        @Schema(description = "True when the URL is served by this platform", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean managed,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String contentType,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Long byteSize,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer width,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Integer height) {
}
