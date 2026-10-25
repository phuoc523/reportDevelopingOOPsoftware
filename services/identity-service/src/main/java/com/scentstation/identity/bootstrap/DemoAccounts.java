package com.scentstation.identity.bootstrap;

import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.model.Tenant;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.Set;

/** Explicit opt-in demo profile; no built-in password or automatic production user. */
@Component
@Profile("demo")
public class DemoAccounts implements ApplicationRunner {
    private final TenantRepository tenants;
    private final StaffUserRepository users;
    private final PasswordEncoder encoder;
    private final String password;

    public DemoAccounts(TenantRepository tenants, StaffUserRepository users, PasswordEncoder encoder,
                        @Value("${DEMO_ADMIN_PASSWORD:}") String password) {
        this.tenants = tenants;
        this.users = users;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank() || password.length() < 12) {
            throw new IllegalStateException("DEMO_ADMIN_PASSWORD must contain at least 12 characters in demo profile");
        }
        if (users.findByUsername("demo.admin").isPresent()) return;
        Tenant tenant = tenants.findByCode("DEMO").orElseGet(() -> tenants.save(new Tenant("DEMO")));
        users.save(new StaffUser("demo.admin", encoder.encode(password), tenant, Set.of("BRAND_ADMIN")));
    }
}
