package com.relix.marketplace.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Immutable business audit event")
public class AuditLogResponse {

    @Schema(example = "501", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "ENGAGEMENT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String entityType;
    @Schema(example = "33", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long entityId;
    @Schema(example = "ENGAGEMENT_FUNDED", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;
    @Schema(example = "USER", allowableValues = {"USER", "ADMIN", "SYSTEM"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String actorType;
    @Schema(example = "12", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long actorId;
    @Schema(description = "JSON object containing event-specific context",
            example = "{\"paymentId\":81}", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String details;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
}
