package com.relix.marketplace.refund.controller;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.refund.dto.RefundResponse;
import com.relix.marketplace.refund.service.RefundService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CAP_CLIENT') or hasRole('ADMIN')")
@Tag(name = "Refunds", description = "Client refund history and status")
@SecurityRequirement(name = "bearerAuth")
public class RefundController {

    private final RefundService refundService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List accessible refunds")
    public ResponseEntity<ApiResponse<PageResponse<RefundResponse>>> getRefunds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<RefundResponse> refunds;
        if (currentUserService.isAdmin()) {
            refunds = refundService.getAllRefunds(pageable);
        } else {
            Long currentUserId = currentUserService.getCurrentUserId();
            refunds = refundService.getRefundsByClientId(currentUserId, pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(refunds));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an accessible refund by ID")
    public ResponseEntity<ApiResponse<RefundResponse>> getRefund(@PathVariable Long id) {
        RefundResponse refund = refundService.getRefundById(
                id,
                currentUserService.getCurrentUserId(),
                currentUserService.isAdmin()
        );
        return ResponseEntity.ok(ApiResponse.success(refund));
    }
}
