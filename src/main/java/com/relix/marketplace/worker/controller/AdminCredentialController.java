package com.relix.marketplace.worker.controller;

import com.relix.marketplace.auth.service.CurrentUserService;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.worker.dto.CredentialRejectRequest;
import com.relix.marketplace.worker.dto.CredentialResponse;
import com.relix.marketplace.worker.entity.Credential;
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
@RequestMapping("/api/admin/credentials")
@RequiredArgsConstructor
@Tag(name = "Admin Credentials", description = "Administrative credential review")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
@Validated
public class AdminCredentialController {

    private final CredentialService credentialService;
    private final CurrentUserService currentUserService;

    @GetMapping
    @Operation(summary = "List credentials by review status")
    public ResponseEntity<ApiResponse<PageResponse<CredentialResponse>>> getByStatus(
            @RequestParam(defaultValue = "PENDING") Credential.Status status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(credentialService.getByStatus(
                status,
                PageRequest.of(
                        page,
                        size,
                        Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))))));
    }

    @PostMapping("/{credentialId}/verify")
    @Operation(summary = "Verify a pending credential")
    public ResponseEntity<ApiResponse<CredentialResponse>> verify(
            @Parameter(example = "42") @PathVariable Long credentialId) {
        return ResponseEntity.ok(ApiResponse.success(
                credentialService.verify(credentialId, currentUserService.getCurrentUser()),
                "Credential verified"));
    }

    @PostMapping("/{credentialId}/reject")
    @Operation(summary = "Reject a pending credential")
    public ResponseEntity<ApiResponse<CredentialResponse>> reject(
            @Parameter(example = "42") @PathVariable Long credentialId,
            @Valid @RequestBody CredentialRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                credentialService.reject(
                        credentialId,
                        request.getReason(),
                        currentUserService.getCurrentUser()),
                "Credential rejected"));
    }
}
