package com.scentstation.identity.account;

import com.scentstation.identity.account.AccountRequests.*;
import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.Permission;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.tenant.TenantService;
import java.util.Set;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountAdminServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder encoder;
    @Mock TenantService tenants;
    @Mock AccessPolicy access;
    @Mock AuditService audit;
    @InjectMocks AccountAdminService service;

    private User technician(long tenantId) {
        User u = new User("tech", "tech@example.org", "hash", "Tech", Role.TECHNICIAN);
        u.setTenantId(tenantId);
        ReflectionTestUtils.setField(u, "id", 15L);
        return u;
    }

    private void brandAdmin() {
        when(access.has(Permission.BRAND_USER_MANAGE)).thenReturn(true);
        when(access.current()).thenReturn(new AccountPrincipal(1L, "brand", 1L,
            Set.of(Role.BRAND_ADMIN), Set.of(), Set.of(), Set.of()));
    }

    private void platformAdmin() {
        when(access.platform()).thenReturn(true);
        when(access.current()).thenReturn(new AccountPrincipal(1L, "platform", null,
            Set.of(Role.PLATFORM_SUPER_ADMIN), Set.of(), Set.of(), Set.of()));
    }

    @Test void brandAdminCannotGrantHighRiskAction() {
        brandAdmin();
        User u = technician(1L);
        when(users.findById(15L)).thenReturn(Optional.of(u));
        assertThrows(ResponseStatusException.class, () -> service.grants(15L,
            new Grants(Set.of(Permission.REFUND_APPROVE)), false));
        assertTrue(u.getPermissionGrants().isEmpty());
        verifyNoInteractions(audit);
    }

    @Test void brandAdminCannotAssignPlatformRole() {
        brandAdmin();
        User u = technician(1L);
        when(users.findById(15L)).thenReturn(Optional.of(u));
        assertThrows(ResponseStatusException.class, () -> service.roles(15L,
            new Roles(Set.of(Role.PLATFORM_SUPER_ADMIN)), false));
        assertEquals(Set.of(Role.TECHNICIAN), u.getRoles());
        verifyNoInteractions(audit);
    }

    @Test void brandAdminCannotChangeAnotherTenantsScope() {
        brandAdmin();
        User u = technician(2L);
        when(users.findById(15L)).thenReturn(Optional.of(u));
        assertThrows(ResponseStatusException.class, () -> service.scopes(15L,
            new Scopes(Set.of(42L), Set.of(99L)), false));
        verifyNoInteractions(audit);
    }

    @Test void platformAdminMayExplicitlyGrantDiagnosticSpray() {
        platformAdmin();
        User u = technician(1L);
        when(users.findById(15L)).thenReturn(Optional.of(u));
        var result = service.grants(15L, new Grants(Set.of(Permission.DIAGNOSTIC_SPRAY)), true);
        assertEquals(Set.of(Permission.DIAGNOSTIC_SPRAY), result.explicitGrants());
        verify(audit).record(eq(1L), eq(1L), eq("GRANTS_CHANGED"), eq("USER"),
            eq("15"), anyString(), anyString());
    }

    @Test void lockingUserRevokesExistingTokenVersion() {
        brandAdmin();
        User u = technician(1L);
        when(users.findById(15L)).thenReturn(Optional.of(u));
        long tokenVersion = u.getTokenVersion();
        service.status(15L, new Status(true, true), false);
        assertTrue(u.isLocked());
        assertTrue(u.getTokenVersion() > tokenVersion);
    }
}
