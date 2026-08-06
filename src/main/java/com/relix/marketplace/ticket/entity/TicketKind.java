package com.relix.marketplace.ticket.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Marketplace listing direction: a worker offer or a client request")
public enum TicketKind {
    OFFER,
    REQUEST
}
