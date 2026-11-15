package com.scentstation.identity.service;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private String secret;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        secret = Base64.getEncoder().encodeToString(new byte[32]);
        jwtService = new JwtService(secret, 60_000);
    }

    @Test
    void staffJwtCarriesIdentityRoleAndTenantWithoutCredentialData() {
        String token = jwtService.generateStaffToken(
                "staff-id", "demo.a", "tenant-id",
                List.of("OPERATIONS_MANAGER"));

        assertEquals("STAFF", jwtService.extractPrincipalType(token));
        assertEquals("staff-id", jwtService.extractPrincipalId(token));
        assertEquals("demo.a", jwtService.extractUsername(token));
        assertEquals("tenant-id", jwtService.extractTenantId(token));
        assertEquals(List.of("OPERATIONS_MANAGER"), jwtService.extractRoles(token));

        SecretKey key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
        var claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        assertFalse(claims.containsKey("password"));
        assertFalse(claims.containsKey("password_hash"));
    }

    @Test
    void customerJwtRetainsRoleAndHasDistinctPrincipalType() {
        User user = new User(
                "customer", "customer@example.com", "bcrypt-hash", "Customer", Role.CUSTOMER);

        String token = jwtService.generateToken(user);

        assertEquals("CUSTOMER", jwtService.extractPrincipalType(token));
        assertEquals("customer", jwtService.extractUsername(token));
        assertEquals("CUSTOMER", jwtService.extractRole(token));
    }

    @Test
    void malformedOrTamperedJwtIsRejected() {
        String token = jwtService.generateStaffToken(
                "staff-id", "demo.a", "tenant-id", List.of("VIEWER"));

        assertThrows(RuntimeException.class,
                () -> jwtService.extractUsername(token + "tampered"));
        assertThrows(RuntimeException.class,
                () -> jwtService.extractUsername("not-a-jwt"));
    }

    @Test
    void expiredJwtIsRejected() {
        JwtService expiredService = new JwtService(secret, -1);
        String token = expiredService.generateStaffToken(
                "staff-id", "demo.a", "tenant-id", List.of("VIEWER"));

        assertThrows(RuntimeException.class,
                () -> expiredService.extractUsername(token));
    }
}
