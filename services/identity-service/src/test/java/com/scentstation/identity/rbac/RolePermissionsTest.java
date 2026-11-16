package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RolePermissionsTest {
    @Test
    void effectivePermissionsProjectsRoleMatrixForDownstreamServices() {
        Set<Permission> brandAdmin = RolePermissions.effectivePermissions(
                Set.of(Role.BRAND_ADMIN), Set.of()
        );
        assertTrue(brandAdmin.contains(Permission.CATALOG_READ));
        assertTrue(brandAdmin.contains(Permission.CATALOG_MANAGE));
        assertTrue(brandAdmin.contains(Permission.CAMPAIGN_MANAGE));

        Set<Permission> viewer = RolePermissions.effectivePermissions(Set.of(Role.VIEWER), Set.of());
        assertTrue(viewer.contains(Permission.CATALOG_READ));
        assertFalse(viewer.contains(Permission.CATALOG_MANAGE));
    }
    @Test void platformMayManageTenants() {
        assertTrue(RolePermissions.allowed(Set.of(Role.PLATFORM_SUPER_ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
    @Test void brandMayNotManagePlatform() {
        assertFalse(RolePermissions.allowed(Set.of(Role.BRAND_ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
    @Test void viewerCannotMutate() {
        assertFalse(RolePermissions.allowed(Set.of(Role.VIEWER), Set.of(), Permission.CATALOG_MANAGE));
        assertTrue(RolePermissions.allowed(Set.of(Role.VIEWER), Set.of(), Permission.REPORT_READ));
    }
    @Test void viewerExplicitGrantCannotEscalate() {
        assertFalse(RolePermissions.allowed(Set.of(Role.VIEWER, Role.OPERATIONS_MANAGER),
            Set.of(Permission.CATALOG_MANAGE), Permission.MACHINE_CONTROL));
        assertFalse(RolePermissions.allowed(Set.of(Role.VIEWER, Role.PLATFORM_SUPER_ADMIN),
            Set.of(Permission.CATALOG_MANAGE), Permission.CATALOG_MANAGE));
        assertFalse(RolePermissions.allowed(Set.of(Role.VIEWER),
            Set.of(Permission.CATALOG_MANAGE), Permission.CATALOG_MANAGE));
        assertFalse(RolePermissions.allowed(Set.of(Role.VIEWER),
            Set.of(Permission.INVENTORY_MANAGE), Permission.INVENTORY_MANAGE));
        assertTrue(RolePermissions.allowed(Set.of(Role.VIEWER),
            Set.of(Permission.REPORT_READ), Permission.REPORT_READ));
    }
    @Test void sensitiveActionsRequireExplicitGrant() {
        assertFalse(RolePermissions.allowed(Set.of(Role.TECHNICIAN), Set.of(), Permission.DIAGNOSTIC_SPRAY));
        assertTrue(RolePermissions.allowed(Set.of(Role.TECHNICIAN), Set.of(Permission.DIAGNOSTIC_SPRAY), Permission.DIAGNOSTIC_SPRAY));
        assertFalse(RolePermissions.mayBrandDelegate(Permission.REFUND_APPROVE));
    }
    @Test void matrixHasExactlyNineRolesAndThirtySevenPermissions() {
        assertEquals(9, Role.values().length);
        assertEquals(37, Permission.values().length);
        assertEquals(9, RolePermissions.matrix().size());
        assertEquals(37, RolePermissions.matrix().get(Role.PLATFORM_SUPER_ADMIN).size());
        assertEquals(25, RolePermissions.matrix().get(Role.BRAND_ADMIN).size());
        assertEquals(14, RolePermissions.matrix().get(Role.OPERATIONS_MANAGER).size());
        assertEquals(6, RolePermissions.matrix().get(Role.TECHNICIAN).size());
        assertEquals(7, RolePermissions.matrix().get(Role.INVENTORY_STAFF).size());
        assertEquals(10, RolePermissions.matrix().get(Role.VIEWER).size());
        assertEquals(2, RolePermissions.matrix().get(Role.CUSTOMER).size());
        assertTrue(RolePermissions.matrix().get(Role.ADMIN).isEmpty());
        assertTrue(RolePermissions.matrix().get(Role.STAFF).isEmpty());
    }

    @Test void highRiskPermissionsRequirePlatformDelegation() {
        for (Permission permission : Set.of(Permission.REFUND_APPROVE, Permission.INVENTORY_ADJUST,
                Permission.DIAGNOSTIC_SPRAY, Permission.MACHINE_CONFIGURE, Permission.DEVICE_ACCESS_REVOKE)) {
            assertFalse(RolePermissions.allowed(Set.of(Role.BRAND_ADMIN), Set.of(), permission));
            assertFalse(RolePermissions.mayBrandDelegate(permission));
        }
        assertFalse(RolePermissions.allowed(Set.of(Role.TECHNICIAN), Set.of(Permission.TENANT_MANAGE),
            Permission.TENANT_MANAGE));
    }
    @Test void legacyAdminDoesNotImplyPlatformAdmin() {
        assertFalse(RolePermissions.allowed(Set.of(Role.ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
}
