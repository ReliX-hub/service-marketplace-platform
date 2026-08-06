package com.relix.marketplace.catalog.controller;

import com.relix.marketplace.catalog.dto.CategoryResponse;
import com.relix.marketplace.catalog.service.CategoryService;
import com.relix.marketplace.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Public marketplace category catalog")
@PreAuthorize("permitAll()")
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @Operation(summary = "List active categories as a tree")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getCategoryTree() {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getPublicTree()));
    }

    @GetMapping("/{code}")
    @Operation(summary = "Get an active category by code")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryByCode(
            @Parameter(example = "ELECTRICAL") @PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.success(categoryService.getPublicByCode(code)));
    }
}
