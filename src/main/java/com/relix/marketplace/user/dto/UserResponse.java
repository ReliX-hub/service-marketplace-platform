package com.relix.marketplace.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Marketplace user profile and account capabilities")
public class UserResponse {

    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(example = "Alex Morgan", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;
    @Schema(example = "alex@example.com", format = "email", requiredMode = Schema.RequiredMode.REQUIRED)
    private String email;
    @Schema(example = "+1-312-555-0142", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String phone;
    @Schema(example = "USER", allowableValues = {"USER", "ADMIN"}, requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    @Schema(example = "[\"CLIENT\",\"WORKER\"]", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<String> capabilities;
    @Schema(example = "ACTIVE", allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"},
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(description = "Average rating received as a client", type = "string", example = "4.80",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal clientRating;
    @Schema(example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer clientReviewCount;
    @Schema(example = "https://cdn.example.com/avatars/12.jpg", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String avatarUrl;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
}
