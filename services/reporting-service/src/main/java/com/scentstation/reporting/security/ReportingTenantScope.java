package com.scentstation.reporting.security;

import com.scentstation.reporting.dto.integration.IdentityActor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Resolves tenant filters from the verified Identity actor; request parameters are never trusted. */
public final class ReportingTenantScope {
    private ReportingTenantScope() {}

    public static String resolve(Long requestedTenantId) {
        IdentityActor actor = ReportingPermissions.current();
        if (actor.roles().contains("PLATFORM_SUPER_ADMIN")) {
            return requestedTenantId == null ? null : requestedTenantId.toString();
        }
        if (actor.tenantId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account has no tenant scope");
        }
        if (requestedTenantId != null && !requestedTenantId.equals(actor.tenantId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access another tenant");
        }
        return actor.tenantId().toString();
    }

    public static void verify(String transactionTenantId) {
        IdentityActor actor = ReportingPermissions.current();
        if (actor.roles().contains("PLATFORM_SUPER_ADMIN")) return;
        if (actor.tenantId() == null || transactionTenantId == null
                || !actor.tenantId().toString().equals(transactionTenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found");
        }
    }
}
