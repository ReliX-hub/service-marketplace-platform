package com.relix.marketplace.ticket.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.common.dto.PageResponse;
import com.relix.marketplace.ticket.dto.TicketDetailResponse;
import com.relix.marketplace.ticket.dto.TicketSummaryResponse;
import com.relix.marketplace.ticket.entity.TicketKind;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.service.TicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/tickets")
@RequiredArgsConstructor
@Tag(name = "My Tickets", description = "Authenticated user's ticket workspace")
@SecurityRequirement(name = "bearerAuth")
public class MeTicketController {

    private final TicketService ticketService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List tickets authored by the current user")
    public ResponseEntity<ApiResponse<PageResponse<TicketSummaryResponse>>> getMyTickets(
            @RequestParam(required = false) TicketKind kind,
            @RequestParam(required = false) TicketStatus status,
            @Parameter(example = "0") @RequestParam(defaultValue = "0") Integer page,
            @Parameter(example = "20") @RequestParam(defaultValue = "20") Integer size,
            @Parameter(example = "createdAt,desc")
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return ResponseEntity.ok(ApiResponse.success(
                ticketService.getMyTickets(kind, status, page, size, sort)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
            summary = "Get one of the current user's tickets",
            description = "Returns the author's private detail for every status without incrementing the public view counter.")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> getMyTicket(
            @Parameter(description = "Ticket ID", example = "42") @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.getMyTicket(id)));
    }
}
