package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RolePermissionsTest {
    @Test
    void pendingStaffHasNoEffectiveOperationalRoleGrantsOrScopes() {
        User user = new User("staff", "staff@example.com", "hash", "Staff", Role.OPERATIONS_MANAGER);
        user.setTenantId(9L);
        user.setPermissionGrants(Set.of(Permission.REPORT_READ));
        user.setLocationIds(Set.of(2L));
        user.setMachineIds(Set.of(3L));
        user.setPendingApproval(true);

        var actor = AccountPrincipal.from(user);
        assertEquals(Set.of(Role.STAFF), actor.roles());
        assertTrue(actor.grants().isEmpty());
        assertTrue(actor.locationIds().isEmpty());
        assertTrue(actor.machineIds().isEmpty());
        assertFalse(RolePermissions.allowed(actor.roles(), actor.grants(), Permission.MACHINE_CONTROL));

        long pendingVersion = user.getTokenVersion();
        user.setPendingApproval(false);
        assertTrue(user.getTokenVersion() > pendingVersion);
        assertTrue(AccountPrincipal.from(user).grants().contains(Permission.MACHINE_CONTROL));
    }
    @Test
    void effectivePermissionsProjectsRoleMatrixForDownstreamServices() {
        Set<Permission> brandAdmin = RolePermissions.effectivePermissions(
                Set.of(Role.BRAND_ADMIN), Set.of()
        );
        assertTrue(brandAdmin.contains(Permission.CATALOG_READ));
        assertTrue(brandAdmin.contains(Permission.CATALOG_MANAGE));
        assertTrue(brandAdmin.contains(Permission.CAMPAIGN_MANAGE));

    }
    @Test void platformMayManageTenants() {
        assertTrue(RolePermissions.allowed(Set.of(Role.PLATFORM_SUPER_ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
    @Test void brandMayNotManagePlatform() {
        assertFalse(RolePermissions.allowed(Set.of(Role.BRAND_ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
    @Test void sensitiveActionsRequireExplicitGrant() {
        assertFalse(RolePermissions.allowed(Set.of(Role.TECHNICIAN), Set.of(), Permission.DIAGNOSTIC_SPRAY));
        assertTrue(RolePermissions.allowed(Set.of(Role.TECHNICIAN), Set.of(Permission.DIAGNOSTIC_SPRAY), Permission.DIAGNOSTIC_SPRAY));
        assertFalse(RolePermissions.mayBrandDelegate(Permission.REFUND_APPROVE));
    }
    @Test void matrixHasExactlyEightRolesAndThirtySevenPermissions() {
        assertEquals(8, Role.values().length);
        assertEquals(37, Permission.values().length);
        assertEquals(8, RolePermissions.matrix().size());
        assertEquals(37, RolePermissions.matrix().get(Role.PLATFORM_SUPER_ADMIN).size());
        assertEquals(26, RolePermissions.matrix().get(Role.BRAND_ADMIN).size());
        assertEquals(14, RolePermissions.matrix().get(Role.OPERATIONS_MANAGER).size());
        assertEquals(6, RolePermissions.matrix().get(Role.TECHNICIAN).size());
        assertEquals(7, RolePermissions.matrix().get(Role.INVENTORY_STAFF).size());
        assertEquals(2, RolePermissions.matrix().get(Role.CUSTOMER).size());
        assertTrue(RolePermissions.matrix().get(Role.ADMIN).isEmpty());
        assertTrue(RolePermissions.matrix().get(Role.STAFF).isEmpty());
    }

    @Test void highRiskPermissionsRequirePlatformDelegation() {
        for (Permission permission : Set.of(Permission.REFUND_APPROVE, Permission.INVENTORY_ADJUST,
                Permission.DIAGNOSTIC_SPRAY, Permission.DEVICE_ACCESS_REVOKE)) {
            assertFalse(RolePermissions.allowed(Set.of(Role.BRAND_ADMIN), Set.of(), permission));
            assertFalse(RolePermissions.mayBrandDelegate(permission));
        }
        assertTrue(RolePermissions.allowed(Set.of(Role.BRAND_ADMIN), Set.of(), Permission.MACHINE_CONFIGURE));
        assertFalse(RolePermissions.allowed(Set.of(Role.TECHNICIAN), Set.of(Permission.TENANT_MANAGE),
            Permission.TENANT_MANAGE));
    }
    @Test void legacyAdminDoesNotImplyPlatformAdmin() {
        assertFalse(RolePermissions.allowed(Set.of(Role.ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
}
