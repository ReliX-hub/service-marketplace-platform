package com.relix.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.auth.dto.AuthResponse;
import com.relix.marketplace.auth.dto.RegisterRequest;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.common.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the USER/ADMIN role split and marketplace capabilities.
 */
class CapabilityBasedAccessIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    private String baseUrl;
    private AuthResponse userAuth;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        String unique = UUID.randomUUID().toString();

        userAuth = register("cap-user-" + unique + "@test.com");
    }

    private AuthResponse register(String email) {
        RegisterRequest req = RegisterRequest.builder()
                .name("Capability User").email(email).password("password123").build();

        ResponseEntity<ApiResponse<AuthResponse>> resp = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(req), new ParameterizedTypeReference<>() {});
        return resp.getBody().getData();
    }

    private HttpHeaders headers(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @DisplayName("Registration grants CLIENT and WORKER capabilities")
    void registration_grantsBothCapabilities() {
        assertEquals("USER", userAuth.getRole());
        assertTrue(userAuth.getCapabilities().containsAll(java.util.List.of("CLIENT", "WORKER")));
    }

    @Test
    @DisplayName("Regular USER cannot access admin settlement endpoints")
    void user_cannotAccessAdminEndpoints() {
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/api/admin/settlements/batch",
                HttpMethod.POST, new HttpEntity<>(headers(userAuth.getAccessToken())), String.class);
        assertTrue(resp.getStatusCode() == HttpStatus.FORBIDDEN
                || resp.getStatusCode() == HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("WORKER capability permits profile creation")
    void workerCapability_permitsProfileCreation() {
        Map<String, Object> body = Map.of("displayName", "Capability Worker");
        ResponseEntity<String> resp = restTemplate.exchange(
                baseUrl + "/api/workers/profile",
                HttpMethod.POST, new HttpEntity<>(body, headers(userAuth.getAccessToken())), String.class);
        assertEquals(HttpStatus.OK, resp.getStatusCode());
    }

    @Test
    @DisplayName("Public endpoints are accessible without token")
    void publicEndpoints_areAccessible() {
        // Actuator health
        ResponseEntity<String> healthResp = restTemplate.getForEntity(
                baseUrl + "/actuator/health", String.class);
        assertEquals(HttpStatus.OK, healthResp.getStatusCode());

        // Swagger UI (might redirect)
        ResponseEntity<String> swaggerResp = restTemplate.getForEntity(
                baseUrl + "/v3/api-docs", String.class);
        assertEquals(HttpStatus.OK, swaggerResp.getStatusCode());

        ResponseEntity<String> workersResp = restTemplate.getForEntity(
                baseUrl + "/api/workers", String.class);
        assertEquals(HttpStatus.OK, workersResp.getStatusCode());
    }

    @Test
    @DisplayName("Ticket and engagement operations enforce resource ownership")
    void resourcesEnforceAuthorClientWorkerAndParticipantOwnership() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        AuthResponse applicant = register("cap-worker-" + unique + "@test.com");
        AuthResponse intruder = register("cap-intruder-" + unique + "@test.com");
        Long categoryId = categoryRepository.findByCode("HOME_CLEANING").orElseThrow().getId();

        Map<String, Object> ticketBody = new LinkedHashMap<>();
        ticketBody.put("kind", "REQUEST");
        ticketBody.put("categoryId", categoryId);
        ticketBody.put("title", "Ownership" + unique);
        ticketBody.put("pricingMode", "FIXED");
        ticketBody.put("price", "80.00");
        ticketBody.put("currency", "USD");
        ticketBody.put("locationMode", "REMOTE");

        ResponseEntity<String> createTicket = exchange(
                "/api/tickets", HttpMethod.POST, ticketBody, userAuth.getAccessToken());
        assertEquals(HttpStatus.CREATED, createTicket.getStatusCode());
        Long ticketId = data(createTicket).path("id").longValue();

        ResponseEntity<String> publish = exchange(
                "/api/tickets/" + ticketId + "/publish",
                HttpMethod.POST,
                null,
                userAuth.getAccessToken());
        assertEquals(HttpStatus.OK, publish.getStatusCode());

        ResponseEntity<String> apply = exchange(
                "/api/tickets/" + ticketId + "/applications",
                HttpMethod.POST,
                Map.of("proposedAmount", "80.00", "message", "I can do this"),
                applicant.getAccessToken());
        assertEquals(HttpStatus.CREATED, apply.getStatusCode());
        Long applicationId = data(apply).path("id").longValue();

        assertEquals(HttpStatus.FORBIDDEN, exchange(
                "/api/tickets/" + ticketId + "/applications",
                HttpMethod.GET,
                null,
                intruder.getAccessToken()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(
                "/api/applications/" + applicationId + "/accept",
                HttpMethod.POST,
                null,
                intruder.getAccessToken()).getStatusCode());

        ResponseEntity<String> accept = exchange(
                "/api/applications/" + applicationId + "/accept",
                HttpMethod.POST,
                null,
                userAuth.getAccessToken());
        assertEquals(HttpStatus.OK, accept.getStatusCode());
        Long engagementId = data(accept).path("engagementId").longValue();

        assertEquals(HttpStatus.FORBIDDEN, exchange(
                "/api/engagements/" + engagementId,
                HttpMethod.GET,
                null,
                intruder.getAccessToken()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(
                "/api/engagements/" + engagementId + "/pay",
                HttpMethod.POST,
                Map.of("requestId", "wrong-client-" + unique),
                applicant.getAccessToken()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, exchange(
                "/api/engagements/" + engagementId + "/start",
                HttpMethod.POST,
                null,
                userAuth.getAccessToken()).getStatusCode());
    }

    private ResponseEntity<String> exchange(
            String path,
            HttpMethod method,
            Object body,
            String token) {
        return restTemplate.exchange(
                baseUrl + path,
                method,
                new HttpEntity<>(body, headers(token)),
                String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        return objectMapper.readTree(response.getBody()).path("data");
    }
}
