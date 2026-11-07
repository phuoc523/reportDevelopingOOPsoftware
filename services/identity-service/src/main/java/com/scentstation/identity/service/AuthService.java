package com.scentstation.identity.service;

import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.rbac.AccessPolicy;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final AccessPolicy access;

    @Autowired
    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       AuditService audit, AccessPolicy access) {
        this.users = users; this.passwordEncoder = encoder;
        this.jwtService = jwt; this.audit = audit; this.access = access;
    }

    /** Retained for existing isolated unit tests. */
    AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users; this.passwordEncoder = encoder;
        this.jwtService = jwt; this.audit = null; this.access = null;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        // Public registration always creates a customer with no elevated tenant role.
        User user = new User(username, email, passwordEncoder.encode(request.password()),
            request.fullName().trim(), Role.CUSTOMER);
        users.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().trim())
            .orElseThrow(() -> {
                if (audit != null) audit.authAttempt(null, null, null, false);
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
            });
        if (!user.isActive() || user.isLocked() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            if (audit != null) audit.authAttempt(user.getId(), user.getTenantId(), null, false);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (audit != null) audit.authAttempt(user.getId(), user.getTenantId(), null, true);
        return response(user);
    }

    @Transactional
    public void logout() {
        var actor = access.current();
        User user = users.findById(actor.id()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        user.revokeSessions(); // Revokes all previously issued JWTs for this account.
        if (audit != null) audit.record(actor.id(), actor.tenantId(), "LOGOUT", "USER",
            actor.id().toString(), null, "tokens-revoked");
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", user.getId(),
            user.getUsername(), user.getRole().name());
    }
}
