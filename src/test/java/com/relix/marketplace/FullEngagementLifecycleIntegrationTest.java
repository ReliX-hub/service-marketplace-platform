package com.relix.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.settlement.repository.SettlementRepository;
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

class FullEngagementLifecycleIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    private String baseUrl;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        categoryId = categoryRepository.findByCode("HOME_CLEANING").orElseThrow().getId();
    }

    @Test
    @DisplayName("Worker OFFER flows through matching, mock escrow, settlement, and bidirectional reviews")
    void offerLifecycleCompletesEndToEnd() throws Exception {
        String unique = unique();
        Actor worker = register("Offer Worker", "offer-worker-" + unique + "@test.com");
        Actor client = register("Offer Client", "offer-client-" + unique + "@test.com");

        Long ticketId = createAndPublishFixedTicket(
                "OFFER",
                "OfferLifecycle" + unique,
                "40.00",
                worker.token());
        Long applicationId = apply(ticketId, "40.00", client.token());
        Long engagementId = accept(applicationId, worker.token());

        completeAndReview(
                engagementId,
                client,
                worker,
                "40.00",
                5,
                4);
    }

    @Test
    @DisplayName("Client REQUEST flows through competitive matching and the same engagement lifecycle")
    void requestLifecycleCompletesEndToEnd() throws Exception {
        String unique = unique();
        Actor client = register("Request Client", "request-client-" + unique + "@test.com");
        Actor worker = register("Request Worker", "request-worker-" + unique + "@test.com");

        Long ticketId = createAndPublishBudgetTicket(
                "RequestLifecycle" + unique,
                client.token());
        Long applicationId = apply(ticketId, "125.00", worker.token());
        Long engagementId = accept(applicationId, client.token());

        completeAndReview(
                engagementId,
                client,
                worker,
                "125.00",
                4,
                5);
    }

    private void completeAndReview(
            Long engagementId,
            Actor client,
            Actor worker,
            String expectedAmount,
            int workerRating,
            int clientRating) throws Exception {
        ResponseEntity<String> payment = exchange(
                "/api/engagements/" + engagementId + "/pay",
                HttpMethod.POST,
                Map.of("requestId", "pay-" + unique()),
                client.token());
        assertThat(payment.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode paymentData = data(payment);
        assertThat(paymentData.path("status").textValue()).isEqualTo("SUCCEEDED");
        assertThat(paymentData.path("amount").isTextual()).isTrue();
        assertThat(paymentData.path("amount").textValue()).isEqualTo(expectedAmount);
        assertThat(paymentData.has("clientSecret")).isFalse();

        ResponseEntity<String> funded = exchange(
                "/api/engagements/" + engagementId,
                HttpMethod.GET,
                null,
                client.token());
        assertThat(data(funded).path("status").textValue()).isEqualTo("FUNDED");

        ResponseEntity<String> started = action(engagementId, "start", worker.token());
        assertThat(data(started).path("status").textValue()).isEqualTo("IN_PROGRESS");

        ResponseEntity<String> delivered = action(engagementId, "deliver", worker.token());
        assertThat(data(delivered).path("status").textValue()).isEqualTo("DELIVERED");

        ResponseEntity<String> approved = action(engagementId, "approve", client.token());
        JsonNode approvedData = data(approved);
        assertThat(approvedData.path("status").textValue()).isEqualTo("COMPLETED");
        assertThat(approvedData.path("amount").isTextual()).isTrue();
        assertThat(approvedData.path("amount").textValue()).isEqualTo(expectedAmount);
        Long workerId = approvedData.path("workerId").longValue();
        assertThat(settlementRepository.existsByEngagementId(engagementId)).isTrue();

        ResponseEntity<String> clientReview = exchange(
                "/api/engagements/" + engagementId + "/reviews",
                HttpMethod.POST,
                Map.of("rating", workerRating, "comment", "Excellent service delivery"),
                client.token());
        assertThat(clientReview.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(clientReview).path("direction").textValue()).isEqualTo("CLIENT_TO_WORKER");

        ResponseEntity<String> workerReview = exchange(
                "/api/engagements/" + engagementId + "/reviews",
                HttpMethod.POST,
                Map.of("rating", clientRating, "comment", "Clear requirements and communication"),
                worker.token());
        assertThat(workerReview.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(workerReview).path("direction").textValue()).isEqualTo("WORKER_TO_CLIENT");

        ResponseEntity<String> workerReviews = restTemplate.getForEntity(
                baseUrl + "/api/workers/" + workerId + "/reviews",
                String.class);
        assertThat(workerReviews.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(workerReviews).path("items")).hasSize(1);
        assertThat(data(workerReviews).path("items").get(0).path("rating").intValue())
                .isEqualTo(workerRating);

        ResponseEntity<String> clientReviews = restTemplate.getForEntity(
                baseUrl + "/api/users/" + client.userId() + "/reviews",
                String.class);
        assertThat(clientReviews.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(clientReviews).path("items")).hasSize(1);
        assertThat(data(clientReviews).path("items").get(0).path("rating").intValue())
                .isEqualTo(clientRating);

        ResponseEntity<String> workerProfile = restTemplate.getForEntity(
                baseUrl + "/api/workers/" + workerId,
                String.class);
        assertThat(workerProfile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(workerProfile).path("reviewCount").intValue()).isEqualTo(1);
        assertThat(data(workerProfile).path("completedJobs").intValue()).isEqualTo(1);
        assertThat(data(workerProfile).path("rating").isTextual()).isTrue();

        ResponseEntity<String> clientProfile = exchange(
                "/api/auth/me",
                HttpMethod.GET,
                null,
                client.token());
        assertThat(data(clientProfile).path("clientReviewCount").intValue()).isEqualTo(1);
        assertThat(data(clientProfile).path("clientRating").isTextual()).isTrue();

        ResponseEntity<String> duplicateReview = exchange(
                "/api/engagements/" + engagementId + "/reviews",
                HttpMethod.POST,
                Map.of("rating", workerRating),
                client.token());
        assertThat(duplicateReview.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(json(duplicateReview).path("error").path("code").textValue())
                .isEqualTo("REVIEW_ALREADY_SUBMITTED");
    }

    private Long createAndPublishFixedTicket(
            String kind,
            String title,
            String price,
            String token) throws Exception {
        Map<String, Object> body = baseTicket(kind, title, "FIXED");
        body.put("price", price);
        return createAndPublish(body, token);
    }

    private Long createAndPublishBudgetTicket(String title, String token) throws Exception {
        Map<String, Object> body = baseTicket("REQUEST", title, "BUDGET_RANGE");
        body.put("budgetMin", "100.00");
        body.put("budgetMax", "150.00");
        return createAndPublish(body, token);
    }

    private Map<String, Object> baseTicket(String kind, String title, String pricingMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("categoryId", categoryId);
        body.put("title", title);
        body.put("description", "Created by the full lifecycle integration test");
        body.put("pricingMode", pricingMode);
        body.put("currency", "USD");
        body.put("locationMode", "REMOTE");
        return body;
    }

    private Long createAndPublish(Map<String, Object> body, String token) throws Exception {
        ResponseEntity<String> created = exchange(
                "/api/tickets", HttpMethod.POST, body, token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ticketId = data(created).path("id").longValue();

        ResponseEntity<String> published = exchange(
                "/api/tickets/" + ticketId + "/publish",
                HttpMethod.POST,
                null,
                token);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(published).path("status").textValue()).isEqualTo("OPEN");
        return ticketId;
    }

    private Long apply(Long ticketId, String amount, String token) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/tickets/" + ticketId + "/applications",
                HttpMethod.POST,
                Map.of("proposedAmount", amount, "message", "Lifecycle test application"),
                token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(data(response).path("status").textValue()).isEqualTo("PENDING");
        return data(response).path("id").longValue();
    }

    private Long accept(Long applicationId, String token) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/applications/" + applicationId + "/accept",
                HttpMethod.POST,
                null,
                token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(response).path("status").textValue()).isEqualTo("ACCEPTED");
        return data(response).path("engagementId").longValue();
    }

    private ResponseEntity<String> action(Long engagementId, String action, String token) {
        ResponseEntity<String> response = exchange(
                "/api/engagements/" + engagementId + "/" + action,
                HttpMethod.POST,
                null,
                token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response;
    }

    private Actor register(String name, String email) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/auth/register",
                HttpMethod.POST,
                Map.of("name", name, "email", email, "password", "password123"),
                null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode auth = data(response);
        assertThat(auth.path("capabilities")).extracting(JsonNode::textValue)
                .containsExactlyInAnyOrder("CLIENT", "WORKER");
        return new Actor(auth.path("userId").longValue(), auth.path("accessToken").textValue());
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
