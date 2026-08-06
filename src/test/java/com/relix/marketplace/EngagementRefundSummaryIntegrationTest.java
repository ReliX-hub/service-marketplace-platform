package com.relix.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.refund.entity.Refund;
import com.relix.marketplace.refund.repository.RefundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EngagementRefundSummaryIntegrationTest extends BaseIntegrationTest {

    private static final String AMOUNT = "75.00";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private RefundRepository refundRepository;

    private String baseUrl;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        categoryId = categoryRepository.findByCode("HOME_CLEANING").orElseThrow().getId();
    }

    @Test
    @DisplayName("Engagement detail and lists expose the same safe refund summary to both participants")
    void refundSummaryIsVisibleToBothParticipantsWithoutWeakeningOwnership() throws Exception {
        String unique = unique();
        Actor client = register("Refund Client", "refund-client-" + unique + "@test.com");
        Actor worker = register("Refund Worker", "refund-worker-" + unique + "@test.com");
        Actor intruder = register("Refund Intruder", "refund-intruder-" + unique + "@test.com");
        Long engagementId = createAcceptedRequest(client, worker, unique);

        // An accepted engagement has no refund. Keep the explicit null in the contract so
        // generated clients do not have to distinguish an absent property from no refund.
        assertNoRefundSummary(getEngagement(engagementId, client.token()));
        assertNoRefundSummary(getEngagement(engagementId, worker.token()));

        ResponseEntity<String> paid = exchange(
                "/api/engagements/" + engagementId + "/pay",
                HttpMethod.POST,
                Map.of("requestId", "refund-summary-pay-" + unique),
                client.token());
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(paid).path("status").textValue()).isEqualTo("SUCCEEDED");

        ResponseEntity<String> cancelled = exchange(
                "/api/engagements/" + engagementId + "/cancel",
                HttpMethod.POST,
                Map.of("reason", "Client schedule changed"),
                client.token());
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(cancelled).path("status").textValue()).isEqualTo("CANCELLED");
        assertRefundSummary(data(cancelled), "COMPLETED", null);

        assertRefundSummary(getEngagement(engagementId, client.token()), "COMPLETED", null);
        assertRefundSummary(getEngagement(engagementId, worker.token()), "COMPLETED", null);
        assertRefundSummary(
                engagementFromList(engagementId, "client", client.token()),
                "COMPLETED",
                null);
        assertRefundSummary(
                engagementFromList(engagementId, "worker", worker.token()),
                "COMPLETED",
                null);

        ResponseEntity<String> forbidden = exchange(
                "/api/engagements/" + engagementId,
                HttpMethod.GET,
                null,
                intruder.token());
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(json(forbidden).path("error").path("code").textValue())
                .isEqualTo("ENGAGEMENT_PARTICIPANT_REQUIRED");

        Refund refund = refundRepository.findFirstByEngagement_IdOrderByCreatedAtAsc(engagementId)
                .orElseThrow();
        refund.setStatus(Refund.RefundStatus.PENDING);
        refund.setFailureMessage(null);
        refund.setRefundedAt(null);
        refundRepository.saveAndFlush(refund);
        assertRefundSummary(getEngagement(engagementId, client.token()), "PENDING", null);

        refund.setStatus(Refund.RefundStatus.FAILED);
        refund.setFailureMessage("Provider rejected the refund");
        refundRepository.saveAndFlush(refund);
        assertRefundSummary(
                getEngagement(engagementId, worker.token()),
                "FAILED",
                "Provider rejected the refund");
    }

    @Test
    @DisplayName("OpenAPI models the engagement refund summary as a nullable reference")
    void openApiDeclaresRefundSummaryNullableWithoutLosingItsSchema() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/v3/api-docs", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode schema = json(response)
                .path("components")
                .path("schemas")
                .path("EngagementResponse")
                .path("properties")
                .path("refundSummary");
        assertThat(schema.path("nullable").booleanValue()).isTrue();
        assertThat(schema.path("allOf").isArray()).isTrue();
        assertThat(schema.path("allOf")).hasSize(1);
        assertThat(schema.path("allOf").path(0).path("$ref").textValue())
                .isEqualTo("#/components/schemas/RefundSummaryResponse");
        assertThat(schema.has("oneOf")).isFalse();
    }

    private Long createAcceptedRequest(Actor client, Actor worker, String unique) throws Exception {
        Map<String, Object> ticket = new LinkedHashMap<>();
        ticket.put("kind", "REQUEST");
        ticket.put("categoryId", categoryId);
        ticket.put("title", "RefundSummary" + unique);
        ticket.put("description", "Integration fixture for the engagement refund summary contract");
        ticket.put("pricingMode", "FIXED");
        ticket.put("price", AMOUNT);
        ticket.put("currency", "USD");
        ticket.put("locationMode", "REMOTE");

        ResponseEntity<String> created = exchange(
                "/api/tickets", HttpMethod.POST, ticket, client.token());
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ticketId = data(created).path("id").longValue();

        ResponseEntity<String> published = exchange(
                "/api/tickets/" + ticketId + "/publish",
                HttpMethod.POST,
                null,
                client.token());
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> applied = exchange(
                "/api/tickets/" + ticketId + "/applications",
                HttpMethod.POST,
                Map.of("proposedAmount", AMOUNT, "message", "Refund summary test application"),
                worker.token());
        assertThat(applied.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long applicationId = data(applied).path("id").longValue();

        ResponseEntity<String> accepted = exchange(
                "/api/applications/" + applicationId + "/accept",
                HttpMethod.POST,
                null,
                client.token());
        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(accepted).path("engagementId").longValue();
    }

    private Actor register(String name, String email) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/auth/register",
                HttpMethod.POST,
                Map.of("name", name, "email", email, "password", "password123"),
                null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode auth = data(response);
        return new Actor(auth.path("userId").longValue(), auth.path("accessToken").textValue());
    }

    private JsonNode getEngagement(Long engagementId, String token) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/engagements/" + engagementId,
                HttpMethod.GET,
                null,
                token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return data(response);
    }

    private JsonNode engagementFromList(Long engagementId, String role, String token)
            throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/engagements?role=" + role + "&page=0&size=100",
                HttpMethod.GET,
                null,
                token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        for (JsonNode engagement : data(response).path("items")) {
            if (engagement.path("id").longValue() == engagementId) {
                return engagement;
            }
        }
        throw new AssertionError("Engagement " + engagementId + " was absent from the " + role + " list");
    }

    private void assertNoRefundSummary(JsonNode engagement) {
        assertThat(engagement.has("refundSummary")).isTrue();
        assertThat(engagement.path("refundSummary").isNull()).isTrue();
    }

    private void assertRefundSummary(
            JsonNode engagement,
            String expectedStatus,
            String expectedFailureMessage) {
        JsonNode summary = engagement.path("refundSummary");
        assertThat(summary.isObject()).isTrue();
        assertThat(summary.path("id").canConvertToLong()).isTrue();
        assertThat(summary.path("amount").isTextual()).isTrue();
        assertThat(summary.path("amount").textValue()).isEqualTo(AMOUNT);
        assertThat(summary.path("status").textValue()).isEqualTo(expectedStatus);
        assertThat(summary.has("providerRefundId")).isFalse();
        assertThat(summary.has("providerStatus")).isFalse();
        if (expectedFailureMessage == null) {
            assertThat(summary.path("failureMessage").isNull()).isTrue();
        } else {
            assertThat(summary.path("failureMessage").textValue()).isEqualTo(expectedFailureMessage);
        }
        if ("COMPLETED".equals(expectedStatus)) {
            assertThat(summary.path("refundedAt").isTextual()).isTrue();
        } else {
            assertThat(summary.path("refundedAt").isNull()).isTrue();
        }
        assertThat(summary.path("updatedAt").isTextual()).isTrue();
    }

    private ResponseEntity<String> exchange(
            String path,
            HttpMethod method,
            Object body,
            String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(
                baseUrl + path,
                method,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        return json(response).path("data");
    }

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody()).isNotNull();
        return objectMapper.readTree(response.getBody());
    }

    private String unique() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Actor(Long userId, String token) {
    }
}
