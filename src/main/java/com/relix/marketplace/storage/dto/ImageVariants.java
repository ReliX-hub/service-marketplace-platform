package com.relix.marketplace.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Normalized image URLs for card and detail rendering")
public record ImageVariants(
        @Schema(example = "/api/files/550e8400-e29b-41d4-a716-446655440000.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String thumb,
        @Schema(example = "/api/files/6ba7b810-9dad-11d1-80b4-00c04fd430c8.jpg",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String large) {
}
