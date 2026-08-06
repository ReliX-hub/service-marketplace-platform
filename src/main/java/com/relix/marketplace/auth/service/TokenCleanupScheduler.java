package com.relix.marketplace.auth.service;

import com.relix.marketplace.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupScheduler {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * Clean up expired and old revoked refresh tokens daily at 3:00 AM CST.
     * Revoked tokens older than 7 days are also removed.
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "America/Chicago")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            Instant now = Instant.now();
            Instant revokedCutoff = now.minus(7, ChronoUnit.DAYS);

            int deleted = refreshTokenRepository.deleteExpiredOrOldRevokedTokens(now, revokedCutoff);
            log.info("Cleaned up {} expired/revoked refresh tokens", deleted);
        } catch (Exception e) {
            log.warn("Failed to clean up expired refresh tokens: {}", e.getMessage());
        }
    }
}
