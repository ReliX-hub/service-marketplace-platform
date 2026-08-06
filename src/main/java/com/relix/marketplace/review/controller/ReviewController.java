package com.relix.marketplace.review.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.review.dto.ReviewCreateRequest;
import com.relix.marketplace.review.dto.ReviewResponse;
import com.relix.marketplace.review.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Tag(name = "Reviews", description = "Bidirectional engagement reviews and public reputation history")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/engagements/{engagementId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Review a completed engagement",
            description = "Only a participant may review. The server derives the direction from the authenticated participant.")
    public ResponseEntity<ApiResponse<ReviewResponse>> createReview(
            @Parameter(description = "Completed engagement ID", example = "42")
            @PathVariable Long engagementId,
            @Valid @RequestBody ReviewCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        reviewService.createReview(engagementId, request),
                        "Review submitted"));
    }

    @GetMapping("/workers/{workerId}/reviews")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List public client reviews for a worker")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getWorkerReviews(
            @Parameter(description = "Worker profile ID", example = "7") @PathVariable Long workerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getWorkerReviews(workerId, pageable(page, size))));
    }

    @GetMapping("/users/{userId}/reviews")
    @PreAuthorize("permitAll()")
    @Operation(summary = "List public worker reviews for a client")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> getUserReviews(
            @Parameter(description = "Client user ID", example = "12") @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getUserReviews(userId, pageable(page, size))));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
