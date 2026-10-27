package com.scentstation.identity.rbac;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Deny by default. Permissions are the union of all recognized roles. */
public final class RbacPolicy {
    private static final Map<Role, Set<Permission>> GRANTS;

    static {
        EnumMap<Role, Set<Permission>> grants = new EnumMap<>(Role.class);
        grants.put(Role.PLATFORM_SUPER_ADMIN, EnumSet.allOf(Permission.class));
        grants.put(Role.BRAND_ADMIN, EnumSet.of(
                Permission.USER_READ, Permission.ROLE_READ, Permission.ROLE_ASSIGN,
                Permission.ROLE_REVOKE, Permission.ROLE_AUDIT_READ,
                Permission.ORDER_READ, Permission.REFUND_APPROVE,
                Permission.INVENTORY_READ, Permission.INVENTORY_ADJUST,
                Permission.MACHINE_READ, Permission.MACHINE_DIAGNOSTIC_SPRAY,
                Permission.MACHINE_CONFIGURE, Permission.REPORT_READ));
        grants.put(Role.OPERATIONS_MANAGER, EnumSet.of(
                Permission.ORDER_READ, Permission.INVENTORY_READ,
                Permission.MACHINE_READ, Permission.REPORT_READ));
        grants.put(Role.TECHNICIAN, EnumSet.of(
                Permission.MACHINE_READ, Permission.MACHINE_DIAGNOSTIC_SPRAY));
        grants.put(Role.INVENTORY_STAFF, EnumSet.of(
                Permission.INVENTORY_READ, Permission.INVENTORY_ADJUST));
        grants.put(Role.VIEWER, EnumSet.of(
                Permission.ORDER_READ, Permission.INVENTORY_READ,
                Permission.MACHINE_READ, Permission.REPORT_READ));
        // Public sign-up can only create CUSTOMER; no back-office permission.
        grants.put(Role.CUSTOMER, EnumSet.noneOf(Permission.class));
        GRANTS = Collections.unmodifiableMap(grants);
    }

    private RbacPolicy() {}

    public static Set<Permission> permissionsFor(Set<String> roleNames) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        if (roleNames != null) {
            for (String name : roleNames) {
                try {
                    permissions.addAll(GRANTS.get(Role.valueOf(name)));
                } catch (IllegalArgumentException | NullPointerException ignored) {
                    // Unknown/legacy values NEVER grant authority.
                }
            }
        }
        return Collections.unmodifiableSet(permissions);
    }

    public static boolean has(Set<String> roles, Permission permission) {
        return permissionsFor(roles).contains(permission);
    }

    public static Map<String, Set<String>> catalog() {
        Map<String, Set<String>> result = new java.util.TreeMap<>();
        GRANTS.forEach((role, permissions) -> {
            Set<String> names = new TreeSet<>();
            permissions.forEach(permission -> names.add(permission.name()));
            result.put(role.name(), Collections.unmodifiableSet(names));
        });
        return Collections.unmodifiableMap(result);
    }
}
