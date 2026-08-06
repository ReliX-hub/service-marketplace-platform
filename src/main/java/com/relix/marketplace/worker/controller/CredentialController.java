package com.relix.marketplace.worker.controller;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.worker.dto.CredentialResponse;
import com.relix.marketplace.worker.dto.CredentialSubmitRequest;
import com.relix.marketplace.worker.entity.WorkerProfile;
import com.relix.marketplace.worker.service.CredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/me/credentials")
@RequiredArgsConstructor
@Tag(name = "Worker Credentials", description = "Current worker credential submissions")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAuthority('CAP_WORKER')")
@Validated
public class CredentialController {

    private final CredentialService credentialService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List credentials for the current worker")
    public ResponseEntity<ApiResponse<PageResponse<CredentialResponse>>> getMyCredentials(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        WorkerProfile worker = currentUserService.requireWorkerProfile();
        return ResponseEntity.ok(ApiResponse.success(
                credentialService.getWorkerCredentials(
                        worker.getId(),
                        PageRequest.of(
                                page,
                                size,
                                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))))));
    }

    @PostMapping
    @Operation(summary = "Submit or resubmit a credential for review")
    public ResponseEntity<ApiResponse<CredentialResponse>> submit(
            @Valid @RequestBody CredentialSubmitRequest request) {
        WorkerProfile worker = currentUserService.requireWorkerProfile();
        return ResponseEntity.ok(ApiResponse.success(
                credentialService.submit(worker, request),
                "Credential submitted for review"));
    }

    @PostMapping(value = "/{credentialId}/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload or replace a pending credential document",
            description = "The image is normalized, stripped of metadata, stored privately, and may only be changed while the credential is PENDING.")
    public ResponseEntity<ApiResponse<CredentialResponse>> uploadDocument(
            @Parameter(description = "Credential identifier", example = "42")
            @PathVariable Long credentialId,
            @Parameter(description = "JPEG, PNG, or WebP image (maximum 8 MB)", required = true)
            @RequestPart("file") MultipartFile file) {
        WorkerProfile worker = currentUserService.requireWorkerProfile();
        return ResponseEntity.ok(ApiResponse.success(
                credentialService.uploadDocument(
                        credentialId,
                        worker,
                        file),
                "Credential document uploaded"));
    }
}
