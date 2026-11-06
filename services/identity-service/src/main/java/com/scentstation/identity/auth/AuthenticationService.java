package com.scentstation.identity.auth;

import com.scentstation.identity.config.AuthProperties;
import com.scentstation.identity.model.LoginAttempt;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.repository.LoginAttemptRepository;
import com.scentstation.identity.repository.StaffUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class AuthenticationService {
    private final StaffUserRepository users;
    private final LoginAttemptRepository attempts;
    private final SessionService sessions;
    private final PasswordEncoder encoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthenticationService(StaffUserRepository users, LoginAttemptRepository attempts,
                                 SessionService sessions, PasswordEncoder encoder,
                                 AuthProperties properties, Clock clock) {
        this.users = users;
        this.attempts = attempts;
        this.sessions = sessions;
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = encoder.encode("unmatched-dummy-password");
    }

    /**
     * Return a failure outcome rather than throwing inside this transaction:
     * failed-attempt counters and audit records MUST commit even for HTTP 401/423.
     */
    @Transactional
    public LoginOutcome login(AuthDto.LoginRequest request) {
        String username = request.username().strip().toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        StaffUser user = users.findByUsernameForLogin(username).orElse(null);
        if (user == null) {
            // Equalize password verification work to limit username probing.
            encoder.matches(request.password(), dummyPasswordHash);
            recordAttempt(username, null, "INVALID_CREDENTIALS", now);
            return LoginOutcome.fail("INVALID_CREDENTIALS", 401);
        }
        if (!user.isEnabled()) {
            recordAttempt(username, user, "ACCOUNT_DISABLED", now);
            return LoginOutcome.fail("INVALID_CREDENTIALS", 401);
        }
        if (user.isManuallyLocked()) {
            recordAttempt(username, user, "ACCOUNT_LOCKED", now);
            return LoginOutcome.fail("ACCOUNT_LOCKED", 423);
        }
        if (user.getTenant() != null && !user.getTenant().isActive()) {
            recordAttempt(username, user, "TENANT_SUSPENDED", now);
            return LoginOutcome.fail("INVALID_CREDENTIALS", 401);
        }
        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                recordAttempt(username, user, "ACCOUNT_LOCKED", now);
                return LoginOutcome.fail("ACCOUNT_LOCKED", 423);
            }
            user.setLockedUntil(null);
            user.setFailedAttempts(0);
        }
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            user.setLastFailedLoginAt(now);
            int count = user.getFailedAttempts() + 1;
            user.setFailedAttempts(count);
            if (count >= properties.getMaxFailedAttempts()) {
                user.setLockedUntil(now.plus(properties.getLockMinutes(), ChronoUnit.MINUTES));
                recordAttempt(username, user, "ACCOUNT_LOCKED", now);
                return LoginOutcome.fail("ACCOUNT_LOCKED", 423);
            }
            recordAttempt(username, user, "INVALID_CREDENTIALS", now);
            return LoginOutcome.fail("INVALID_CREDENTIALS", 401);
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        recordAttempt(username, user, "SUCCESS", now);
        return LoginOutcome.ok(sessions.issue(user));
    }

    private void recordAttempt(String username, StaffUser user, String result, Instant now) {
        String tenantId = user == null || user.getTenant() == null ? null : user.getTenant().getId();
        attempts.save(new LoginAttempt(username, user == null ? null : user.getId(), tenantId, result, now));
    }
}
