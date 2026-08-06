package com.relix.marketplace.payment.webhook;

/** A deliberately detail-free exception so signatures and payload secrets never reach API errors. */
public class InvalidStripeWebhookException extends RuntimeException {

    public InvalidStripeWebhookException() {
        super("Invalid Stripe webhook signature or payload");
    }
}
