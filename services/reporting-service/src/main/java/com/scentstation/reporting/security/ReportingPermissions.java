package com.scentstation.reporting.security;

import com.scentstation.reporting.dto.integration.IdentityActor;
import java.util.Set;

/** Reporting-only projection of Identity RolePermissions; never reads request role headers or JWT claims. */
public final class ReportingPermissions {
    public static final String READ = "REPORT_READ";
    public static final String EXPORT = "REPORT_EXPORT";
    public static final String PLATFORM = "PLATFORM_REPORT_READ";
    private ReportingPermissions() {}

    public static boolean has(IdentityActor actor, String permission) {
        if (actor.roles().contains("VIEWER")) { return READ.equals(permission); }
        if (actor.roles().contains("PLATFORM_SUPER_ADMIN")) { return true; }
        if (PLATFORM.equals(permission)) { return false; }
        if (actor.grants().contains(permission)) { return true; }
        if (READ.equals(permission)) {
            return actor.roles().stream().anyMatch(Set.of("BRAND_ADMIN", "OPERATIONS_MANAGER", "INVENTORY_STAFF")::contains);
        }
        return EXPORT.equals(permission) && actor.roles().contains("BRAND_ADMIN");
    }

    public static IdentityActor current() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof IdentityActor actor)) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Verified Identity authentication required");
        }
        return actor;
    }
}
