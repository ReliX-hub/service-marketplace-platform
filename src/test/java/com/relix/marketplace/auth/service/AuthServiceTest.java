package com.relix.marketplace.auth.service;

import com.relix.marketplace.auth.dto.AuthResponse;
import com.relix.marketplace.auth.dto.LoginRequest;
import com.relix.marketplace.auth.dto.RefreshRequest;
import com.relix.marketplace.auth.dto.RegisterRequest;
import com.relix.marketplace.auth.entity.RefreshToken;
import com.relix.marketplace.auth.repository.RefreshTokenRepository;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.user.entity.UserCapability;
import com.relix.marketplace.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private JwtService jwtService;
    @Mock private PasswordEncoder passwordEncoder;
    @InjectMocks private AuthService authService;

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("registers a USER with CLIENT and WORKER capabilities")
        void register_userWithDefaultCapabilities_success() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("Alice").email("alice@test.com").password("secret123").build();

            when(userRepository.existsByEmail("alice@test.com")).thenReturn(false);
            when(passwordEncoder.encode("secret123")).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenAnswer(inv -> {
                User u = inv.getArgument(0);
                u.setId(1L);
                return u;
            });
            when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
            when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh");
            when(jwtService.getAccessTokenExpiration()).thenReturn(7200L);
            when(jwtService.getRefreshTokenExpiration()).thenReturn(604800L);
            when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse response = authService.register(request);

            assertEquals("alice@test.com", response.getEmail());
            assertEquals("USER", response.getRole());
            assertEquals(List.of("CLIENT", "WORKER"), response.getCapabilities());
            assertEquals("access-token", response.getAccessToken());
            assertNotNull(response.getRefreshToken());
            assertEquals("Bearer", response.getTokenType());
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertEquals(Set.of(UserCapability.Type.CLIENT, UserCapability.Type.WORKER),
                    userCaptor.getValue().getCapabilityTypes());
        }

        @Test
        @DisplayName("rejects duplicate email")
        void register_duplicateEmail_throws() {
            RegisterRequest request = RegisterRequest.builder()
                    .name("X").email("dup@test.com").password("pass").build();

            when(userRepository.existsByEmail("dup@test.com")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request));
            assertEquals("EMAIL_EXISTS", ex.getCode());
        }

    }

    @Nested
    @DisplayName("login")
    class Login {

        @Test
        @DisplayName("logs in with correct credentials")
        void login_success() {
            User user = User.builder()
                    .email("a@test.com").passwordHash("hashed").name("A")
                    .role(User.UserRole.USER).status(User.UserStatus.ACTIVE).build();
            user.setId(1L);

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);
            when(jwtService.generateAccessToken(user)).thenReturn("tok");
            when(jwtService.hashToken(anyString())).thenReturn("h");
            when(jwtService.getAccessTokenExpiration()).thenReturn(7200L);
            when(jwtService.getRefreshTokenExpiration()).thenReturn(604800L);
            when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            AuthResponse resp = authService.login(LoginRequest.builder().email("a@test.com").password("pass").build());

            assertEquals("a@test.com", resp.getEmail());
            verify(refreshTokenRepository).revokeAllByUserId(1L);
        }

        @Test
        @DisplayName("rejects wrong password")
        void login_wrongPassword_throws() {
            User user = User.builder()
                    .email("a@test.com").passwordHash("hashed").name("A")
                    .role(User.UserRole.USER).status(User.UserStatus.ACTIVE).build();
            user.setId(1L);

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(LoginRequest.builder().email("a@test.com").password("wrong").build()));
            assertEquals("INVALID_CREDENTIALS", ex.getCode());
        }

        @Test
        @DisplayName("rejects non-existent email")
        void login_unknownEmail_throws() {
            when(userRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

            assertThrows(BusinessException.class,
                    () -> authService.login(LoginRequest.builder().email("unknown@test.com").password("x").build()));
        }

        @Test
        @DisplayName("rejects inactive account")
        void login_inactiveAccount_throws() {
            User user = User.builder()
                    .email("a@test.com").passwordHash("hashed").name("A")
                    .role(User.UserRole.USER).status(User.UserStatus.SUSPENDED).build();
            user.setId(1L);

            when(userRepository.findByEmail("a@test.com")).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("pass", "hashed")).thenReturn(true);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> authService.login(LoginRequest.builder().email("a@test.com").password("pass").build()));
            assertEquals("ACCOUNT_INACTIVE", ex.getCode());
        }
    }

    @Test
    @DisplayName("refresh rejects an account suspended after the token was issued")
    void refresh_inactiveAccount_throwsWithoutRotatingToken() {
        User user = User.builder()
                .email("suspended@test.com")
                .passwordHash("hashed")
                .name("Suspended")
                .role(User.UserRole.USER)
                .status(User.UserStatus.SUSPENDED)
                .build();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("stored-hash")
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();

        when(jwtService.hashToken("refresh-token")).thenReturn("stored-hash");
        when(refreshTokenRepository.findByTokenHash("stored-hash")).thenReturn(Optional.of(token));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.refresh(RefreshRequest.builder()
                        .refreshToken("refresh-token")
                        .build()));

        assertEquals("ACCOUNT_INACTIVE", exception.getCode());
        assertFalse(token.getRevoked());
        verify(refreshTokenRepository, never()).save(token);
    }
}
