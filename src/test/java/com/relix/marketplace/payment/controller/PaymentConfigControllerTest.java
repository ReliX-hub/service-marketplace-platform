package com.relix.marketplace.payment.controller;

import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.payment.config.PaymentProperties;
import com.relix.marketplace.payment.config.StripeProperties;
import com.relix.marketplace.payment.dto.PaymentConfigResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfigControllerTest {

    @Test
    void mockConfigNeverExposesStripeSecretsOrPublishableKey() {
        PaymentProperties payment = new PaymentProperties();
        StripeProperties stripe = stripeProperties();

        ResponseEntity<ApiResponse<PaymentConfigResponse>> response =
                new PaymentConfigController(payment, stripe).getPaymentConfig();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData().gateway()).isEqualTo("mock");
        assertThat(response.getBody().getData().publishableKey()).isNull();
        assertThat(response.toString()).doesNotContain("sk_test_private", "whsec_private");
    }

    @Test
    void stripeConfigExposesOnlyPublishableKey() {
        PaymentProperties payment = new PaymentProperties();
        payment.setGateway("stripe");
        StripeProperties stripe = stripeProperties();

        PaymentConfigResponse config = new PaymentConfigController(payment, stripe)
                .getPaymentConfig()
                .getBody()
                .getData();

        assertThat(config.gateway()).isEqualTo("stripe");
        assertThat(config.publishableKey()).isEqualTo("pk_test_public");
        assertThat(config.toString()).doesNotContain("sk_test_private", "whsec_private");
    }

    private StripeProperties stripeProperties() {
        StripeProperties stripe = new StripeProperties();
        stripe.setSecretKey("sk_test_private");
        stripe.setPublishableKey("pk_test_public");
        stripe.setWebhookSecret("whsec_private");
        return stripe;
    }
}
