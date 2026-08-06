package com.relix.marketplace.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Exchange a valid refresh token for a new access token")
public class RefreshRequest {

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "JWT refresh token returned by login or registration",
            example = "eyJhbGciOiJIUzI1NiJ9.refresh-token...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
}
