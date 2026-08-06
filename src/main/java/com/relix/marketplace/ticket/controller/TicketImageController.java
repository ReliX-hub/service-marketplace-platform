package com.relix.marketplace.ticket.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.ticket.dto.TicketImageResponse;
import com.relix.marketplace.ticket.service.TicketImageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/tickets/{ticketId}/images")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ticket Images", description = "Author-managed photos for marketplace listings")
public class TicketImageController {

    private final TicketImageService ticketImageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a ticket photo",
            description = "Only the author may upload. DRAFT photos remain private; OPEN photos are public. The response contains both normalized variants for immediate rendering.")
    public ResponseEntity<ApiResponse<TicketImageResponse>> upload(
            @Parameter(description = "Ticket ID", example = "42")
            @PathVariable @Positive Long ticketId,
            @Parameter(description = "JPEG, PNG, or WebP image; maximum 8 MB", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Optional photo caption", example = "Leak beneath the shutoff valve")
            @RequestParam(required = false) @Size(max = 160) String caption) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        ticketImageService.addImage(ticketId, file, caption),
                        "Ticket image uploaded"));
    }

    @DeleteMapping("/{imageId}")
    @Operation(
            summary = "Delete a ticket photo",
            description = "Only the author may delete photos while the ticket is DRAFT or OPEN. Remaining positions and cover-image caches are recalculated atomically.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Ticket ID", example = "42")
            @PathVariable @Positive Long ticketId,
            @Parameter(description = "Ticket image ID", example = "91")
            @PathVariable @Positive Long imageId) {
        ticketImageService.deleteImage(ticketId, imageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Ticket image deleted"));
    }

    @PutMapping("/order")
    @Operation(
            summary = "Reorder ticket photos",
            description = "The JSON array must contain every current image ID exactly once. Final non-negative positions are written with the database uniqueness constraint deferred until commit.")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Complete ordered image-ID array",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    array = @ArraySchema(schema = @Schema(type = "integer", format = "int64", example = "91"))))
    public ResponseEntity<ApiResponse<List<TicketImageResponse>>> reorder(
            @Parameter(description = "Ticket ID", example = "42")
            @PathVariable @Positive Long ticketId,
            @RequestBody List<@NotNull @Positive Long> imageIds) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketImageService.reorderImages(ticketId, imageIds),
                "Ticket images reordered"));
    }
}
