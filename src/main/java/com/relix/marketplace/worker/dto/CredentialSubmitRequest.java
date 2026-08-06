package com.relix.marketplace.worker.dto;

import com.relix.marketplace.worker.entity.Credential;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Submit or resubmit the current credential record for review")
public class CredentialSubmitRequest {

    @NotNull
    @Schema(description = "Credential category being submitted", example = "DRIVER_LICENSE",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Credential.Type type;

    @Size(max = 100)
    @Schema(description = "Issuer-provided credential identifier", example = "IL-DL-1234567",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String credentialNumber;

    @PastOrPresent
    @Schema(type = "string", format = "date", example = "2025-01-15",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate issuedAt;

    @Future
    @Schema(type = "string", format = "date", example = "2028-01-15",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private LocalDate expiresAt;
}
