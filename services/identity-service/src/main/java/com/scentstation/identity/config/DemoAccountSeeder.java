package com.scentstation.identity.config;

import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import com.scentstation.identity.tenant.Tenant;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "demo.accounts.enabled", havingValue = "true")
public class DemoAccountSeeder implements ApplicationRunner {

    private static final String DEMO_TENANT_CODE = "DEMO";
    private static final Logger LOGGER = LoggerFactory.getLogger(DemoAccountSeeder.class);

    private record DemoAccount(String username, StaffRole role, boolean platform) {
    }

    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("platform.admin", StaffRole.PLATFORM_SUPER_ADMIN, true),
            new DemoAccount("demo.admin", StaffRole.BRAND_ADMIN, false),
            new DemoAccount("demo.a", StaffRole.OPERATIONS_MANAGER, false),
            new DemoAccount("demo.b", StaffRole.TECHNICIAN, false),
            new DemoAccount("demo.c", StaffRole.INVENTORY_STAFF, false),
            new DemoAccount("demo.d", StaffRole.VIEWER, false));

    private final StaffUserRepository staffUsers;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;
    private final Environment environment;

    public DemoAccountSeeder(
            StaffUserRepository staffUsers,
            TenantRepository tenants,
            PasswordEncoder passwordEncoder,
            @Value("${SCENTSTATION_DEMO_PASSWORD:}") String demoPassword,
            Environment environment) {
        this.staffUsers = staffUsers;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        if (Arrays.stream(environment.getActiveProfiles())
                .anyMatch(profile -> profile.equalsIgnoreCase("prod")
                        || profile.equalsIgnoreCase("production"))) {
            throw new IllegalStateException("Demo account seeding is disabled in production");
        }
        validatePassword();
        Tenant demoTenant = tenants.findByTenantCode(DEMO_TENANT_CODE)
                .orElseGet(() -> tenants.save(new Tenant(DEMO_TENANT_CODE)));
        if (!demoTenant.isActive()) {
            LOGGER.warn("Existing DEMO tenant is inactive; demo staff logins will be rejected");
        }
        String demoTenantId = demoTenant.getId();

        for (DemoAccount account : ACCOUNTS) {
            if (staffUsers.existsByUsername(account.username())) {
                continue;
            }

            StaffUser staffUser = new StaffUser(
                    account.username(),
                    passwordEncoder.encode(demoPassword),
                    account.platform() ? null : demoTenantId);
            staffUser.addRole(account.role());
            staffUsers.save(staffUser);
        }
    }

    private void validatePassword() {
        if (demoPassword == null || demoPassword.length() < 8 || demoPassword.length() > 72) {
            throw new IllegalStateException(
                    "SCENTSTATION_DEMO_PASSWORD must contain 8 to 72 characters");
        }
    }
}
