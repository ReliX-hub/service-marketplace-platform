package com.relix.marketplace.payment.webhook;

/**
 * Signals that Stripe should retry. This remains a generic server failure at the HTTP boundary.
 */
public class RetryableWebhookException extends RuntimeException {

    public RetryableWebhookException(String message) {
        super(message);
    }

    public RetryableWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
