package com.relix.marketplace.engagement.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.engagement.dto.DeliverableResponse;
import com.relix.marketplace.engagement.service.DeliverableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/engagements/{engagementId}/deliverables")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
@Tag(name = "Engagement Deliverables", description = "Private delivery evidence for engagement participants")
@SecurityRequirement(name = "bearerAuth")
public class DeliverableController {

    private final DeliverableService deliverableService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload delivery evidence",
            description = "Only the assigned worker may upload while the engagement is IN_PROGRESS. The image is normalized into private thumb and large variants.")
    public ResponseEntity<ApiResponse<DeliverableResponse>> upload(
            @Parameter(description = "Engagement ID", example = "33")
            @PathVariable @Positive Long engagementId,
            @Parameter(description = "JPEG, PNG, or WebP image (maximum 8 MB)", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional delivery note", example = "Leak tested for ten minutes.")
            @RequestParam(required = false) @Size(max = 300) String note) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                deliverableService.upload(engagementId, file, note),
                "Delivery evidence uploaded"));
    }

    @GetMapping
    @Operation(
            summary = "List delivery evidence",
            description = "Readable by the engagement client, assigned worker, and administrators.")
    public ResponseEntity<ApiResponse<List<DeliverableResponse>>> list(
            @Parameter(description = "Engagement ID", example = "33")
            @PathVariable @Positive Long engagementId) {
        return ResponseEntity.ok(ApiResponse.success(
                deliverableService.getDeliverables(engagementId)));
    }

    @DeleteMapping("/{deliverableId}")
    @Operation(
            summary = "Delete delivery evidence",
            description = "Only the assigned worker may delete evidence while the engagement is IN_PROGRESS. Evidence becomes immutable after delivery.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Engagement ID", example = "33")
            @PathVariable @Positive Long engagementId,
            @Parameter(description = "Deliverable ID", example = "73")
            @PathVariable @Positive Long deliverableId) {
        deliverableService.delete(engagementId, deliverableId);
        return ResponseEntity.ok(ApiResponse.success(null, "Delivery evidence deleted"));
    }
}
