package com.relix.marketplace.payment.gateway;

public interface PaymentGateway {

    /**
     * Whether successful local API calls still require a signed provider webhook
     * before marketplace state can advance.
     */
    boolean webhookDriven();

    PaymentIntentResult createIntent(PaymentIntentRequest request);

    /**
     * Reload provider state for an already-created payment intent. This allows
     * an owning client to recover a transient client secret without persisting
     * that secret in the marketplace database.
     */
    PaymentIntentResult retrieveIntent(String providerRef);

    RefundResult refund(RefundRequest request);

    /**
     * Reload the provider's canonical refund state before retrying a failed local attempt.
     */
    RefundResult retrieveRefund(String providerRef);
}
