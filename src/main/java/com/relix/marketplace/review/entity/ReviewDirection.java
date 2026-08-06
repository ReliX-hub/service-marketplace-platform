package com.relix.marketplace.review.entity;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Direction of a participant-to-participant engagement review")
public enum ReviewDirection {
    CLIENT_TO_WORKER,
    WORKER_TO_CLIENT
}
