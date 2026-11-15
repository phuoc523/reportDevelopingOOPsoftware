package com.scentstation.identity.service;

import com.scentstation.identity.dto.StaffUserRequests.CreateRequest;
import com.scentstation.identity.dto.StaffUserRequests.UpdateRequest;
import com.scentstation.identity.dto.StaffUserResponse;
import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import com.scentstation.identity.security.StaffPrincipal;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffUserLifecycleService {

    private final StaffUserRepository staffUsers;
    private final TenantRepository tenants;
    private final PasswordEncoder passwordEncoder;

    public StaffUserLifecycleService(
            StaffUserRepository staffUsers,
            TenantRepository tenants,
            PasswordEncoder passwordEncoder) {
        this.staffUsers = staffUsers;
        this.tenants = tenants;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<StaffUserResponse> findAll(StaffPrincipal actor) {
        requireManager(actor);
        List<StaffUser> results = isPlatformAdmin(actor)
                ? staffUsers.findAllByOrderByUsernameAsc()
                : staffUsers.findAllByTenantIdOrderByUsernameAsc(actor.tenantId());
        return results.stream().map(StaffUserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public StaffUserResponse findById(StaffPrincipal actor, String id) {
        requireManager(actor);
        return StaffUserResponse.from(findScoped(actor, id));
    }

    @Transactional
    public StaffUserResponse create(StaffPrincipal actor, CreateRequest request) {
        requireManager(actor);
        String username = normalizeUsername(request.username());
        if (staffUsers.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        Set<StaffRole> roles = Set.copyOf(request.roles());
        checkAssignableRoles(actor, roles);
        String tenantId = resolveTenant(actor, request.tenantId(), roles);

        StaffUser staffUser = new StaffUser(
                username, passwordEncoder.encode(request.password()), tenantId);
        roles.forEach(staffUser::addRole);
        return StaffUserResponse.from(staffUsers.save(staffUser));
    }

    @Transactional
    public StaffUserResponse update(
            StaffPrincipal actor, String id, UpdateRequest request) {
        requireManager(actor);
        StaffUser staffUser = findScoped(actor, id);
        String username = normalizeUsername(request.username());
        if (!staffUser.getUsername().equals(username)
                && staffUsers.existsByUsernameAndIdNot(username, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }

        Set<StaffRole> roles = Set.copyOf(request.roles());
        checkAssignableRoles(actor, roles);
        String tenantId = resolveTenant(actor, request.tenantId(), roles);
        if (!isPlatformAdmin(actor)
                && !staffUser.getTenantId().equals(tenantId)) {
            throw forbidden();
        }

        staffUser.updateUsernameAndTenant(username, tenantId);
        replaceRoles(staffUser, roles);
        return StaffUserResponse.from(staffUsers.save(staffUser));
    }

    @Transactional
    public StaffUserResponse setEnabled(StaffPrincipal actor, String id, boolean enabled) {
        StaffUser staffUser = findScopedManagerTarget(actor, id);
        staffUser.setEnabled(enabled);
        return StaffUserResponse.from(staffUsers.save(staffUser));
    }

    @Transactional
    public StaffUserResponse setLocked(StaffPrincipal actor, String id, boolean locked) {
        StaffUser staffUser = findScopedManagerTarget(actor, id);
        staffUser.setManuallyLocked(locked);
        return StaffUserResponse.from(staffUsers.save(staffUser));
    }

    @Transactional
    public StaffUserResponse replaceRoles(
            StaffPrincipal actor, String id, Set<StaffRole> roles) {
        requireManager(actor);
        StaffUser staffUser = findScoped(actor, id);
        Set<StaffRole> requestedRoles = Set.copyOf(roles);
        checkAssignableRoles(actor, requestedRoles);
        String tenantId = resolveTenant(actor, staffUser.getTenantId(), requestedRoles);
        if (!isPlatformAdmin(actor)
                && !staffUser.getTenantId().equals(tenantId)) {
            throw forbidden();
        }
        replaceRoles(staffUser, requestedRoles);
        return StaffUserResponse.from(staffUsers.save(staffUser));
    }

    @Transactional
    public void resetPassword(StaffPrincipal actor, String id, String password) {
        StaffUser staffUser = findScopedManagerTarget(actor, id);
        staffUser.setPasswordHash(passwordEncoder.encode(password));
        staffUsers.save(staffUser);
    }

    private StaffUser findScopedManagerTarget(StaffPrincipal actor, String id) {
        requireManager(actor);
        return findScoped(actor, id);
    }

    private StaffUser findScoped(StaffPrincipal actor, String id) {
        return (isPlatformAdmin(actor)
                ? staffUsers.findWithRolesById(id)
                : staffUsers.findWithRolesByIdAndTenantId(id, actor.tenantId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Staff user not found"));
    }

    private String resolveTenant(
            StaffPrincipal actor, String requestedTenantId, Set<StaffRole> roles) {
        String tenantId;
        if (isPlatformAdmin(actor)) {
            tenantId = requestedTenantId;
        } else {
            tenantId = actor.tenantId();
            if (tenantId == null
                    || (requestedTenantId != null && !tenantId.equals(requestedTenantId))) {
                throw forbidden();
            }
        }

        boolean platformRole = roles.contains(StaffRole.PLATFORM_SUPER_ADMIN);
        if (platformRole) {
            if (roles.size() != 1 || tenantId != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "PLATFORM_SUPER_ADMIN must be the only role and have no tenant");
            }
        } else {
            if (tenantId == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "A tenant is required for this role");
            }
            if (tenants.findById(tenantId)
                    .filter(tenant -> tenant.isActive()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Tenant is unavailable");
            }
        }
        return tenantId;
    }

    private void checkAssignableRoles(StaffPrincipal actor, Set<StaffRole> roles) {
        if (roles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "At least one role is required");
        }
        if (!isPlatformAdmin(actor)
                && (roles.contains(StaffRole.PLATFORM_SUPER_ADMIN)
                    || roles.contains(StaffRole.BRAND_ADMIN))) {
            throw forbidden();
        }
    }

    private void requireManager(StaffPrincipal actor) {
        if (actor == null
                || (!isPlatformAdmin(actor) && !actor.hasRole(StaffRole.BRAND_ADMIN))) {
            throw forbidden();
        }
        if (!isPlatformAdmin(actor) && actor.tenantId() == null) {
            throw forbidden();
        }
    }

    private boolean isPlatformAdmin(StaffPrincipal actor) {
        return actor != null && actor.hasRole(StaffRole.PLATFORM_SUPER_ADMIN);
    }

    private void replaceRoles(StaffUser staffUser, Set<StaffRole> roles) {
        staffUser.updateRoles(roles);
    }

    private String normalizeUsername(String username) {
        String normalized = username.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid username");
        }
        return normalized;
    }

    private ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Insufficient permission");
    }
}
