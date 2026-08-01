package com.modularbank.transfers;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import java.time.Instant;
import java.util.UUID;

/**
 * Auth lives in the monolith, not in transfers-service, so these tests cannot
 * call a real /auth/login. They mint tokens with the same shared secret the
 * running service validates against (application.yml default).
 */
final class TestJwtSupport {

    private static final String SECRET = "modular-bank-dev-secret-change-in-production-256bit";

    private TestJwtSupport() {
    }

    static String tokenFor(UUID userId) {
        Instant now = Instant.now();
        return JWT.create()
            .withSubject(userId.toString())
            .withIssuedAt(now)
            .withExpiresAt(now.plusSeconds(900))
            .sign(Algorithm.HMAC256(SECRET));
    }
}
