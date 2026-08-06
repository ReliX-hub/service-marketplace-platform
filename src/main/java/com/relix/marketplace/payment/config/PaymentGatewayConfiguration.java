package com.relix.marketplace.payment.config;

import com.stripe.StripeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PaymentProperties.class, StripeProperties.class})
@Slf4j
public class PaymentGatewayConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "payment.gateway",
            havingValue = "mock",
            matchIfMissing = true)
    public ApplicationRunner mockGatewayNotice() {
        return args -> log.warn(
                "Payment gateway is MOCK; no external charges will be created. "
                        + "Set PAYMENT_GATEWAY=stripe and configure Stripe test credentials to use Stripe.");
    }

    @Bean
    @ConditionalOnProperty(name = "payment.gateway", havingValue = "stripe")
    public StripeClient stripeClient(StripeProperties properties) {
        validateStripeTestCredentials(properties);
        return new StripeClient(properties.getSecretKey().trim());
    }

    static void validateStripeTestCredentials(StripeProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("Stripe configuration is required when payment.gateway=stripe");
        }

        List<String> configuredValues = List.of(
                valueOrEmpty(properties.getSecretKey()),
                valueOrEmpty(properties.getPublishableKey()),
                valueOrEmpty(properties.getWebhookSecret()));
        if (configuredValues.stream().map(String::trim).anyMatch(value -> value.startsWith("sk_live_"))) {
            throw new IllegalStateException("Live Stripe secret keys are not allowed");
        }

        requirePrefix(properties.getSecretKey(), "sk_test_", "Stripe secret key");
        requirePrefix(properties.getPublishableKey(), "pk_test_", "Stripe publishable key");
        requirePrefix(properties.getWebhookSecret(), "whsec_", "Stripe webhook secret");
    }

    private static void requirePrefix(String value, String prefix, String label) {
        if (value == null || !value.trim().startsWith(prefix)) {
            throw new IllegalStateException(label + " must use the " + prefix + " prefix");
        }
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
