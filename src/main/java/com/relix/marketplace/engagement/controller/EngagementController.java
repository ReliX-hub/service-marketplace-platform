package com.relix.marketplace.engagement.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.engagement.dto.EngagementActionRequest;
import com.relix.marketplace.engagement.dto.EngagementResponse;
import com.relix.marketplace.engagement.entity.EngagementStatus;
import com.relix.marketplace.engagement.service.EngagementService;
import com.relix.marketplace.payment.dto.PaymentRequest;
import com.relix.marketplace.payment.dto.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
@RequestMapping("/api/engagements")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
@Tag(name = "Engagements", description = "Escrow-backed service delivery workflow")
@SecurityRequirement(name = "bearerAuth")
public class EngagementController {

    private final EngagementService engagementService;

    @GetMapping
    @Operation(summary = "List current user's client or worker engagements")
    public ResponseEntity<ApiResponse<PageResponse<EngagementResponse>>> list(
            @RequestParam(defaultValue = "client") String role,
            @RequestParam(required = false) EngagementStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.getMyEngagements(role, status, pageable(page, size))));
    }

    @GetMapping("/{engagementId}")
    @Operation(summary = "Get an engagement")
    public ResponseEntity<ApiResponse<EngagementResponse>> get(
            @PathVariable Long engagementId) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.getEngagement(engagementId)));
    }

    @PostMapping("/{engagementId}/pay")
    @Operation(summary = "Create or retry payment for an accepted engagement")
    public ResponseEntity<ApiResponse<PaymentResponse>> pay(
            @PathVariable Long engagementId,
            @Valid @RequestBody PaymentRequest request) {
        PaymentResponse payment = engagementService.pay(engagementId, request);
        return ResponseEntity.ok(ApiResponse.success(payment,
                "PENDING".equals(payment.getStatus())
                        ? "Payment confirmation pending"
                        : "Payment processed"));
    }

    @PostMapping("/{engagementId}/start")
    @Operation(summary = "Start funded work")
    public ResponseEntity<ApiResponse<EngagementResponse>> start(
            @PathVariable Long engagementId) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.start(engagementId),
                "Engagement started"));
    }

    @PostMapping("/{engagementId}/deliver")
    @Operation(summary = "Mark work as delivered")
    public ResponseEntity<ApiResponse<EngagementResponse>> deliver(
            @PathVariable Long engagementId) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.deliver(engagementId),
                "Engagement delivered"));
    }

    @PostMapping("/{engagementId}/approve")
    @Operation(summary = "Approve delivered work and release settlement")
    public ResponseEntity<ApiResponse<EngagementResponse>> approve(
            @PathVariable Long engagementId) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.approve(engagementId),
                "Delivery approved"));
    }

    @PostMapping("/{engagementId}/dispute")
    @Operation(summary = "Dispute delivered work")
    public ResponseEntity<ApiResponse<EngagementResponse>> dispute(
            @PathVariable Long engagementId,
            @Valid @RequestBody EngagementActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.dispute(engagementId, request.getReason()),
                "Dispute opened"));
    }

    @PostMapping("/{engagementId}/cancel")
    @Operation(summary = "Cancel an engagement")
    public ResponseEntity<ApiResponse<EngagementResponse>> cancel(
            @PathVariable Long engagementId,
            @Valid @RequestBody(required = false) EngagementActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.cancel(
                        engagementId,
                        request == null ? null : request.getReason()),
                "Engagement cancelled"));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
