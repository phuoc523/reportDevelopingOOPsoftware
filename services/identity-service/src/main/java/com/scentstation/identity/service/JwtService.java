package com.scentstation.identity.service;

import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.RolePermissions;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        return Jwts.builder().subject(user.getUsername())
            .claim("role", user.getRole().name()) // legacy client compatibility
            .claim("roles", RolePermissions.effectiveRoles(user.getRoles(), user.getRole()).stream()
                .map(Enum::name).sorted().toList())
            .claim("tenantId", user.getTenantId())
            .claim("tokenVersion", user.getTokenVersion())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(key).compact();
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public String extractUsername(String token) { return claims(token).getSubject(); }
    public String extractRole(String token) { return claims(token).get("role", String.class); }
    public long extractTokenVersion(String token) {
        Object version = claims(token).get("tokenVersion");
        return version instanceof Number n ? n.longValue() : -1L;
    }
}
