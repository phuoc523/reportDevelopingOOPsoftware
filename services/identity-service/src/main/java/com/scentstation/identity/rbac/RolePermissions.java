package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Default role grants. High-risk actions require an additional explicit grant. */
public final class RolePermissions {
    private static final Map<Role, Set<Permission>> MATRIX = new EnumMap<>(Role.class);
    private static final Set<Permission> HIGH_RISK = Set.of(
        Permission.REFUND_APPROVE, Permission.INVENTORY_ADJUST,
        Permission.DIAGNOSTIC_SPRAY, Permission.MACHINE_CONFIGURE,
        Permission.DEVICE_ACCESS_REVOKE);
    private static final Set<Permission> PLATFORM_ONLY = Set.of(
        Permission.TENANT_MANAGE, Permission.PLATFORM_CONFIG_MANAGE,
        Permission.PLATFORM_ROLE_MANAGE, Permission.PLATFORM_AUDIT_READ,
        Permission.PLATFORM_REPORT_READ, Permission.DEVICE_ACCESS_REVOKE);

    static {
        MATRIX.put(Role.PLATFORM_SUPER_ADMIN, EnumSet.allOf(Permission.class));
        MATRIX.put(Role.BRAND_ADMIN, EnumSet.of(
            Permission.BRAND_PROFILE_READ, Permission.BRAND_PROFILE_EDIT,
            Permission.BRAND_USER_MANAGE, Permission.BRAND_ROLE_MANAGE,
            Permission.CATALOG_READ, Permission.CATALOG_MANAGE, Permission.CAMPAIGN_MANAGE,
            Permission.LOCATION_READ, Permission.LOCATION_MANAGE,
            Permission.MACHINE_READ, Permission.MACHINE_CONTROL,
            Permission.ORDER_READ, Permission.ORDER_REVIEW, Permission.REFUND_REQUEST,
            Permission.INVENTORY_READ, Permission.INVENTORY_MANAGE,
            Permission.MAINTENANCE_READ, Permission.MAINTENANCE_MANAGE,
            Permission.ALERT_READ, Permission.ALERT_MANAGE,
            Permission.REPORT_READ, Permission.REPORT_EXPORT,
            Permission.AUDIT_READ, Permission.NOTIFICATION_READ,
            Permission.NOTIFICATION_CONFIGURE));
        MATRIX.put(Role.OPERATIONS_MANAGER, EnumSet.of(
            Permission.BRAND_PROFILE_READ, Permission.LOCATION_READ,
            Permission.MACHINE_READ, Permission.MACHINE_CONTROL,
            Permission.ORDER_READ, Permission.ORDER_REVIEW, Permission.REFUND_REQUEST,
            Permission.INVENTORY_READ, Permission.MAINTENANCE_READ,
            Permission.MAINTENANCE_MANAGE, Permission.ALERT_READ,
            Permission.ALERT_MANAGE, Permission.REPORT_READ,
            Permission.NOTIFICATION_READ));
        MATRIX.put(Role.TECHNICIAN, EnumSet.of(
            Permission.MACHINE_READ, Permission.MAINTENANCE_READ,
            Permission.MAINTENANCE_MANAGE, Permission.ALERT_READ,
            Permission.INVENTORY_READ, Permission.NOTIFICATION_READ));
        MATRIX.put(Role.INVENTORY_STAFF, EnumSet.of(
            Permission.CATALOG_READ, Permission.MACHINE_READ,
            Permission.INVENTORY_READ, Permission.INVENTORY_MANAGE,
            Permission.ALERT_READ, Permission.NOTIFICATION_READ,
            Permission.REPORT_READ));
        MATRIX.put(Role.VIEWER, EnumSet.of(
            Permission.BRAND_PROFILE_READ, Permission.CATALOG_READ,
            Permission.LOCATION_READ, Permission.MACHINE_READ,
            Permission.ORDER_READ, Permission.INVENTORY_READ,
            Permission.MAINTENANCE_READ, Permission.ALERT_READ,
            Permission.REPORT_READ, Permission.NOTIFICATION_READ));
        MATRIX.put(Role.CUSTOMER, EnumSet.of(Permission.KIOSK_BROWSE, Permission.KIOSK_ORDER));
        MATRIX.put(Role.ADMIN, EnumSet.noneOf(Permission.class));
        MATRIX.put(Role.STAFF, EnumSet.noneOf(Permission.class));
    }

    private RolePermissions() {}

    public static Set<Role> effectiveRoles(Set<Role> assigned, Role legacy) {
        if (assigned != null && !assigned.isEmpty()) return Set.copyOf(assigned);
        return legacy == null ? Set.of() : Set.of(legacy);
    }

    public static boolean allowed(Set<Role> roles, Set<Permission> explicitGrants, Permission requested) {
        // A VIEWER account never gains write permissions, even with stale or mixed-role grants.
        if (roles.contains(Role.VIEWER)) return MATRIX.get(Role.VIEWER).contains(requested);
        if (roles.contains(Role.PLATFORM_SUPER_ADMIN)) return true;
        if (PLATFORM_ONLY.contains(requested)) return false;
        if (explicitGrants != null && explicitGrants.contains(requested)) return true;
        if (HIGH_RISK.contains(requested)) return false;
        return roles.stream().anyMatch(role -> MATRIX.getOrDefault(role, Set.of()).contains(requested));
    }

    public static boolean isHighRisk(Permission permission) { return HIGH_RISK.contains(permission); }
    public static boolean isPlatformOnly(Permission permission) { return PLATFORM_ONLY.contains(permission); }
    public static boolean mayBrandDelegate(Permission permission) {
        return !HIGH_RISK.contains(permission) && !PLATFORM_ONLY.contains(permission)
            && permission != Permission.BRAND_ROLE_MANAGE
            && permission != Permission.BRAND_USER_MANAGE;
    }
    public static Map<Role, Set<Permission>> matrix() {
        Map<Role, Set<Permission>> copy = new EnumMap<>(Role.class);
        MATRIX.forEach((role, permissions) -> copy.put(role, Set.copyOf(permissions)));
        return Map.copyOf(copy);
    }
}
