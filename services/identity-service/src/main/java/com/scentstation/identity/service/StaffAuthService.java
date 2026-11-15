package com.scentstation.identity.service;

import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.StaffAuthResponse;
import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffAuthService {

    private static final String INVALID_CREDENTIALS = "Invalid username or password";

    private final StaffUserRepository staffUsers;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public StaffAuthService(
            StaffUserRepository staffUsers,
            TenantRepository tenants,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            Clock clock,
            @Value("${security.login.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${security.login.lock-duration:15m}") Duration lockDuration) {
        if (maxFailedAttempts < 1 || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("Login lockout settings must be positive");
        }
        this.staffUsers = staffUsers;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.clock = clock;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = lockDuration;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public StaffAuthResponse login(LoginRequest request) {
        String username = request.username().trim();
        StaffUser staffUser = staffUsers.findByUsernameForUpdate(username)
                .orElseThrow(() -> unauthorized());
        LocalDateTime now = LocalDateTime.now(clock);

        if (staffUser.isUnavailableAt(now) || !isTenantActive(staffUser.getTenantId())) {
            throw unauthorized();
        }

        if (!passwordEncoder.matches(request.password(), staffUser.getPasswordHash())) {
            staffUser.recordFailedLogin(
                    now, maxFailedAttempts, now.plus(lockDuration));
            staffUsers.save(staffUser);
            throw unauthorized();
        }

        List<String> roles = staffUser.getRoles().stream()
                .map(role -> StaffRole.valueOf(role.getId().getRoleName()).name())
                .sorted()
                .toList();
        if (roles.isEmpty()) {
            throw new IllegalStateException("Staff user must have at least one role");
        }

        staffUser.recordSuccessfulLogin(now);
        staffUsers.save(staffUser);
        return new StaffAuthResponse(
                jwtService.generateStaffToken(
                        staffUser.getId(), staffUser.getUsername(),
                        staffUser.getTenantId(), roles),
                "Bearer",
                staffUser.getId(),
                staffUser.getUsername(),
                roles,
                staffUser.getTenantId());
    }

    private boolean isTenantActive(String tenantId) {
        return tenantId == null || tenants.findById(tenantId)
                .map(tenant -> tenant.isActive())
                .orElse(false);
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS);
    }
}
