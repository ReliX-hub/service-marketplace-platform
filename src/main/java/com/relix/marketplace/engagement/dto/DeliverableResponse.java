package com.relix.marketplace.engagement.dto;

import com.relix.marketplace.storage.dto.ImageVariants;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Immutable image evidence submitted by the assigned worker")
public record DeliverableResponse(
        @Schema(example = "73", requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ImageVariants image,
        @Schema(description = "Zero-based display order", example = "0",
                requiredMode = Schema.RequiredMode.REQUIRED)
        int position,
        @Schema(example = "Repaired connection and dry cabinet after the leak test.",
                maxLength = 300, requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String note,
        @Schema(description = "Account ID of the worker who submitted the evidence",
                example = "18", requiredMode = Schema.RequiredMode.REQUIRED)
        Long submittedBy,
        @Schema(type = "string", format = "date-time", example = "2026-08-08T16:45:00Z",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt) {
}
