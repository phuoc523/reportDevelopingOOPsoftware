package com.scentstation.identity.service;

import com.scentstation.identity.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final Key key;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        byte[] bytes = java.util.Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder().subject(user.getUsername()).claim("role", user.getRole().name())
            .issuedAt(now).expiration(new Date(now.getTime() + expirationMs)).signWith(key).compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
            .parseSignedClaims(token).getPayload().getSubject();
    }

    public String extractRole(String token) {
        return Jwts.parser().verifyWith((javax.crypto.SecretKey) key).build()
            .parseSignedClaims(token).getPayload().get("role", String.class);
    }
}
