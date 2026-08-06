package com.relix.marketplace.worker.dto;

import com.relix.marketplace.storage.dto.FileReferenceResponse;
import com.relix.marketplace.worker.entity.Credential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "A worker credential and its current review state")
public class CredentialResponse {

    @Schema(example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workerId;
    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long workerUserId;
    @Schema(example = "Alex Home Services", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workerDisplayName;
    @Schema(description = "Credential category", example = "DRIVER_LICENSE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Credential.Type type;
    @Schema(description = "Current administrative review state", example = "PENDING",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Credential.Status status;
    @Schema(example = "IL-DL-1234567", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String credentialNumber;
    @Schema(description = "Managed private image or retained legacy external reference. Null until uploaded.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private FileReferenceResponse document;

    @Schema(type = "string", format = "date", example = "2025-01-15",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate issuedAt;

    @Schema(type = "string", format = "date", example = "2028-01-15",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate expiresAt;

    @Schema(example = "The uploaded document is incomplete.", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String rejectionReason;
    @Schema(example = "1", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long reviewedByUserId;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Instant reviewedAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-04T09:15:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant updatedAt;
}
