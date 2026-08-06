package com.relix.marketplace.worker.controller;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.worker.dto.WorkerProfileResponse;
import com.relix.marketplace.worker.dto.WorkerProfileUpsertRequest;
import com.relix.marketplace.worker.service.WorkerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workers/profile")
@RequiredArgsConstructor
@Tag(name = "Worker Profile", description = "Worker profile management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('CAP_WORKER')")
public class WorkerProfileController {

    private final WorkerProfileService workerProfileService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @Operation(summary = "Create worker profile for current user")
    public ResponseEntity<ApiResponse<WorkerProfileResponse>> createProfile(
            @Valid @RequestBody WorkerProfileUpsertRequest request) {
        WorkerProfileResponse response = workerProfileService.upsertWorkerProfile(currentUserService.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Worker profile saved"));
    }

    @PutMapping
    @Operation(summary = "Update worker profile for current user")
    public ResponseEntity<ApiResponse<WorkerProfileResponse>> updateProfile(
            @Valid @RequestBody WorkerProfileUpsertRequest request) {
        WorkerProfileResponse response = workerProfileService.upsertWorkerProfile(currentUserService.getCurrentUser(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Worker profile updated"));
    }
}
