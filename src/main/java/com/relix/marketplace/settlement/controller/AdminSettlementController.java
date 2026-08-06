package com.relix.marketplace.settlement.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.settlement.dto.BatchResponse;
import com.relix.marketplace.settlement.service.SettlementBatchService;
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
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Settlements", description = "Administrative settlement batch processing")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class AdminSettlementController {

    private final SettlementBatchService settlementBatchService;

    @PostMapping("/batch")
    @Operation(summary = "Process the next settlement batch")
    public ResponseEntity<ApiResponse<BatchResponse>> triggerBatch() {
        BatchResponse result = settlementBatchService.processBatch();
        return ResponseEntity.ok(ApiResponse.success(result, "Batch processing completed"));
    }

    @GetMapping("/batches")
    @Operation(summary = "List settlement processing batches")
    public ResponseEntity<ApiResponse<PageResponse<BatchResponse>>> getBatches(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        PageResponse<BatchResponse> batches = settlementBatchService.getAllBatches(pageable);
        return ResponseEntity.ok(ApiResponse.success(batches));
    }
}
