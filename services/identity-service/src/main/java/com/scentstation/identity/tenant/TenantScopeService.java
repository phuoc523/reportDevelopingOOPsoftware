package com.scentstation.identity.tenant;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.rbac.RbacException;
import com.scentstation.identity.rbac.Role;
import com.scentstation.identity.repository.StaffUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Tenant IDs in this service come exclusively from validated opaque sessions. */
@Service
public class TenantScopeService {
    private final StaffUserRepository users;

    public TenantScopeService(StaffUserRepository users) {
        this.users = users;
    }

    public AuthDto.Identity authenticated(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthDto.Identity actor)) {
            throw new RbacException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        return actor;
    }

    public boolean isPlatformAdmin(AuthDto.Identity actor) {
        return actor.roles() != null && actor.roles().contains(Role.PLATFORM_SUPER_ADMIN.name());
    }

    /** No trust is placed in tenant headers, path values or JSON payloads. */
    public String requireTenant(AuthDto.Identity actor, String requestedTenantId) {
        if (requestedTenantId == null || requestedTenantId.isBlank()) {
            throw new RbacException(HttpStatus.BAD_REQUEST, "TENANT_ID_REQUIRED");
        }
        if (isPlatformAdmin(actor)) {
            // Explicit, RBAC-authorized platform cross-tenant exception.
            return requestedTenantId;
        }
        if (actor.tenantId() == null || !Objects.equals(actor.tenantId(), requestedTenantId)) {
            throw new RbacException(HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED");
        }
        return actor.tenantId();
    }

    /** Frontend-supplied context is only validated; NEVER used to determine authorization scope. */
    public void requireMatchingHints(AuthDto.Identity actor, String tenantHeader, String tenantQuery) {
        if (tenantHeader != null) requireTenant(actor, tenantHeader);
        if (tenantQuery != null) requireTenant(actor, tenantQuery);
        if (tenantHeader != null && tenantQuery != null && !tenantHeader.equals(tenantQuery)) {
            throw new RbacException(HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED");
        }
    }

    /** Database-owned tenant is checked during lookup, not after an unscoped read. */
    @Transactional(readOnly = true)
    public StaffUser findVisibleUser(AuthDto.Identity actor, String userId, String tenantHint) {
        if (tenantHint != null) requireTenant(actor, tenantHint);
        if (isPlatformAdmin(actor)) {
            StaffUser user = users.findById(userId)
                    .orElseThrow(() -> new RbacException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
            String owningTenant = user.getTenant() == null ? null : user.getTenant().getId();
            if (tenantHint != null && !tenantHint.equals(owningTenant)) {
                throw new RbacException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND");
            }
            return user;
        }
        if (actor.tenantId() == null) {
            throw new RbacException(HttpStatus.FORBIDDEN, "TENANT_SCOPE_DENIED");
        }
        // A 404 for cross-tenant IDs avoids disclosing that another tenant's user exists.
        return users.findWithinTenant(userId, actor.tenantId())
                .orElseThrow(() -> new RbacException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
    }
}
