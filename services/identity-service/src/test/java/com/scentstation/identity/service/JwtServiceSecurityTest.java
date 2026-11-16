package com.scentstation.identity.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtServiceSecurityTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(
        "identity-security-test-signing-key-2026".getBytes(StandardCharsets.UTF_8));

    @Test
    void missingTokenVersionFailsClosed() {
        String token = Jwts.builder().subject("legacy")
            .expiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)))
            .compact();

        assertEquals(-1L, new JwtService(SECRET, 60_000).extractTokenVersion(token));
    }
}
