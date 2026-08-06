package com.relix.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relix.marketplace.catalog.repository.CategoryRepository;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TicketBoardIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    private String baseUrl;
    private Long categoryId;
    private Long ticketId;
    private String ticketTitle;
    private Map<String, Object> ticketRequest;

    @BeforeEach
    void setUp() throws Exception {
        baseUrl = "http://localhost:" + port;
        categoryId = categoryRepository.findByCode("HOME_CLEANING").orElseThrow().getId();
        String unique = UUID.randomUUID().toString().replace("-", "");
        ticketTitle = "BoardPrice" + unique;
        ticketRequest = fixedRequestTicket(ticketTitle);

        String token = register("board-" + unique + "@test.com");
        ResponseEntity<String> created = exchange(
                "/api/tickets",
                HttpMethod.POST,
                ticketRequest,
                token);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        ticketId = json(created).path("data").path("id").longValue();

        ResponseEntity<String> published = exchange(
                "/api/tickets/" + ticketId + "/publish",
                HttpMethod.POST,
                null,
                token);
        assertThat(published.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(published).path("data").path("status").textValue()).isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Anonymous users can browse the ticket board and frontend metadata")
    void anonymousReadsExposeStableFrontendContracts() throws Exception {
        ResponseEntity<String> board = restTemplate.getForEntity(
                baseUrl + "/api/tickets?q=" + ticketTitle + "&page=0&size=10",
                String.class);
        assertThat(board.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode page = json(board).path("data");
        assertThat(page.path("page").intValue()).isZero();
        assertThat(page.path("size").intValue()).isEqualTo(10);
        assertThat(page.path("totalElements").longValue()).isEqualTo(1L);
        assertThat(page.path("totalPages").intValue()).isEqualTo(1);
        assertThat(page.path("hasNext").booleanValue()).isFalse();
        assertThat(page.path("items")).hasSize(1);

        JsonNode summary = page.path("items").get(0);
        assertThat(summary.path("id").longValue()).isEqualTo(ticketId);
        assertThat(summary.path("price").isTextual()).isTrue();
        assertThat(summary.path("price").textValue()).isEqualTo("45.00");
        assertThat(summary.path("author").path("name").textValue()).isNotBlank();
        assertThat(summary.path("category").path("code").textValue()).isEqualTo("HOME_CLEANING");

        ResponseEntity<String> detailResponse = restTemplate.getForEntity(
                baseUrl + "/api/tickets/" + ticketId,
                String.class);
        assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode detail = json(detailResponse).path("data");
        assertThat(detail.path("price").isTextual()).isTrue();
        assertThat(detail.path("price").textValue()).isEqualTo("45.00");
        assertThat(detail.path("category").path("name").textValue()).isNotBlank();

        ResponseEntity<String> categories = restTemplate.getForEntity(
                baseUrl + "/api/categories",
                String.class);
        assertThat(categories.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(categories).path("data").isArray()).isTrue();
        assertThat(json(categories).path("data")).isNotEmpty();

        ResponseEntity<String> metadata = restTemplate.getForEntity(
                baseUrl + "/api/meta/enums",
                String.class);
        assertThat(metadata.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json(metadata).path("data").path("ticketKind").get(0).path("value").textValue())
                .isEqualTo("OFFER");
        assertThat(json(metadata).path("data").path("engagementStatus")).hasSize(8);
    }

    @Test
    @DisplayName("Anonymous access never leaks ticket mutation or application endpoints")
    void anonymousWritesAndNestedApplicationReadsRemainProtected() throws Exception {
        // JDK HttpClient avoids HttpURLConnection's streaming-body retry failure
        // when Spring Security answers an anonymous POST with an auth challenge.
        HttpRequest createRequest = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tickets"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();
        HttpResponse<String> create = HttpClient.newHttpClient().send(
                createRequest,
                HttpResponse.BodyHandlers.ofString());
        assertThat(create.statusCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());

        ResponseEntity<String> applications = restTemplate.getForEntity(
                baseUrl + "/api/tickets/" + ticketId + "/applications",
                String.class);
        assertThat(applications.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String register(String email) throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Ticket Board User",
                "email", email,
                "password", "password123");
        ResponseEntity<String> response = exchange(
                "/api/auth/register",
                HttpMethod.POST,
                body,
                null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json(response).path("data").path("accessToken").textValue();
    }

    private Map<String, Object> fixedRequestTicket(String title) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("kind", "REQUEST");
        request.put("categoryId", categoryId);
        request.put("title", title);
        request.put("description", "Integration-test ticket for the public board");
        request.put("pricingMode", "FIXED");
        request.put("price", "45.00");
        request.put("currency", "USD");
        request.put("locationMode", "REMOTE");
        return request;
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

    private JsonNode json(ResponseEntity<String> response) throws Exception {
        assertThat(response.getBody()).isNotNull();
        return objectMapper.readTree(response.getBody());
    }
}
