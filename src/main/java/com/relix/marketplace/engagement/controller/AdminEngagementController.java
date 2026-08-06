package com.relix.marketplace.engagement.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.engagement.dto.DisputeResolutionRequest;
import com.relix.marketplace.engagement.dto.EngagementResponse;
import com.relix.marketplace.engagement.service.EngagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/engagements")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Engagements", description = "Engagement dispute resolution")
@SecurityRequirement(name = "bearerAuth")
public class AdminEngagementController {

    private final EngagementService engagementService;

    @PostMapping("/{engagementId}/resolve")
    @Operation(summary = "Resolve a disputed engagement")
    public ResponseEntity<ApiResponse<EngagementResponse>> resolve(
            @PathVariable Long engagementId,
            @Valid @RequestBody DisputeResolutionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                engagementService.resolveDispute(engagementId, request),
                "Dispute resolved"));
    }
}
