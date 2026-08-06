package com.relix.marketplace.catalog.dto;

import com.relix.marketplace.worker.entity.Credential;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Marketplace category, optionally containing active child categories")
public class CategoryResponse {

    @Schema(example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(example = "ELECTRICAL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(example = "Electrical", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "Licensed residential and small-business electrical work.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Schema(example = "zap", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String icon;

    @Schema(description = "Credential required to offer or apply for work in this category",
            example = "ELECTRICAL_LICENSE", nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Credential.Type requiredCredential;

    @Schema(example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean active;

    @Schema(description = "Parent category ID", example = "2", nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long parentId;

    @Builder.Default
    @Schema(description = "Active child categories", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<CategoryResponse> children = new ArrayList<>();

    @Schema(type = "string", format = "date-time", example = "2026-01-10T10:00:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant createdAt;
    @Schema(type = "string", format = "date-time", example = "2026-08-05T12:30:00Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private Instant updatedAt;
}
