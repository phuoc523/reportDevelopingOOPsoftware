package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class AccessPolicyTest {
    private final AccessPolicy policy = new AccessPolicy();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private void actor(Role role, Long tenant, Set<Long> locations, Set<Long> machines) {
        AccountPrincipal p = new AccountPrincipal(1L, "test", tenant, Set.of(role), Set.of(), locations, machines);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(p, null, Set.of()));
    }

    @Test void rejectsAnotherBrand() {
        actor(Role.BRAND_ADMIN, 1L, Set.of(), Set.of());
        assertTrue(policy.tenant(1L));
        assertFalse(policy.tenant(2L));
    }

    @Test void deniesUnassignedMachine() {
        actor(Role.TECHNICIAN, 5L, Set.of(11L), Set.of(22L));
        assertTrue(policy.scope(5L, 11L, 22L));
        assertFalse(policy.scope(5L, 11L, 23L));
        assertFalse(policy.scope(6L, 11L, 22L));
    }

    @Test void platformCanAccessTenants() {
        actor(Role.PLATFORM_SUPER_ADMIN, null, Set.of(), Set.of());
        assertTrue(policy.tenant(5L));
        assertTrue(policy.has(Permission.TENANT_MANAGE));
    }
}
