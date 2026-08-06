package com.relix.marketplace.engagement.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Escrow-backed service engagement lifecycle")
public enum EngagementStatus {
    ACCEPTED,
    FUNDED,
    IN_PROGRESS,
    DELIVERED,
    COMPLETED,
    DISPUTED,
    CANCELLED,
    REFUNDED
}
