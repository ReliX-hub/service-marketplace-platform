package com.relix.marketplace.payment.webhook;

import com.relix.marketplace.payment.webhook.entity.WebhookEvent;
import com.relix.marketplace.payment.webhook.repository.WebhookEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WebhookEventRepositoryDataJpaTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("webhook_repository_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private WebhookEventRepository webhookEventRepository;

    @Test
    void nativeInsertIsIdempotentAndProcessedTimestampIsOneWay() {
        String payload = "{\"id\":\"evt_repository\",\"type\":\"payment_intent.succeeded\"}";

        int first = webhookEventRepository.insertIfAbsent(
                "STRIPE", "evt_repository", "payment_intent.succeeded", payload);
        int duplicate = webhookEventRepository.insertIfAbsent(
                "STRIPE", "evt_repository", "payment_intent.succeeded", payload);

        assertThat(first).isEqualTo(1);
        assertThat(duplicate).isZero();
        WebhookEvent inserted = webhookEventRepository
                .findByProviderAndEventId("STRIPE", "evt_repository")
                .orElseThrow();
        assertThat(inserted.getProcessedAt()).isNull();
        assertThat(inserted.getPayload().path("id").asText()).isEqualTo("evt_repository");

        Instant processedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        assertThat(webhookEventRepository.markProcessed(
                "STRIPE", "evt_repository", processedAt)).isEqualTo(1);
        assertThat(webhookEventRepository.markProcessed(
                "STRIPE", "evt_repository", processedAt.plusSeconds(1))).isZero();

        WebhookEvent processed = webhookEventRepository
                .findByProviderAndEventId("STRIPE", "evt_repository")
                .orElseThrow();
        assertThat(processed.getProcessedAt()).isEqualTo(processedAt);
    }
}
