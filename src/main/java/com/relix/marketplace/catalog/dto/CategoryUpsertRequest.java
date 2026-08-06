package com.relix.marketplace.catalog.dto;

import com.relix.marketplace.worker.entity.Credential;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Category create or full-update request")
public class CategoryUpsertRequest {

    @NotBlank
    @Size(max = 50)
    @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]*",
            message = "code must start with a letter and contain only letters, digits, or underscores")
    @Schema(description = "Stable uppercase category code", example = "HOME_CLEANING",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Human-readable English category name", example = "Home Cleaning",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(example = "Routine, deep, and move-out cleaning services.",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String description;

    @Size(max = 100)
    @Schema(example = "sparkles", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String icon;

    @Schema(description = "Credential required for workers in this category", nullable = true,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Credential.Type requiredCredential;

    @Schema(description = "Parent category ID; null makes this a root category", nullable = true,
            example = "2", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Long parentId;

    @Schema(description = "Initial or updated active state; defaults to true on create", nullable = true,
            example = "true", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private Boolean active;
}
