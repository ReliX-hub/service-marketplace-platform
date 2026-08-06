package com.relix.marketplace.ticket.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "How a ticket's transaction amount is determined")
public enum PricingMode {
    FIXED,
    BUDGET_RANGE,
    OPEN_BID
}
