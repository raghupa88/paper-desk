package com.paperdesk.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the fail-fast secret guard added for production deploys — a missing,
 * short, or still-default JWT secret must abort startup with a clear message
 * rather than silently signing tokens with a weak or well-known key.
 */
class JwtServiceTest {

    private static final String DEV_DEFAULT_SECRET =
            "dev-only-secret-change-me-0123456789-0123456789-0123456789";
    private static final String REAL_SECRET =
            "a-real-32-plus-byte-secret-not-the-dev-default-value";

    @Test
    void nullSecretRejected() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService(null, 1, new MockEnvironment()));
    }

    @Test
    void shortSecretRejected() {
        assertThrows(IllegalStateException.class,
                () -> new JwtService("too-short", 1, new MockEnvironment()));
    }

    @Test
    void devDefaultSecretRejectedUnderProdProfile() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new JwtService(DEV_DEFAULT_SECRET, 1, prodEnv));
    }

    @Test
    void devDefaultSecretAllowedWithoutProdProfile() {
        assertDoesNotThrow(() -> new JwtService(DEV_DEFAULT_SECRET, 1, new MockEnvironment()));
    }

    @Test
    void realSecretAllowedUnderProdProfile() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        assertDoesNotThrow(() -> new JwtService(REAL_SECRET, 1, prodEnv));
    }
}
