package com.relix.marketplace.worker.dto;

import com.relix.marketplace.storage.dto.ImageVariants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A recent public service offer and one of its images")
public record RecentWorkItem(
        @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
        Long ticketId,
        @Schema(example = "Weekend home cleaning", requiredMode = Schema.RequiredMode.REQUIRED)
        String ticketTitle,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ImageVariants image) {
}
