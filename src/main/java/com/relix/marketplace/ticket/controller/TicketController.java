package com.relix.marketplace.ticket.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.ticket.dto.TicketCreateRequest;
import com.relix.marketplace.ticket.dto.TicketDetailResponse;
import com.relix.marketplace.ticket.dto.TicketSearchCriteria;
import com.relix.marketplace.ticket.dto.TicketSummaryResponse;
import com.relix.marketplace.ticket.dto.TicketUpdateRequest;
import com.relix.marketplace.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "Public discovery and author-managed marketplace listings")
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    @Operation(
            summary = "Browse open marketplace tickets",
            description = "Returns unexpired OPEN listings. Price filters match fixed prices and overlapping budget ranges; serviceFrom/serviceTo match overlapping service windows. Open bids are excluded when a price filter is present.")
    public ResponseEntity<ApiResponse<PageResponse<TicketSummaryResponse>>> listTickets(
            @Valid @ParameterObject TicketSearchCriteria criteria) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.listPublicTickets(criteria)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get a public ticket",
            description = "Unexpired OPEN, MATCHED, and CLOSED tickets are public. Expired listings return TICKET_EXPIRED; a successful read atomically increments the view counter.")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getTicket(
            @Parameter(description = "Ticket ID", example = "42") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getPublicTicket(id)));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a draft ticket")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> createTicket(
            @Valid @RequestBody TicketCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ticketService.createTicket(request), "Ticket created as draft"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Update a ticket",
            description = "Only the author may update it. Category, pricing, service-window, and application-deadline terms are immutable after matching.")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> updateTicket(
            @Parameter(description = "Ticket ID", example = "42") @PathVariable Long id,
            @Valid @RequestBody TicketUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketService.updateTicket(id, request),
                "Ticket updated"));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Publish a draft ticket")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> publishTicket(
            @Parameter(description = "Ticket ID", example = "42") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketService.publishTicket(id),
                "Ticket published"));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(
            summary = "Close an open or matched ticket",
            description = "Only the author may close it. Closing rejects every remaining pending application; an effectively expired open ticket cannot be closed.")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> closeTicket(
            @Parameter(description = "Ticket ID", example = "42") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketService.closeTicket(id),
                "Ticket closed"));
    }
}
