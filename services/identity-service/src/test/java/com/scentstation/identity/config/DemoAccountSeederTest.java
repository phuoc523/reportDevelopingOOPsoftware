package com.scentstation.identity.config;

import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import com.scentstation.identity.tenant.Tenant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoAccountSeederTest {

    private StaffUserRepository staffUsers;
    private TenantRepository tenants;
    private PasswordEncoder passwordEncoder;
    private MockEnvironment environment;
    private Set<String> usernames;
    private List<StaffUser> savedUsers;
    private Tenant demoTenant;
    private DemoAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        staffUsers = mock(StaffUserRepository.class);
        tenants = mock(TenantRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        environment = new MockEnvironment();
        usernames = new HashSet<>();
        savedUsers = new ArrayList<>();

        when(staffUsers.existsByUsername(any())).thenAnswer(invocation ->
                usernames.contains(invocation.getArgument(0)));
        when(staffUsers.save(any(StaffUser.class))).thenAnswer(invocation -> {
            StaffUser staffUser = invocation.getArgument(0);
            usernames.add(staffUser.getUsername());
            savedUsers.add(staffUser);
            return staffUser;
        });
        when(tenants.findByTenantCode("DEMO")).thenAnswer(invocation ->
                Optional.ofNullable(demoTenant));
        when(tenants.save(any(Tenant.class))).thenAnswer(invocation -> {
            demoTenant = invocation.getArgument(0);
            return demoTenant;
        });
        when(passwordEncoder.encode("local-demo-password")).thenReturn("bcrypt-hash");

        seeder = new DemoAccountSeeder(
                staffUsers, tenants, passwordEncoder, "local-demo-password", environment);
    }

    @Test
    void seedsTheSixAccountsAndTenantOnlyOnce() throws Exception {
        var args = new DefaultApplicationArguments(new String[0]);

        seeder.run(args);
        seeder.run(args);

        assertEquals(6, usernames.size());
        assertEquals(6, demoAccountsSaved());
        assertNotNull(demoTenant);
        assertEquals("DEMO", demoTenant.getTenantCode());
        assertTrue(demoTenant.isActive());
        assertSeededRole("platform.admin", StaffRole.PLATFORM_SUPER_ADMIN, null);
        assertSeededRole("demo.admin", StaffRole.BRAND_ADMIN, demoTenant.getId());
        assertSeededRole("demo.a", StaffRole.OPERATIONS_MANAGER, demoTenant.getId());
        assertSeededRole("demo.b", StaffRole.TECHNICIAN, demoTenant.getId());
        assertSeededRole("demo.c", StaffRole.INVENTORY_STAFF, demoTenant.getId());
        assertSeededRole("demo.d", StaffRole.VIEWER, demoTenant.getId());
        verify(tenants, times(1)).save(any(Tenant.class));
        verify(passwordEncoder, times(6)).encode("local-demo-password");
        verify(staffUsers, times(6)).save(any(StaffUser.class));
    }

    @Test
    void preservesExistingUsersWithoutChangingTheirRolesOrPassword() throws Exception {
        usernames.add("demo.a");
        var args = new DefaultApplicationArguments(new String[0]);

        seeder.run(args);

        assertEquals(6, usernames.size());
        verify(staffUsers, times(5)).save(any(StaffUser.class));
        assertFalse(savedUsers.stream().anyMatch(user -> user.getUsername().equals("demo.a")));
        verify(passwordEncoder, times(5)).encode("local-demo-password");
    }

    @Test
    void reusesExistingTenantWithoutChangingIt() throws Exception {
        demoTenant = new Tenant("DEMO");
        demoTenant.setActive(false);
        String tenantId = demoTenant.getId();

        seeder.run(new DefaultApplicationArguments(new String[0]));

        assertEquals(tenantId, demoTenant.getId());
        assertFalse(demoTenant.isActive());
        verify(tenants, never()).save(any(Tenant.class));
    }

    @Test
    void rejectsMissingOrInvalidDemoPasswordBeforeWritingData() {
        DemoAccountSeeder missing = new DemoAccountSeeder(
                staffUsers, tenants, passwordEncoder, "", environment);

        assertThrows(IllegalStateException.class,
                () -> missing.run(new DefaultApplicationArguments(new String[0])));

        verifyNoInteractions(staffUsers, tenants, passwordEncoder);
    }

    @Test
    void refusesToSeedWhenProductionProfileIsActive() {
        environment.setActiveProfiles("production");

        assertThrows(IllegalStateException.class,
                () -> seeder.run(new DefaultApplicationArguments(new String[0])));

        verifyNoInteractions(staffUsers, tenants, passwordEncoder);
    }

    private int demoAccountsSaved() {
        return (int) usernames.stream()
                .filter(name -> Set.of(
                        "platform.admin", "demo.admin", "demo.a",
                        "demo.b", "demo.c", "demo.d").contains(name))
                .count();
    }

    private void assertSeededRole(
            String username, StaffRole role, String expectedTenantId) {
        StaffUser staffUser = savedUsers.stream()
                .filter(user -> user.getUsername().equals(username))
                .findFirst()
                .orElseThrow();
        assertEquals(expectedTenantId, staffUser.getTenantId());
        assertEquals(1, staffUser.getRoles().size());
        assertEquals(role.name(), staffUser.getRoles().iterator().next().getId().getRoleName());
        assertEquals("bcrypt-hash", staffUser.getPasswordHash());
    }
}
