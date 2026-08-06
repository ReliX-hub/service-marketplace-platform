package com.relix.marketplace.ticket.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Where the service can be performed")
public enum LocationMode {
    ON_SITE,
    REMOTE,
    HYBRID
}
