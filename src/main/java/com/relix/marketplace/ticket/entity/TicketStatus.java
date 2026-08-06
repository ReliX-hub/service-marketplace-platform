package com.relix.marketplace.ticket.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Ticket publication and matching lifecycle")
public enum TicketStatus {
    DRAFT,
    OPEN,
    MATCHED,
    CLOSED,
    CANCELLED,
    EXPIRED
}
