package com.relix.marketplace.auth.config;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtSecretPolicyTest {

    @Test
    void acceptsStrongBase64Secret() {
        String secret = Base64.getEncoder().encodeToString(
                "a-random-enough-jwt-secret-with-more-than-32-bytes".getBytes(StandardCharsets.UTF_8));

        assertDoesNotThrow(() -> JwtSecretPolicy.validate(secret, false));
    }

    @Test
    void rejectsMalformedBase64() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtSecretPolicy.validate("not-base64!", false));

        assertEquals("JWT secret must be valid Base64", exception.getMessage());
    }

    @Test
    void rejectsShortDecodedSecret() {
        String secret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtSecretPolicy.validate(secret, false));

        assertEquals("JWT secret must decode to at least 32 bytes", exception.getMessage());
    }

    @Test
    void developmentSecretIsLimitedToDevelopmentProfiles() {
        assertDoesNotThrow(() -> JwtSecretPolicy.validate(JwtSecretPolicy.DEVELOPMENT_SECRET, true));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> JwtSecretPolicy.validate(JwtSecretPolicy.DEVELOPMENT_SECRET, false));

        assertEquals("The development JWT secret is not allowed outside dev or test", exception.getMessage());
    }

    @Test
    void publicKeyIsAllowedOnlyWhenEveryActiveProfileIsDevelopmentOnly() {
        assertTrue(JwtSecretPolicy.isDevelopmentOnly("dev"));
        assertTrue(JwtSecretPolicy.isDevelopmentOnly("test"));
        assertTrue(JwtSecretPolicy.isDevelopmentOnly("dev", "test"));

        assertFalse(JwtSecretPolicy.isDevelopmentOnly());
        assertFalse(JwtSecretPolicy.isDevelopmentOnly("docker"));
        assertFalse(JwtSecretPolicy.isDevelopmentOnly("dev", "docker"));
        assertFalse(JwtSecretPolicy.isDevelopmentOnly("DEV"));
        assertFalse(JwtSecretPolicy.isDevelopmentOnly("Dev"));
        assertThrows(
                IllegalStateException.class,
                () -> JwtSecretPolicy.validate(
                        JwtSecretPolicy.DEVELOPMENT_SECRET,
                        JwtSecretPolicy.isDevelopmentOnly("dev", "docker")));
        assertThrows(
                IllegalStateException.class,
                () -> JwtSecretPolicy.validate(
                        JwtSecretPolicy.DEVELOPMENT_SECRET,
                        JwtSecretPolicy.isDevelopmentOnly("DEV")));
    }
}
