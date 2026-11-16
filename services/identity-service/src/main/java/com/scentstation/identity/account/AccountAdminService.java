package com.scentstation.identity.account;

import com.scentstation.identity.account.AccountRequests.*;
import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.rbac.Permission;
import com.scentstation.identity.rbac.RolePermissions;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.tenant.Tenant;
import com.scentstation.identity.tenant.TenantService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountAdminService {
    private static final Set<Role> BRAND_ASSIGNABLE = EnumSet.of(
        Role.OPERATIONS_MANAGER, Role.TECHNICIAN, Role.INVENTORY_STAFF, Role.VIEWER);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TenantService tenants;
    private final AccessPolicy access;
    private final AuditService audit;

    public AccountAdminService(UserRepository users, PasswordEncoder encoder,
                               TenantService tenants, AccessPolicy access, AuditService audit) {
        this.users = users; this.encoder = encoder; this.tenants = tenants;
        this.access = access; this.audit = audit;
    }

    private User get(Long id) {
        return users.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private void requireAdmin(boolean platform) {
        if (platform ? !access.platform() : !access.has(Permission.BRAND_USER_MANAGE))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator permission required");
    }

    private Long effectiveTenant(Long requested, boolean platform) {
        requireAdmin(platform);
        if (platform) return requested;
        Long own = access.current().tenantId();
        if (own == null || (requested != null && !own.equals(requested)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Wrong tenant");
        tenants.requireActive(own);
        return own;
    }

    private void checkRoles(Set<Role> requested, boolean platform) {
        if (requested == null || requested.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Roles required");
        if (!platform && !BRAND_ASSIGNABLE.containsAll(requested))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign privileged role");
        if (requested.contains(Role.ADMIN) || requested.contains(Role.STAFF))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Legacy roles cannot be assigned to new accounts");
        if (requested.contains(Role.PLATFORM_SUPER_ADMIN) && requested.size() != 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform administrator must have a single platform role");
    }

    private User target(Long id, boolean platform) {
        requireAdmin(platform);
        User target = get(id);
        if (!platform && !Objects.equals(access.current().tenantId(), target.getTenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot manage another tenant");
        if (!platform) tenants.requireActive(target.getTenantId());
        if (!platform && RolePermissions.effectiveRoles(target.getRoles(), target.getRole()).stream()
            .anyMatch(r -> !BRAND_ASSIGNABLE.contains(r)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot manage a privileged account");
        return target;
    }

    @Transactional
    public AccountResponse create(Create request, boolean platform) {
        Long tenantId = effectiveTenant(request.tenantId(), platform);
        checkRoles(request.roles(), platform);
        if (request.roles().contains(Role.PLATFORM_SUPER_ADMIN)) {
            if (tenantId != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform account cannot have tenant");
        } else if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brand account requires tenant");
        }
        if (tenantId != null) tenants.requireActive(tenantId);
        if (users.existsByUsername(request.username().trim()) || users.existsByEmail(request.email().trim().toLowerCase(Locale.ROOT)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        User u = new User(request.username().trim(), request.email().trim().toLowerCase(Locale.ROOT),
            encoder.encode(request.password()), request.fullName().trim(), request.roles().iterator().next());
        u.setRoles(request.roles());
        u.setTenantId(tenantId);
        u.setLocationIds(validIds(request.locationIds()));
        u.setMachineIds(validIds(request.machineIds()));
        u = users.save(u);
        audit.record(access.current().id(), tenantId, "ACCOUNT_CREATED", "USER", id(u), null, u.getRoles().toString());
        return AccountResponse.from(u);
    }

    public List<AccountResponse> list(boolean platform, Long tenantId) {
        requireAdmin(platform);
        List<User> result = platform
            ? (tenantId == null ? users.findAll() : users.findByTenantId(tenantId))
            : users.findByTenantId(access.current().tenantId());
        return result.stream().map(AccountResponse::from).toList();
    }

    public AccountResponse detail(Long id, boolean platform) {
        return AccountResponse.from(target(id, platform));
    }

    @Transactional
    public AccountResponse updateProfile(Long id, Profile r, boolean platform) {
        User u = target(id, platform);
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (!u.getEmail().equals(email) && users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        u.setEmail(email);
        u.setFullName(r.fullName().trim());
        audit.record(access.current().id(), u.getTenantId(), "ACCOUNT_PROFILE_UPDATED", "USER", id(u), null, "profile fields updated");
        return AccountResponse.from(u);
    }

    @Transactional
    public AccountResponse roles(Long id, Roles r, boolean platform) {
        User u = target(id, platform);
        checkRoles(r.roles(), platform);
        if (!platform && !BRAND_ASSIGNABLE.containsAll(r.roles()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot assign privileged role");
        if (r.roles().contains(Role.PLATFORM_SUPER_ADMIN) && u.getTenantId() != null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform role requires platform account");
        if (!r.roles().contains(Role.PLATFORM_SUPER_ADMIN) && u.getTenantId() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Brand role requires tenant");
        if (u.getId().equals(access.current().id()) && !r.roles().equals(u.getRoles()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot change own roles");
        Set<Role> before = RolePermissions.effectiveRoles(u.getRoles(), u.getRole());
        u.setRoles(r.roles());
        audit.record(access.current().id(), u.getTenantId(), "ROLES_CHANGED", "USER", id(u), before.toString(), u.getRoles().toString());
        return AccountResponse.from(u);
    }

    @Transactional
    public AccountResponse grants(Long id, Grants r, boolean platform) {
        User u = target(id, platform);
        if (r.permissions().stream().anyMatch(RolePermissions::isPlatformOnly))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform privileges are role-controlled, not delegated");
        if (!platform && r.permissions().stream().anyMatch(p -> !RolePermissions.mayBrandDelegate(p)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only platform administrator may grant sensitive permissions");
        Set<Permission> before = Set.copyOf(u.getPermissionGrants());
        u.setPermissionGrants(r.permissions());
        audit.record(access.current().id(), u.getTenantId(), "GRANTS_CHANGED", "USER", id(u), before.toString(), r.permissions().toString());
        return AccountResponse.from(u);
    }

    @Transactional
    public AccountResponse status(Long id, Status r, boolean platform) {
        User u = target(id, platform);
        if (u.getId().equals(access.current().id()) && (!r.active() || r.locked()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot deactivate or lock own account");
        if (u.isActive() != r.active()) u.setActive(r.active());
        if (u.isLocked() != r.locked()) u.setLocked(r.locked());
        audit.record(access.current().id(), u.getTenantId(), "ACCOUNT_STATUS_CHANGED", "USER", id(u), null,
            "active=" + u.isActive() + ",locked=" + u.isLocked());
        return AccountResponse.from(u);
    }

    @Transactional
    public AccountResponse scopes(Long id, Scopes r, boolean platform) {
        User u = target(id, platform);
        u.setLocationIds(validIds(r.locationIds()));
        u.setMachineIds(validIds(r.machineIds()));
        audit.record(access.current().id(), u.getTenantId(), "SCOPES_CHANGED", "USER", id(u), null, "updated");
        return AccountResponse.from(u);
    }

    @Transactional
    public void resetPassword(Long id, Password r, boolean platform) {
        User u = target(id, platform);
        u.setPassword(encoder.encode(r.newPassword()));
        audit.record(access.current().id(), u.getTenantId(), "PASSWORD_RESET", "USER", id(u), null, "reset");
    }

    private static String id(User u) { return u.getId().toString(); }
    private static Set<Long> validIds(Set<Long> ids) {
        if (ids == null) return Set.of();
        if (ids.contains(null) || ids.stream().anyMatch(x -> x <= 0))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid operational scope ID");
        return ids;
    }
}
