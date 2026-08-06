package com.relix.marketplace.payment.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void omitsAbsentClientSecretWithoutChangingOtherNullableFields() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .clientSecret(null)
                .providerStatus(null)
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.has("clientSecret")).isFalse();
        assertThat(json.has("providerStatus")).isTrue();
        assertThat(json.get("providerStatus").isNull()).isTrue();
    }

    @Test
    void includesTransientClientSecretWhenPresent() throws Exception {
        PaymentResponse response = PaymentResponse.builder()
                .paymentId(1L)
                .clientSecret("pi_test_secret_transient")
                .build();

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(response));

        assertThat(json.path("clientSecret").textValue()).isEqualTo("pi_test_secret_transient");
        assertThat(response.toString()).doesNotContain("pi_test_secret_transient");
    }
}
