package com.scentstation.identity.config;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Opt-in first-admin bootstrap; never creates a known default password. */
@Component
public class PlatformAdminBootstrap {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String password;
    private final String email;

    public PlatformAdminBootstrap(UserRepository users, PasswordEncoder encoder,
        @Value("${platform.bootstrap.username:}") String username,
        @Value("${platform.bootstrap.password:}") String password,
        @Value("${platform.bootstrap.email:}") String email) {
        this.users = users; this.encoder = encoder; this.username = username;
        this.password = password; this.email = email;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        if (username.isBlank() || password.isBlank()) return;
        if (password.length() < 16 || email.isBlank())
            throw new IllegalStateException("Bootstrap administrator requires a strong password and email");
        if (users.findByUsername(username.trim()).isPresent()) return;
        User administrator = new User(username.trim(), email.trim().toLowerCase(),
            encoder.encode(password), "Platform Administrator", Role.PLATFORM_SUPER_ADMIN);
        users.save(administrator);
    }
}
