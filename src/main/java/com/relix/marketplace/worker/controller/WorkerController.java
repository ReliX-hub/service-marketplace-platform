package com.relix.marketplace.worker.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.worker.dto.WorkerProfileResponse;
import com.relix.marketplace.worker.service.WorkerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
@Validated
@Tag(name = "Workers", description = "Worker profile discovery")
public class WorkerController {

    private final WorkerProfileService workerProfileService;

    @GetMapping
    @Operation(summary = "List all workers")
    public ResponseEntity<ApiResponse<PageResponse<WorkerProfileResponse>>> getAllWorkers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                workerProfileService.getAllWorkers(newestFirst(page, size))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get worker by ID")
    public ResponseEntity<ApiResponse<WorkerProfileResponse>> getWorkerById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(workerProfileService.getWorkerById(id)));
    }

    @GetMapping("/verified")
    @Operation(summary = "List verified workers")
    public ResponseEntity<ApiResponse<PageResponse<WorkerProfileResponse>>> getVerifiedWorkers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                workerProfileService.getVerifiedWorkers(newestFirst(page, size))));
    }

    private Pageable newestFirst(int page, int size) {
        return PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
