package com.scentstation.identity.service;

import com.scentstation.identity.entity.User;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.rbac.RolePermissions;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final SecretKey key;
    private final JwtParser parser;
    private final long expirationMs;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(java.util.Base64.getDecoder().decode(secret));
        this.parser = Jwts.parser().verifyWith(key).build();
        this.expirationMs = expirationMs;
    }

    public String generateToken(User user) {
        Date now = new Date();
        Role role = user.isPendingApproval() ? Role.STAFF : user.getRole();
        Set<Role> roles = user.isPendingApproval() ? Set.of(Role.STAFF)
            : RolePermissions.effectiveRoles(user.getRoles(), role);
        return Jwts.builder().subject(user.getUsername())
            .claim("userId", user.getId())
            .claim("role", role.name()) // legacy client compatibility
            .claim("roles", roles.stream().map(Enum::name).sorted().toList())
            .claim("tenantId", user.getTenantId())
            .claim("tokenVersion", user.getTokenVersion())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMs))
            .signWith(key).compact();
    }

    public Session readSession(String token) {
        var claims = parser.parseSignedClaims(token).getPayload();
        Object userId = claims.get("userId");
        Object version = claims.get("tokenVersion");
        return new Session(claims.getSubject(), userId instanceof Number id ? id.longValue() : -1L,
            version instanceof Number n ? n.longValue() : -1L);
    }

    public record Session(String username, long userId, long tokenVersion) {}
}
