package com.relix.marketplace.payment.config;

import com.relix.marketplace.payment.gateway.MockPaymentGateway;
import com.relix.marketplace.payment.gateway.PaymentGateway;
import com.relix.marketplace.payment.gateway.StripePaymentGateway;
import com.stripe.StripeClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    PaymentGatewayConfiguration.class,
                    MockPaymentGateway.class,
                    StripePaymentGateway.class);

    @Test
    void defaultsToOfflineMockGateway() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PaymentGateway.class);
            assertThat(context).hasSingleBean(MockPaymentGateway.class);
            assertThat(context).doesNotHaveBean(StripePaymentGateway.class);
            assertThat(context).doesNotHaveBean(StripeClient.class);
        });
    }

    @Test
    void createsStripeGatewayOnlyWhenExplicitlyConfiguredWithTestCredentials() {
        contextRunner
                .withPropertyValues(
                        "payment.gateway=stripe",
                        "stripe.secret-key=sk_test_example",
                        "stripe.publishable-key=pk_test_example",
                        "stripe.webhook-secret=whsec_example")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PaymentGateway.class);
                    assertThat(context).hasSingleBean(StripePaymentGateway.class);
                    assertThat(context).hasSingleBean(StripeClient.class);
                    assertThat(context).doesNotHaveBean(MockPaymentGateway.class);
                });
    }

    @Test
    void rejectsLiveSecretKey() {
        contextRunner
                .withPropertyValues(
                        "payment.gateway=stripe",
                        "stripe.secret-key=sk_live_forbidden",
                        "stripe.publishable-key=pk_test_example",
                        "stripe.webhook-secret=whsec_example")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Live Stripe secret keys are not allowed");
                });
    }

    @Test
    void rejectsMissingOrNonTestStripeCredentials() {
        contextRunner
                .withPropertyValues(
                        "payment.gateway=stripe",
                        "stripe.secret-key=sk_test_example",
                        "stripe.publishable-key=pk_live_forbidden",
                        "stripe.webhook-secret=whsec_example")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage("Stripe publishable key must use the pk_test_ prefix");
                });
    }
}
