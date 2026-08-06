package com.relix.marketplace.application.controller;

import com.relix.marketplace.application.dto.ApplicationCreateRequest;
import com.relix.marketplace.application.dto.ApplicationResponse;
import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.application.service.ApplicationService;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
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
@PreAuthorize("isAuthenticated()")
@Tag(name = "Applications", description = "Ticket applications and matching")
@SecurityRequirement(name = "bearerAuth")
public class ApplicationController {

    private final ApplicationService applicationService;

    @PostMapping("/tickets/{ticketId}/applications")
    @Operation(summary = "Apply to an open ticket")
    public ResponseEntity<ApiResponse<ApplicationResponse>> apply(
            @PathVariable Long ticketId,
            @Valid @RequestBody ApplicationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        applicationService.apply(ticketId, request),
                        "Application submitted"));
    }

    @GetMapping("/tickets/{ticketId}/applications")
    @Operation(summary = "List applications for a ticket", description = "Only the ticket author may access this list")
    public ResponseEntity<ApiResponse<PageResponse<ApplicationResponse>>> getTicketApplications(
            @PathVariable Long ticketId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                applicationService.getTicketApplications(ticketId, status, pageable(page, size))));
    }

    @GetMapping("/me/applications")
    @Operation(summary = "List applications submitted by the current user")
    public ResponseEntity<ApiResponse<PageResponse<ApplicationResponse>>> getMyApplications(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(ApiResponse.success(
                applicationService.getMyApplications(status, pageable(page, size))));
    }

    @PostMapping("/applications/{applicationId}/accept")
    @Operation(summary = "Accept an application and create an engagement")
    public ResponseEntity<ApiResponse<ApplicationResponse>> accept(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.success(
                applicationService.accept(applicationId),
                "Application accepted"));
    }

    @PostMapping("/applications/{applicationId}/reject")
    @Operation(summary = "Reject a pending application")
    public ResponseEntity<ApiResponse<ApplicationResponse>> reject(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.success(
                applicationService.reject(applicationId),
                "Application rejected"));
    }

    @PostMapping("/applications/{applicationId}/withdraw")
    @Operation(summary = "Withdraw your pending application")
    public ResponseEntity<ApiResponse<ApplicationResponse>> withdraw(
            @PathVariable Long applicationId) {
        return ResponseEntity.ok(ApiResponse.success(
                applicationService.withdraw(applicationId),
                "Application withdrawn"));
    }

    private Pageable pageable(int page, int size) {
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
