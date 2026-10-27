package com.scentstation.identity.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Opt-in signed JWT; persistent auth_sessions still govern revocation. */
@Component
public class JwtSessionTokenCodec {
    private static final String ISSUER = "scentstation-identity";
    private final boolean enabled;
    private final byte[] key;

    public JwtSessionTokenCodec(@Value("${auth.jwt.enabled:false}") boolean enabled,
                                @Value("${auth.jwt.secret-base64:}") String secret) {
        this.enabled = enabled;
        if (!enabled) { this.key = null; return; }
        try { this.key = Base64.getDecoder().decode(secret); }
        catch (IllegalArgumentException ex) { throw new IllegalStateException("Invalid JWT signing key encoding", ex); }
        if (key.length < 32) throw new IllegalStateException("JWT key must be at least 32 random bytes");
    }

    public boolean enabled() { return enabled; }

    public String issue(String subject, Instant issuedAt, Instant expiresAt) {
        if (!enabled) throw new IllegalStateException("JWT mode disabled");
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER).subject(subject).jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(issuedAt)).expirationTime(Date.from(expiresAt)).build();
        SignedJWT signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try { signed.sign(new MACSigner(key)); return signed.serialize(); }
        catch (JOSEException ex) { throw new IllegalStateException("Could not issue JWT", ex); }
    }

    public boolean verify(String token, String userId, Instant now) {
        if (!enabled || token == null || token.length() > 8192) return false;
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) ||
                    !jwt.verify(new MACVerifier(key))) return false;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expires = claims.getExpirationTime();
            Date issued = claims.getIssueTime();
            return ISSUER.equals(claims.getIssuer()) && userId.equals(claims.getSubject())
                    && expires != null && expires.toInstant().isAfter(now)
                    && issued != null && !issued.toInstant().isAfter(now.plusSeconds(60));
        } catch (ParseException | JOSEException | RuntimeException ex) {
            return false;
        }
    }
}
