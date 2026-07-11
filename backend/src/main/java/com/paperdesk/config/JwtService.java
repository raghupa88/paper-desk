package com.paperdesk.config;

import com.paperdesk.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

@Service
public class JwtService {

    private static final String DEV_DEFAULT_SECRET =
            "dev-only-secret-change-me-0123456789-0123456789-0123456789";
    private static final int MIN_SECRET_BYTES = 32; // HS256 requires a 256-bit key

    private final SecretKey key;
    private final Duration ttl;

    public JwtService(@Value("${paperdesk.jwt.secret}") String secret,
                      @Value("${paperdesk.jwt.ttl-hours}") long ttlHours,
                      Environment environment) {
        validateSecret(secret, environment);
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * Fails startup loudly rather than letting a missing/weak/dev-default secret
     * reach production silently (Keys.hmacShaKeyFor's WeakKeyException is accurate
     * but unhelpful for an operator to act on quickly).
     */
    private static void validateSecret(String secret, Environment environment) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "paperdesk.jwt.secret is missing or shorter than " + MIN_SECRET_BYTES +
                " bytes (HS256 requires a 256-bit key). Set PAPERDESK_JWT_SECRET.");
        }
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd && secret.equals(DEV_DEFAULT_SECRET)) {
            throw new IllegalStateException(
                "paperdesk.jwt.secret is still the dev default under the 'prod' profile. " +
                "Set PAPERDESK_JWT_SECRET to a real secret before deploying.");
        }
    }

    public String issue(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.id))
                .claim("email", user.email)
                .claim("role", user.role.name())
                .claim("name", user.displayName)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** Returns the claims if valid, else throws. Subject is the user id. */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
