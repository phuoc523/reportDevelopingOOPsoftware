package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RolePermissionsTest {
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
    @Test void legacyAdminDoesNotImplyPlatformAdmin() {
        assertFalse(RolePermissions.allowed(Set.of(Role.ADMIN), Set.of(), Permission.TENANT_MANAGE));
    }
}
