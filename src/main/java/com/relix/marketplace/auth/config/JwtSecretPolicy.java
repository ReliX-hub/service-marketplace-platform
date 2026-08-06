package com.relix.marketplace.auth.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSecretPolicy {

    static final String DEVELOPMENT_SECRET =
            "dGhpcyBpcyBhIHZlcnkgbG9uZyBzZWNyZXQga2V5IGZvciBqd3QgdG9rZW4gZ2VuZXJhdGlvbiB0aGF0IGlzIGF0IGxlYXN0IDI1NiBiaXRz";
    private static final int MINIMUM_KEY_BYTES = 32;
    private static final Set<String> DEVELOPMENT_PROFILES = Set.of("dev", "test");

    private final Environment environment;

    @Value("${jwt.secret}")
    private String configuredSecret;

    @PostConstruct
    void validateConfiguredSecret() {
        boolean developmentOnly = isDevelopmentOnly(environment.getActiveProfiles());
        validate(configuredSecret, developmentOnly);
        if (developmentOnly && DEVELOPMENT_SECRET.equals(configuredSecret)) {
            log.warn("Using the repository's development-only JWT secret; never expose this profile publicly");
        }
    }

    static boolean isDevelopmentOnly(String... activeProfiles) {
        return activeProfiles != null
                && activeProfiles.length > 0
                && Arrays.stream(activeProfiles)
                .allMatch(DEVELOPMENT_PROFILES::contains);
    }

    static void validate(String encodedSecret, boolean developmentProfile) {
        if (encodedSecret == null || encodedSecret.isBlank()) {
            throw new IllegalStateException("JWT secret is required");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT secret must be valid Base64", exception);
        }

        if (decoded.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("JWT secret must decode to at least 32 bytes");
        }
        byte[] developmentKey = Base64.getDecoder().decode(DEVELOPMENT_SECRET);
        if (!developmentProfile && MessageDigest.isEqual(decoded, developmentKey)) {
            throw new IllegalStateException("The development JWT secret is not allowed outside dev or test");
        }
    }
}
