package com.relix.marketplace.payment.gateway;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentGatewayContractTest {

    private final PaymentGateway gateway = new MockPaymentGateway();

    @Test
    void createsDeterministicOfflinePaymentIntent() {
        PaymentIntentRequest request = new PaymentIntentRequest(
                new BigDecimal("12.34"),
                "USD",
                Map.of("engagementId", "42"),
                "pay-engagement-42");

        PaymentIntentResult first = gateway.createIntent(request);
        PaymentIntentResult replay = gateway.createIntent(request);

        assertThat(first).isEqualTo(replay);
        assertThat(first.providerRef()).startsWith("pi_mock_");
        assertThat(first.clientSecret()).isEqualTo(first.providerRef() + "_secret_mock");
        assertThat(first.status()).isEqualTo("succeeded");
        assertThat(gateway.retrieveIntent(first.providerRef())).isEqualTo(first);
        assertThat(gateway.webhookDriven()).isFalse();
    }

    @Test
    void createsDeterministicOfflineRefund() {
        RefundRequest request = new RefundRequest(
                "pi_mock_existing",
                new BigDecimal("12.34"),
                "usd",
                Map.of("marketplaceRefundId", "7"),
                "refund-engagement-42");

        RefundResult first = gateway.refund(request);
        RefundResult replay = gateway.refund(request);

        assertThat(first).isEqualTo(replay);
        assertThat(first.providerRef()).startsWith("re_mock_");
        assertThat(first.status()).isEqualTo("succeeded");
        assertThat(first.amount()).isEqualTo(1234L);
        assertThat(first.currency()).isEqualTo("usd");
        assertThat(first.paymentIntentId()).isEqualTo("pi_mock_existing");
        assertThat(gateway.retrieveRefund(first.providerRef())).isEqualTo(first);
    }

    @Test
    void rejectsPrecisionThatCannotBeRepresentedInMinorUnits() {
        PaymentIntentRequest request = new PaymentIntentRequest(
                new BigDecimal("1.001"), "USD", Map.of(), "precision-test");

        assertThatThrownBy(() -> gateway.createIntent(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fractional digits");
    }

    @Test
    void observesZeroDecimalCurrencies() {
        assertThat(gateway.createIntent(new PaymentIntentRequest(
                new BigDecimal("100"), "JPY", Map.of(), "jpy-valid")))
                .isNotNull();

        assertThatThrownBy(() -> gateway.createIntent(new PaymentIntentRequest(
                new BigDecimal("100.5"), "JPY", Map.of(), "jpy-invalid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0 fractional digits");
    }

    @Test
    void requiresCallerSuppliedIdempotencyKeys() {
        assertThatThrownBy(() -> gateway.createIntent(new PaymentIntentRequest(
                BigDecimal.ONE, "USD", Map.of(), " ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Idempotency key");
    }

    @Test
    void requiresProviderReferenceWhenRetrievingIntent() {
        assertThatThrownBy(() -> gateway.retrieveIntent(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider reference");
    }

    @Test
    void requiresProviderReferenceWhenRetrievingRefund() {
        assertThatThrownBy(() -> gateway.retrieveRefund(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider reference");
    }
}
