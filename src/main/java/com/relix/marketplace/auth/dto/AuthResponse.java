package com.relix.marketplace.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authenticated user identity and bearer tokens")
public class AuthResponse {

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    @Schema(example = "alex@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
    @Schema(example = "Alex Morgan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(description = "Administrative account role", example = "USER",
            allowableValues = {"USER", "ADMIN"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    @Schema(description = "Marketplace capabilities granted to the account",
            example = "[\"CLIENT\",\"WORKER\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> capabilities;
    @Schema(description = "Short-lived JWT bearer token", example = "eyJhbGciOiJIUzI1NiJ9.access-token...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String accessToken;
    @Schema(description = "Longer-lived JWT used to refresh access", example = "eyJhbGciOiJIUzI1NiJ9.refresh-token...",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String refreshToken;
    @Schema(example = "Bearer", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tokenType;
    @Schema(description = "Access-token lifetime in seconds", example = "900",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expiresIn;
}
