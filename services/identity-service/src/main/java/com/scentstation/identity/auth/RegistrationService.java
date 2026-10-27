package com.scentstation.identity.auth;

import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public registration: always assigns CUSTOMER with no administrative permissions. */
@Service
public class RegistrationService {
    private final StaffUserRepository users;
    private final PasswordEncoder passwords;

    public RegistrationService(StaffUserRepository users, PasswordEncoder passwords) {
        this.users = users;
        this.passwords = passwords;
    }

    @Transactional
    public AuthDto.RegisterResponse register(AuthDto.RegisterRequest input) {
        String username = input.username().strip().toLowerCase(Locale.ROOT);
        String email = input.email().strip().toLowerCase(Locale.ROOT);
        if (!username.matches("[a-z0-9._-]{3,120}")) {
            throw new InvalidRegistrationException("INVALID_USERNAME", "Invalid username");
        }
        // BCrypt's password input is at most 72 bytes. Explicitly reject truncation.
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new InvalidRegistrationException("INVALID_PASSWORD", "Password too long");
        }
        if (users.existsByUsername(username) || users.existsByEmail(email)) {
            throw new DuplicateAccountException();
        }
        StaffUser created = new StaffUser(username, passwords.encode(input.password()), null, Set.of("CUSTOMER"));
        created.setEmail(email);
        // The database uniqueness constraints also handle concurrent registrations.
        StaffUser saved;
        try {
            saved = users.saveAndFlush(created);
        } catch (DataIntegrityViolationException duplicate) {
            throw new DuplicateAccountException();
        }
        return new AuthDto.RegisterResponse(saved.getId(), saved.getUsername(), saved.getEmail());
    }

    public static class DuplicateAccountException extends RuntimeException {}
    public static class InvalidRegistrationException extends RuntimeException {
        private final String code;
        public InvalidRegistrationException(String code, String message) {
            super(message);
            this.code = code;
        }
        public String code() { return code; }
    }
}
