package com.scentstation.identity.service;

import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.ChangePasswordRequest;
import com.scentstation.identity.dto.ChangeUsernameRequest;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.PublicTenantSummary;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.dto.StaffRegisterRequest;
import com.scentstation.identity.dto.StaffRegisterResponse;
import com.scentstation.identity.dto.StaffRoleRequest;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.RolePermissions;
import com.scentstation.identity.tenant.Tenant;
import com.scentstation.identity.tenant.TenantRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final Set<Role> SELF_REGISTER_STAFF_ROLES = EnumSet.of(
        Role.OPERATIONS_MANAGER, Role.TECHNICIAN, Role.INVENTORY_STAFF);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService audit;
    private final AccessPolicy access;
    private final TenantRepository tenantRepository;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       AuditService audit, AccessPolicy access, TenantRepository tenantRepository) {
        this.users = users; this.passwordEncoder = encoder;
        this.jwtService = jwt; this.audit = audit; this.access = access;
        this.tenantRepository = tenantRepository;
    }

    public List<PublicTenantSummary> listActiveTenants() {
        return tenantRepository.findAll().stream()
            .filter(t -> t.getStatus() == Tenant.Status.ACTIVE)
            .map(t -> new PublicTenantSummary(t.getId(), t.getCode(), t.getName()))
            .toList();
    }

    @Transactional
    public StaffRegisterResponse registerStaff(StaffRegisterRequest request) {
        if (!SELF_REGISTER_STAFF_ROLES.contains(request.requestedRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Chỉ được đăng ký các vai trò nhân viên: Quản lý vận hành, Kỹ thuật viên hoặc Nhân viên kho.");
        }
        requireActiveTenant(request.tenantId());
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại.");
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại.");
        User user = new User(username, email, passwordEncoder.encode(request.password()),
            request.fullName().trim(), request.requestedRole());
        user.setRoles(Set.of(request.requestedRole()));
        user.setTenantId(request.tenantId());
        user.setActive(false);
        user.setLocked(false);
        user.setPendingApproval(true);
        user = users.save(user);
        if (audit != null) {
            audit.record(user.getId(), request.tenantId(), "STAFF_REGISTRATION_REQUESTED", "USER",
                user.getId().toString(), null, request.requestedRole().name());
        }
        return new StaffRegisterResponse(
            user.getId(), user.getUsername(), request.tenantId(), request.requestedRole().name(),
            "PENDING_APPROVAL",
            "Đã gửi yêu cầu đăng ký nhân viên tới Quản trị thương hiệu. Vui lòng chờ phê duyệt vai trò trước khi đăng nhập.",
            null
        );
    }

    @Transactional
    public AuthResponse registerStaffAccount(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại.");
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại.");
        User user = new User(username, email, passwordEncoder.encode(request.password()),
            request.fullName().trim(), Role.STAFF);
        user.setRoles(Set.of(Role.STAFF));
        user.setActive(true);
        user.setLocked(false);
        user.setPendingApproval(false);
        user = users.save(user);
        if (audit != null) {
            audit.record(user.getId(), null, "STAFF_ACCOUNT_REGISTERED", "USER",
                user.getId().toString(), null, "UNASSIGNED_STAFF");
        }
        return response(user);
    }

    @Transactional
    public StaffRegisterResponse submitStaffRoleRequest(StaffRoleRequest request) {
        var actor = access.current();
        if (!SELF_REGISTER_STAFF_ROLES.contains(request.requestedRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Chỉ được đề nghị các vai trò: Quản lý vận hành, Kỹ thuật viên hoặc Nhân viên kho.");
        }
        User user = users.findByIdForUpdate(actor.id()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        if (!user.isActive() || user.isLocked() || user.isPendingApproval()
            || user.getTenantId() != null
            || !RolePermissions.effectiveRoles(user.getRoles(), user.getRole()).equals(Set.of(Role.STAFF))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Only unassigned staff may request a role");
        }
        requireActiveTenant(request.tenantId());
        user.setTenantId(request.tenantId());
        user.setRoles(Set.of(request.requestedRole()));
        user.setPendingApproval(true);
        users.save(user);
        if (audit != null) {
            audit.record(user.getId(), request.tenantId(), "STAFF_ROLE_REQUEST_SUBMITTED", "USER",
                user.getId().toString(), null, request.requestedRole().name());
        }
        return new StaffRegisterResponse(
            user.getId(), user.getUsername(), request.tenantId(), request.requestedRole().name(),
            "PENDING_APPROVAL",
            "Đã gửi phiếu yêu cầu tới Quản trị thương hiệu. Vui lòng chờ phê duyệt vai trò.",
            jwtService.generateToken(user)
        );
    }

    @Transactional
    public AuthResponse changeOwnUsername(ChangeUsernameRequest request) {
        var actor = access.current();
        User user = users.findById(actor.id()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        String nextUsername = request.newUsername().trim();
        if (nextUsername.length() < 3 || nextUsername.length() > 80 || nextUsername.chars().anyMatch(Character::isWhitespace)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên đăng nhập phải từ 3 đến 80 ký tự và không chứa khoảng trắng.");
        }
        String previousUsername = user.getUsername();
        if (previousUsername.equals(nextUsername)) {
            return response(user);
        }
        if (users.existsByUsername(nextUsername)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tên đăng nhập đã tồn tại.");
        }
        user.setUsername(nextUsername);
        users.save(user);
        if (audit != null) {
            audit.record(actor.id(), actor.tenantId(), "USERNAME_CHANGED_SELF", "USER",
                actor.id().toString(), previousUsername, nextUsername);
        }
        return response(user);
    }

    @Transactional
    public void changeOwnPassword(ChangePasswordRequest request) {
        var actor = access.current();
        User user = users.findById(actor.id()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng.");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        users.save(user);
        if (audit != null) {
            audit.record(actor.id(), actor.tenantId(), "PASSWORD_CHANGED_SELF", "USER",
                actor.id().toString(), null, "bcrypt-hash-updated");
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        // Public registration always creates a customer with no elevated tenant role.
        User user = new User(username, email, passwordEncoder.encode(request.password()),
            request.fullName().trim(), Role.CUSTOMER);
        users.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().trim())
            .orElseThrow(() -> {
                if (audit != null) audit.authAttempt(null, null, null, false);
                return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
            });
        if (!user.isActive() || user.isLocked() || !passwordEncoder.matches(request.password(), user.getPassword())) {
            if (audit != null) audit.authAttempt(user.getId(), user.getTenantId(), null, false);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }
        if (audit != null) audit.authAttempt(user.getId(), user.getTenantId(), null, true);
        return response(user);
    }

    @Transactional
    public void logout() {
        var actor = access.current();
        User user = users.findById(actor.id()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        user.revokeSessions(); // Revokes all previously issued JWTs for this account.
        if (audit != null) audit.record(actor.id(), actor.tenantId(), "LOGOUT", "USER",
            actor.id().toString(), null, "tokens-revoked");
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer", user.getId(),
            user.getUsername(), (user.isPendingApproval() ? Role.STAFF : user.getRole()).name());
    }

    private void requireActiveTenant(Long tenantId) {
        Tenant.Status status = tenantRepository.findStatusById(tenantId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Thương hiệu không tồn tại."));
        if (status != Tenant.Status.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thương hiệu hiện đang tạm ngưng.");
        }
    }
}
