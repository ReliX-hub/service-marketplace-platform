package com.relix.marketplace.payment.webhook;

public record WebhookProcessingResult(
        String eventId,
        String eventType,
        Status status) {

    public enum Status {
        PROCESSED,
        DUPLICATE
    }

    public boolean duplicate() {
        return status == Status.DUPLICATE;
    }
}
