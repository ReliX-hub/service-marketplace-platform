package com.relix.marketplace.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Administrative credential rejection reason")
public class CredentialRejectRequest {

    @NotBlank
    @Size(max = 500)
    @Schema(description = "Reason shown to the submitting worker",
            example = "The uploaded document is incomplete.",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
}
