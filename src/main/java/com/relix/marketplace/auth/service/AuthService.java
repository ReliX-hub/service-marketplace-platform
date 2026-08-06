package com.relix.marketplace.auth.service;

import com.relix.marketplace.auth.dto.AuthResponse;
import com.relix.marketplace.auth.dto.LoginRequest;
import com.relix.marketplace.auth.dto.RefreshRequest;
import com.relix.marketplace.auth.dto.RegisterRequest;
import com.relix.marketplace.auth.entity.RefreshToken;
import com.relix.marketplace.auth.repository.RefreshTokenRepository;
import com.relix.marketplace.common.exception.BusinessException;
import com.relix.marketplace.common.exception.ResourceNotFoundException;
import com.relix.marketplace.user.entity.User;
import com.relix.marketplace.user.entity.UserCapability;
import com.relix.marketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessException("Email already registered", "EMAIL_EXISTS");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .role(User.UserRole.USER)
                .status(User.UserStatus.ACTIVE)
                .build();

        user.grantCapability(UserCapability.Type.CLIENT);
        user.grantCapability(UserCapability.Type.WORKER);

        user = userRepository.save(user);

        log.info("User registered: id={}, email={}", user.getId(), user.getEmail());

        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException("Invalid email or password", "INVALID_CREDENTIALS"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("Invalid email or password", "INVALID_CREDENTIALS");
        }

        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("Account is not active", "ACCOUNT_INACTIVE");
        }

        refreshTokenRepository.revokeAllByUserId(user.getId());

        log.info("User logged in: id={}, email={}", user.getId(), user.getEmail());
        return generateAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = jwtService.hashToken(request.getRefreshToken());

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException("Invalid refresh token", "INVALID_TOKEN"));

        if (!refreshToken.isValid()) {
            throw new BusinessException("Refresh token expired or revoked", "TOKEN_EXPIRED");
        }

        User user = refreshToken.getUser();
        if (user.getStatus() != User.UserStatus.ACTIVE) {
            throw new BusinessException("Account is not active", "ACCOUNT_INACTIVE");
        }

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        log.info("Token refreshed for user: id={}", user.getId());
        return generateAuthResponse(user);
    }

    public User getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.info("User logged out: id={}", userId);
    }

    private AuthResponse generateAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = generateRefreshToken(user);

        return AuthResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .capabilities(user.getCapabilityTypes().stream()
                        .map(UserCapability.Type::name)
                        .sorted()
                        .toList())
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration())
                .build();
    }

    private String generateRefreshToken(User user) {
        String tokenStr = UUID.randomUUID().toString();
        String tokenHash = jwtService.hashToken(tokenStr);
        Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenExpiration());

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .expiresAt(expiresAt)
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return tokenStr;
    }
}
