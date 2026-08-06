package com.relix.marketplace;

import com.relix.marketplace.auth.dto.AuthResponse;
import com.relix.marketplace.auth.dto.LoginRequest;
import com.relix.marketplace.auth.dto.RefreshRequest;
import com.relix.marketplace.auth.dto.RegisterRequest;
import com.relix.marketplace.common.dto.ApiResponse;
import com.relix.marketplace.worker.repository.WorkerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuthIntegrationTest extends BaseIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WorkerProfileRepository workerProfileRepository;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;
    }

    @Test
    @DisplayName("Registration returns access and refresh tokens")
    void registration_returnsTokens() {
        String email = "reg-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("Test User").email(email).password("password123").build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AuthResponse auth = response.getBody().getData();
        assertNotNull(auth.getAccessToken());
        assertNotNull(auth.getRefreshToken());
        assertEquals("Bearer", auth.getTokenType());
        assertEquals("USER", auth.getRole());
        assertTrue(auth.getCapabilities().containsAll(java.util.List.of("CLIENT", "WORKER")));
        assertEquals(email, auth.getEmail());
    }

    @Test
    @DisplayName("Duplicate email registration is rejected")
    void duplicateEmail_isRejected() {
        String email = "dup-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("Test").email(email).password("password123").build();

        // First registration
        restTemplate.exchange(baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        // Duplicate
        ResponseEntity<String> dupResponse = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, dupResponse.getStatusCode());
    }

    @Test
    @DisplayName("Login with correct credentials succeeds")
    void login_withCorrectCredentials_succeeds() {
        String email = "login-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Login Test").email(email).password("password123").build();
        restTemplate.exchange(baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        LoginRequest loginReq = LoginRequest.builder().email(email).password("password123").build();
        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(loginReq), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody().getData().getAccessToken());
    }

    @Test
    @DisplayName("Login with wrong password fails")
    void login_withWrongPassword_fails() {
        String email = "wrongpw-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Test").email(email).password("password123").build();
        restTemplate.exchange(baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<ApiResponse<AuthResponse>>() {});

        LoginRequest loginReq = LoginRequest.builder().email(email).password("wrongpassword").build();
        ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(loginReq), String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Token refresh returns new tokens and invalidates old refresh token")
    void tokenRefresh_returnsNewTokens() {
        String email = "refresh-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Refresh Test").email(email).password("password123").build();

        ResponseEntity<ApiResponse<AuthResponse>> regResp = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<>() {});
        String refreshToken = regResp.getBody().getData().getRefreshToken();

        // Refresh token
        RefreshRequest refreshReq = RefreshRequest.builder().refreshToken(refreshToken).build();
        ResponseEntity<ApiResponse<AuthResponse>> refreshResp = restTemplate.exchange(
                baseUrl + "/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(refreshReq), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, refreshResp.getStatusCode());
        AuthResponse newAuth = refreshResp.getBody().getData();
        assertNotNull(newAuth.getAccessToken());
        assertNotNull(newAuth.getRefreshToken());
        assertNotEquals(refreshToken, newAuth.getRefreshToken()); // new refresh token

        // Old refresh token should be revoked
        ResponseEntity<String> reuseResp = restTemplate.exchange(
                baseUrl + "/api/auth/refresh", HttpMethod.POST,
                new HttpEntity<>(refreshReq), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, reuseResp.getStatusCode());
    }

    @Test
    @DisplayName("Unauthenticated access to protected endpoints returns 401")
    void unauthenticatedAccess_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/api/engagements", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    @DisplayName("Registration grants both capabilities without eagerly creating a worker profile")
    void registration_grantsCapabilitiesWithoutEagerProfile() {
        String email = "worker-reg-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest request = RegisterRequest.builder()
                .name("WorkerProfile Test").email(email).password("password123").build();

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        AuthResponse auth = response.getBody().getData();
        assertEquals("USER", auth.getRole());
        assertTrue(auth.getCapabilities().containsAll(java.util.List.of("CLIENT", "WORKER")));
        assertFalse(workerProfileRepository.existsByUser_Id(auth.getUserId()));
    }

    @Test
    @DisplayName("Registration cannot self-assign the ADMIN role")
    void registration_cannotSelfAssignAdmin() {
        Map<String, Object> request = Map.of(
                "name", "Admin Hack",
                "email", "admin-hack-" + System.currentTimeMillis() + "@test.com",
                "password", "password123",
                "role", "ADMIN");

        ResponseEntity<ApiResponse<AuthResponse>> response = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("USER", response.getBody().getData().getRole());
    }

    @Test
    @DisplayName("Get current user with valid token succeeds")
    void getCurrentUser_withValidToken() {
        String email = "me-" + System.currentTimeMillis() + "@test.com";
        RegisterRequest regReq = RegisterRequest.builder()
                .name("Me Test").email(email).password("password123").build();
        ResponseEntity<ApiResponse<AuthResponse>> regResp = restTemplate.exchange(
                baseUrl + "/api/auth/register", HttpMethod.POST,
                new HttpEntity<>(regReq), new ParameterizedTypeReference<>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(regResp.getBody().getData().getAccessToken());
        ResponseEntity<ApiResponse<Map<String, Object>>> meResp = restTemplate.exchange(
                baseUrl + "/api/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<>() {});

        assertEquals(HttpStatus.OK, meResp.getStatusCode());
        assertEquals(email, meResp.getBody().getData().get("email"));
        assertEquals("Me Test", meResp.getBody().getData().get("name"));
    }
}
