package com.relix.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.application.entity.ApplicationStatus;
import com.relix.marketplace.application.repository.ApplicationRepository;
import com.relix.marketplace.catalog.repository.CategoryRepository;
import com.relix.marketplace.engagement.repository.EngagementRepository;
import com.relix.marketplace.ticket.entity.TicketStatus;
import com.relix.marketplace.ticket.repository.TicketRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ApplicationRepository applicationRepository;

    @Autowired
    private EngagementRepository engagementRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String baseUrl;
    private Long categoryId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
        categoryId = categoryRepository.findByCode("HOME_CLEANING").orElseThrow().getId();
    }

    @Test
    @DisplayName("Concurrent accepts create exactly one accepted application and one engagement")
    void concurrentAcceptsAreSerializedAndDatabaseConstrained() throws Exception {
        String unique = UUID.randomUUID().toString().replace("-", "");
        String authorToken = register("Ticket Author", "author-" + unique + "@test.com");
        String firstWorkerToken = register("First Worker", "worker-one-" + unique + "@test.com");
        String secondWorkerToken = register("Second Worker", "worker-two-" + unique + "@test.com");

        Long ticketId = createAndPublishRequest(authorToken, "ConcurrentAccept" + unique);
        Long firstApplicationId = apply(ticketId, firstWorkerToken, "95.00");
        Long secondApplicationId = apply(ticketId, secondWorkerToken, "95.00");

        assertPartialUniqueAcceptedIndexExists();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();

        try {
            Future<HttpResponse<String>> first = executor.submit(acceptTask(
                    httpClient,
                    firstApplicationId,
                    authorToken,
                    ready,
                    start));
            Future<HttpResponse<String>> second = executor.submit(acceptTask(
                    httpClient,
                    secondApplicationId,
                    authorToken,
                    ready,
                    start));

            assertThat(ready.await(5, TimeUnit.SECONDS))
                    .as("both accept requests reached the start gate")
                    .isTrue();
            start.countDown();

            List<HttpResponse<String>> responses = List.of(
                    first.get(20, TimeUnit.SECONDS),
                    second.get(20, TimeUnit.SECONDS));

            assertThat(responses)
                    .extracting(HttpResponse::statusCode)
                    .containsExactlyInAnyOrder(
                            HttpStatus.OK.value(),
                            HttpStatus.CONFLICT.value());

            HttpResponse<String> conflict = responses.stream()
                    .filter(response -> response.statusCode() == HttpStatus.CONFLICT.value())
                    .findFirst()
                    .orElseThrow();
            assertThat(objectMapper.readTree(conflict.body()).path("error").path("code").textValue())
                    .isIn("TICKET_NOT_OPEN", "APPLICATION_NOT_PENDING");
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS))
                    .as("concurrent accept executor terminated")
                    .isTrue();
        }

        List<ApplicationStatus> finalStatuses = List.of(
                applicationRepository.findById(firstApplicationId).orElseThrow().getStatus(),
                applicationRepository.findById(secondApplicationId).orElseThrow().getStatus());
        assertThat(finalStatuses).containsExactlyInAnyOrder(
                ApplicationStatus.ACCEPTED,
                ApplicationStatus.REJECTED);

        long engagementCount = List.of(firstApplicationId, secondApplicationId).stream()
                .filter(applicationId -> engagementRepository.findByApplication_Id(applicationId).isPresent())
                .count();
        assertThat(engagementCount).isEqualTo(1L);
        assertThat(ticketRepository.findById(ticketId).orElseThrow().getStatus())
                .isEqualTo(TicketStatus.MATCHED);
    }

    private Callable<HttpResponse<String>> acceptTask(
            HttpClient httpClient,
            Long applicationId,
            String authorToken,
            CountDownLatch ready,
            CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent accept start");
            }

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/applications/" + applicationId + "/accept"))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + authorToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        };
    }

    private void assertPartialUniqueAcceptedIndexExists() {
        String indexDefinition = jdbcTemplate.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                String.class,
                "uk_applications_one_accepted_per_ticket");

        assertThat(indexDefinition)
                .containsIgnoringCase("CREATE UNIQUE INDEX")
                .containsIgnoringCase("ticket_id")
                .containsIgnoringCase("WHERE")
                .containsIgnoringCase("ACCEPTED");
    }

    private String register(String name, String email) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/auth/register",
                HttpMethod.POST,
                Map.of("name", name, "email", email, "password", "password123"),
                null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return data(response).path("accessToken").textValue();
    }

    private Long createAndPublishRequest(String token, String title) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kind", "REQUEST");
        request.put("categoryId", categoryId);
        request.put("title", title);
        request.put("description", "Two workers will race to accept matching");
        request.put("pricingMode", "FIXED");
        request.put("price", "95.00");
        request.put("currency", "USD");
        request.put("locationMode", "REMOTE");

        ResponseEntity<String> created = exchange(
                "/api/tickets", HttpMethod.POST, request, token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long ticketId = data(created).path("id").longValue();

        ResponseEntity<String> published = exchange(
                "/api/tickets/" + ticketId + "/publish",
                HttpMethod.POST,
                null,
                token);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ticketId;
    }

    private Long apply(Long ticketId, String token, String amount) throws Exception {
        ResponseEntity<String> response = exchange(
                "/api/tickets/" + ticketId + "/applications",
                HttpMethod.POST,
                Map.of("proposedAmount", amount, "message", "Concurrent applicant"),
                token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return data(response).path("id").longValue();
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
        assertThat(response.getBody()).isNotNull();
        return objectMapper.readTree(response.getBody()).path("data");
    }
}
