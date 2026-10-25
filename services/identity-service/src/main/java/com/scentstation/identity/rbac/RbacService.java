package com.scentstation.identity.rbac;

import com.scentstation.identity.auth.AuthDto;
import com.scentstation.identity.auth.SessionService;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class RbacService {
    private final StaffUserRepository users;
    private final RoleAssignmentAuditRepository audits;
    private final SessionService sessions;
    private final Clock clock;

    public RbacService(StaffUserRepository users, RoleAssignmentAuditRepository audits,
                       SessionService sessions, Clock clock) {
        this.users = users;
        this.audits = audits;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** RBAC is not enough for a tenant-bound operation: the tenant must match too. */
    public void requirePermission(AuthDto.Identity actor, Permission permission, String resourceTenantId) {
        if (!RbacPolicy.has(actor.roles(), permission)) {
            throw forbidden();
        }
        if (!actor.roles().contains(Role.PLATFORM_SUPER_ADMIN.name())
                && (resourceTenantId == null || !Objects.equals(actor.tenantId(), resourceTenantId))) {
            throw forbidden();
        }
    }

    public void requirePermission(AuthDto.Identity actor, Permission permission) {
        requirePermission(actor, permission, actor.tenantId());
    }

    @Transactional
    public RoleUpdateResponse grant(AuthDto.Identity actor, String targetId, Role role) {
        return modify(actor, targetId, role, true);
    }

    @Transactional
    public RoleUpdateResponse revoke(AuthDto.Identity actor, String targetId, Role role) {
        return modify(actor, targetId, role, false);
    }

    private RoleUpdateResponse modify(AuthDto.Identity actor, String targetId, Role role, boolean grant) {
        requirePermission(actor, grant ? Permission.ROLE_ASSIGN : Permission.ROLE_REVOKE);
        StaffUser target = users.findByIdForRoleUpdate(targetId)
                .orElseThrow(() -> new RbacException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND"));
        String tenantId = target.getTenant() == null ? null : target.getTenant().getId();
        requirePermission(actor, grant ? Permission.ROLE_ASSIGN : Permission.ROLE_REVOKE, tenantId);

        boolean platform = actor.roles().contains(Role.PLATFORM_SUPER_ADMIN.name());
        if (!platform && (role == Role.PLATFORM_SUPER_ADMIN
                || target.getRoles().contains(Role.PLATFORM_SUPER_ADMIN.name()))) {
            throw forbidden();
        }
        // Avoid accidentally removing one's own admin role and locking out administration.
        if (!grant && actor.userId().equals(targetId) &&
                (role == Role.BRAND_ADMIN || role == Role.PLATFORM_SUPER_ADMIN)) {
            throw new RbacException(HttpStatus.CONFLICT, "SELF_ADMIN_REVOKE_FORBIDDEN");
        }
        boolean changed = grant ? target.getRoles().add(role.name()) : target.getRoles().remove(role.name());
        if (!changed) {
            throw new RbacException(HttpStatus.CONFLICT,
                    grant ? "ROLE_ALREADY_ASSIGNED" : "ROLE_NOT_ASSIGNED");
        }
        users.save(target);
        RoleAssignmentAudit audit = audits.save(new RoleAssignmentAudit(
                actor.userId(), targetId, tenantId, grant ? "GRANT" : "REVOKE", role, clock.instant()));
        // Invalidate old role-bearing sessions immediately (including on grant).
        sessions.revokeAllForUser(targetId);
        return new RoleUpdateResponse(targetId, tenantId, Set.copyOf(target.getRoles()),
                grant ? "GRANT" : "REVOKE", role.name(), audit.getId());
    }

    @Transactional(readOnly = true)
    public List<RoleAssignmentAudit> audit(AuthDto.Identity actor) {
        requirePermission(actor, Permission.ROLE_AUDIT_READ);
        if (actor.roles().contains(Role.PLATFORM_SUPER_ADMIN.name())) {
            return audits.findTop50ByOrderByOccurredAtDesc();
        }
        return audits.findTop50ByTargetTenantIdOrderByOccurredAtDesc(actor.tenantId());
    }

    public static RbacException forbidden() {
        return new RbacException(HttpStatus.FORBIDDEN, "ACCESS_DENIED");
    }

    public record RoleUpdateResponse(String userId, String tenantId, Set<String> roles,
                                     String action, String role, String auditId) {}
}
