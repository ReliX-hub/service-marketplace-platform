package com.relix.marketplace.application.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Application lifecycle")
public enum ApplicationStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    WITHDRAWN,
    EXPIRED
}
