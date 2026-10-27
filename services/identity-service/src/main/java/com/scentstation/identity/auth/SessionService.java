package com.scentstation.identity.auth;

import com.scentstation.identity.config.AuthProperties;
import com.scentstation.identity.model.AuthSession;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

@Service
public class SessionService {
    private final AuthSessionRepository sessions;
    private final TokenService tokens;
    private final JwtSessionTokenCodec jwt;
    private final AuthProperties properties;
    private final Clock clock;

    public SessionService(AuthSessionRepository sessions, TokenService tokens,
                          JwtSessionTokenCodec jwt, AuthProperties properties, Clock clock) {
        this.sessions = sessions;
        this.tokens = tokens;
        this.jwt = jwt;
        this.properties = properties;
        this.clock = clock;
    }

    /** Must be invoked within the caller's login transaction. */
    public AuthDto.LoginResponse issue(StaffUser user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getSessionTtlMinutes(), ChronoUnit.MINUTES);
        String raw = jwt.enabled() ? jwt.issue(user.getId(), now, expiresAt) : tokens.newToken();
        sessions.save(new AuthSession(tokens.hash(raw), user, now, expiresAt));
        return new AuthDto.LoginResponse(raw, "Bearer", expiresAt, identityFor(user));
    }

    @Transactional(readOnly = true)
    public AuthDto.Identity resolve(String rawToken) {
        if (!isWellFormedToken(rawToken)) return null;
        AuthSession session = sessions.findByTokenHash(tokens.hash(rawToken)).orElse(null);
        if (session == null || session.getRevokedAt() != null || !session.getExpiresAt().isAfter(clock.instant())) {
            return null;
        }
        StaffUser user = session.getUser();
        if (!user.isEnabled() || user.isManuallyLocked()
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(clock.instant()))
                || (user.getTenant() != null && !user.getTenant().isActive())) {
            return null;
        }
        // Opaque tokens rely on the random token hash; JWTs require signature and claims verification.
        if (rawToken.indexOf('.') >= 0 && !jwt.verify(rawToken, user.getId(), clock.instant())) return null;
        return identityFor(user);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (!isWellFormedToken(rawToken)) return;
        sessions.findByTokenHash(tokens.hash(rawToken)).ifPresent(session -> {
            if (session.getRevokedAt() == null) session.setRevokedAt(clock.instant());
        });
    }

    /** For integration with P1-02/P1-04; call in the same transaction as account changes. */
    @Transactional
    public int revokeAllForUser(String userId) {
        return sessions.revokeAllForUser(userId, clock.instant());
    }

    private boolean isWellFormedToken(String token) {
        if (token == null) return false;
        if (token.length() == 43 && token.matches("[A-Za-z0-9_-]{43}")) return true;
        return token.length() <= 8192 && token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    public static AuthDto.Identity identityFor(StaffUser user) {
        Set<String> roles = Set.copyOf(user.getRoles());
        return new AuthDto.Identity(user.getId(), user.getUsername(),
                user.getTenant() == null ? null : user.getTenant().getId(), roles);
    }
}
