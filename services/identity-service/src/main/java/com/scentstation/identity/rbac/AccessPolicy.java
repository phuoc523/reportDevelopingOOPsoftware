package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.Role;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Used in controllers/services; direct REST calls must not choose their own tenant context. */
@Component("accessPolicy")
public class AccessPolicy {
    public AccountPrincipal current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof AccountPrincipal actor) || !a.isAuthenticated()) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return actor;
    }

    public boolean has(String permissionName) {
        try { return has(Permission.valueOf(permissionName)); }
        catch (IllegalArgumentException ex) { return false; }
    }

    public boolean has(Permission permission) {
        AccountPrincipal actor = current();
        return RolePermissions.allowed(actor.roles(), actor.grants(), permission);
    }

    public boolean platform() { return current().roles().contains(Role.PLATFORM_SUPER_ADMIN); }

    public boolean tenant(Long requestedTenantId) {
        return requestedTenantId != null && (platform()
            || requestedTenantId.equals(current().tenantId()));
    }

    /** Assigned operational scopes, if any, are an allowlist; never trust a client scope header. */
    public boolean scope(Long tenantId, Long locationId, Long machineId) {
        if (!tenant(tenantId)) return false;
        if (platform()) return true;
        AccountPrincipal a = current();
        boolean technicianOnly = a.roles().contains(Role.TECHNICIAN)
            && !a.roles().contains(Role.BRAND_ADMIN)
            && !a.roles().contains(Role.OPERATIONS_MANAGER)
            && !a.roles().contains(Role.PLATFORM_SUPER_ADMIN);
        if (technicianOnly && (machineId == null || a.machineIds().isEmpty()
            || !a.machineIds().contains(machineId))) return false;
        if (locationId != null && !a.locationIds().isEmpty() && !a.locationIds().contains(locationId)) return false;
        if (machineId != null && !a.machineIds().isEmpty() && !a.machineIds().contains(machineId)) return false;
        return true;
    }
}
