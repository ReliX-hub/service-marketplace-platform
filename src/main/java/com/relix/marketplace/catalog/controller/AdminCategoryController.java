package com.relix.marketplace.catalog.controller;

import com.relix.marketplace.catalog.dto.CategoryResponse;
import com.relix.marketplace.catalog.dto.CategoryUpsertRequest;
import com.relix.marketplace.catalog.service.CategoryService;
import com.relix.marketplace.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@Tag(name = "Admin Categories", description = "Administrative category catalog management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(
            @Valid @RequestBody CategoryUpsertRequest request) {
        CategoryResponse response = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Category created"));
    }

    @PutMapping("/{categoryId}")
    @Operation(summary = "Update a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(
            @Parameter(example = "3") @PathVariable Long categoryId,
            @Valid @RequestBody CategoryUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.update(categoryId, request),
                "Category updated"));
    }

    @PostMapping("/{categoryId}/activate")
    @Operation(summary = "Activate a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> activate(
            @Parameter(example = "3") @PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.activate(categoryId),
                "Category activated"));
    }

    @PostMapping("/{categoryId}/deactivate")
    @Operation(summary = "Deactivate a category")
    public ResponseEntity<ApiResponse<CategoryResponse>> deactivate(
            @Parameter(example = "3") @PathVariable Long categoryId) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryService.deactivate(categoryId),
                "Category deactivated"));
    }
}
