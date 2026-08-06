package com.relix.marketplace.settlement.controller;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.settlement.dto.SettlementResponse;
import com.relix.marketplace.settlement.dto.SettlementSummaryResponse;
import com.relix.marketplace.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CAP_WORKER') or hasRole('ADMIN')")
@Tag(name = "Settlements", description = "Worker payouts and settlement summaries")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class SettlementController {

    private final SettlementService settlementService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List accessible settlements")
    public ResponseEntity<ApiResponse<PageResponse<SettlementResponse>>> getSettlements(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        PageResponse<SettlementResponse> settlements;
        if (currentUserService.isAdmin()) {
            settlements = settlementService.getAllSettlements(pageable);
        } else {
            settlements = settlementService.getSettlementsByWorkerId(
                    currentUserService.requireWorkerProfile().getId(),
                    pageable);
        }
        return ResponseEntity.ok(ApiResponse.success(settlements));
    }

    @GetMapping("/summary")
    @Operation(summary = "Get the accessible settlement summary")
    public ResponseEntity<ApiResponse<SettlementSummaryResponse>> getSettlementSummary() {
        SettlementSummaryResponse summary;
        if (currentUserService.isAdmin()) {
            summary = settlementService.getOverallSettlementSummary();
        } else {
            summary = settlementService.getSettlementSummary(currentUserService.requireWorkerProfile().getId());
        }
        return ResponseEntity.ok(ApiResponse.success(summary));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an accessible settlement by ID")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlement(@PathVariable Long id) {
        SettlementResponse settlement = settlementService.getSettlementByIdWithAccess(
                id,
                currentUserService.getCurrentUserId(),
                currentUserService.isAdmin()
        );
        return ResponseEntity.ok(ApiResponse.success(settlement));
    }

    @GetMapping("/engagement/{engagementId}")
    @Operation(summary = "Get the settlement for an engagement")
    public ResponseEntity<ApiResponse<SettlementResponse>> getSettlementByEngagementId(
            @PathVariable Long engagementId) {
        SettlementResponse settlement = settlementService.getSettlementByEngagementIdWithAccess(
                engagementId,
                currentUserService.getCurrentUserId(),
                currentUserService.isAdmin()
        );
        return ResponseEntity.ok(ApiResponse.success(settlement));
    }
}
