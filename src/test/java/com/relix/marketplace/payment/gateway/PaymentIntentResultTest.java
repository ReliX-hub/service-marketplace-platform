package com.relix.marketplace.payment.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentIntentResultTest {

    @Test
    void redactsClientSecretFromDiagnosticString() {
        PaymentIntentResult result = new PaymentIntentResult(
                "pi_test",
                "pi_test_secret_transient",
                "requires_confirmation");

        assertThat(result.toString())
                .contains("pi_test", "requires_confirmation", "<redacted>")
                .doesNotContain("pi_test_secret_transient");
    }
}
