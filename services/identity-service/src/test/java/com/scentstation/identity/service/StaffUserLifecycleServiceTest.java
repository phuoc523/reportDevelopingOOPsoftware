package com.scentstation.identity.service;

import com.scentstation.identity.dto.StaffUserRequests.CreateRequest;
import com.scentstation.identity.dto.StaffUserRequests.UpdateRequest;
import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import com.scentstation.identity.security.StaffPrincipal;
import com.scentstation.identity.tenant.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StaffUserLifecycleServiceTest {

    private StaffUserRepository staffUsers;
    private TenantRepository tenants;
    private PasswordEncoder passwordEncoder;
    private StaffUserLifecycleService service;

    @BeforeEach
    void setUp() {
        staffUsers = mock(StaffUserRepository.class);
        tenants = mock(TenantRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new StaffUserLifecycleService(staffUsers, tenants, passwordEncoder);
    }

    @Test
    void brandAdminCreatesUsersOnlyInOwnTenant() {
        StaffPrincipal brandAdmin = principal(
                "brand-admin", "tenant-a", StaffRole.BRAND_ADMIN);
        when(staffUsers.existsByUsername("demo.b")).thenReturn(false);
        when(tenants.findById("tenant-a"))
                .thenReturn(Optional.of(new Tenant("DEMO")));
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt");
        when(staffUsers.save(any(StaffUser.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var response = service.create(brandAdmin,
                new CreateRequest(" demo.b ", "password123", null,
                        Set.of(StaffRole.TECHNICIAN)));

        assertEquals("demo.b", response.username());
        assertEquals("tenant-a", response.tenantId());
        assertEquals(List.of(StaffRole.TECHNICIAN), response.roles());
        verify(staffUsers).save(argThat(user ->
                user.getTenantId().equals("tenant-a")
                        && user.getPasswordHash().equals("bcrypt")
                        && user.getRoles().size() == 1));
    }

    @Test
    void brandAdminCannotCreateOrAssignAUserInAnotherTenant() {
        StaffPrincipal brandAdmin = principal(
                "brand-admin", "tenant-a", StaffRole.BRAND_ADMIN);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.create(brandAdmin,
                        new CreateRequest("outsider", "password123", "tenant-b",
                                Set.of(StaffRole.VIEWER))));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(staffUsers, never()).save(any(StaffUser.class));
    }

    @Test
    void brandAdminCannotGrantPrivilegedRoles() {
        StaffPrincipal brandAdmin = principal(
                "brand-admin", "tenant-a", StaffRole.BRAND_ADMIN);
        when(staffUsers.existsByUsername("elevated")).thenReturn(false);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.create(brandAdmin,
                        new CreateRequest("elevated", "password123", null,
                                Set.of(StaffRole.BRAND_ADMIN))));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(staffUsers, never()).save(any(StaffUser.class));
    }

    @Test
    void brandAdminCannotReadAnotherTenantsAccount() {
        StaffPrincipal brandAdmin = principal(
                "brand-admin", "tenant-a", StaffRole.BRAND_ADMIN);
        when(staffUsers.findWithRolesByIdAndTenantId("staff-id", "tenant-a"))
                .thenReturn(Optional.empty());

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.findById(brandAdmin, "staff-id"));

        assertEquals(HttpStatus.NOT_FOUND, failure.getStatusCode());
        verify(staffUsers, never()).findWithRolesById("staff-id");
    }

    @Test
    void brandAdminCannotMoveExistingAccountToAnotherTenant() {
        StaffPrincipal brandAdmin = principal(
                "brand-admin", "tenant-a", StaffRole.BRAND_ADMIN);
        StaffUser staffUser = new StaffUser("demo.b", "bcrypt", "tenant-a");
        staffUser.addRole(StaffRole.TECHNICIAN);
        when(staffUsers.findWithRolesByIdAndTenantId(staffUser.getId(), "tenant-a"))
                .thenReturn(Optional.of(staffUser));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.update(brandAdmin, staffUser.getId(),
                        new UpdateRequest("demo.b", "tenant-b",
                                Set.of(StaffRole.TECHNICIAN))));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verify(staffUsers, never()).save(any(StaffUser.class));
    }

    @Test
    void platformAdminCanCreateGlobalSuperAdminWithoutTenant() {
        StaffPrincipal platform = principal(
                "platform.admin", null, StaffRole.PLATFORM_SUPER_ADMIN);
        when(staffUsers.existsByUsername("platform.new")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("bcrypt");
        when(staffUsers.save(any(StaffUser.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        var response = service.create(platform,
                new CreateRequest("platform.new", "password123", null,
                        Set.of(StaffRole.PLATFORM_SUPER_ADMIN)));

        assertNull(response.tenantId());
        assertEquals(List.of(StaffRole.PLATFORM_SUPER_ADMIN), response.roles());
    }

    @Test
    void platformRoleMustBeTheOnlyRoleAndHaveNoTenant() {
        StaffPrincipal platform = principal(
                "platform.admin", null, StaffRole.PLATFORM_SUPER_ADMIN);
        when(staffUsers.existsByUsername("invalid")).thenReturn(false);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.create(platform,
                        new CreateRequest("invalid", "password123", "tenant-a",
                                Set.of(StaffRole.PLATFORM_SUPER_ADMIN, StaffRole.VIEWER))));

        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verify(staffUsers, never()).save(any(StaffUser.class));
    }

    @Test
    void nonManagerCannotManageStaffAccounts() {
        StaffPrincipal viewer = principal("viewer", "tenant-a", StaffRole.VIEWER);

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> service.findAll(viewer));

        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        verifyNoInteractions(staffUsers);
    }

    private StaffPrincipal principal(String id, String tenantId, StaffRole role) {
        return new StaffPrincipal(id, id, tenantId, Set.of(role));
    }
}
