package com.scentstation.identity.controller;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.ChangePasswordRequest;
import com.scentstation.identity.dto.ChangeUsernameRequest;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.PublicTenantSummary;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.dto.StaffRegisterRequest;
import com.scentstation.identity.dto.StaffRegisterResponse;
import com.scentstation.identity.dto.StaffRoleRequest;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccessPolicy access;
    public AuthController(AuthService auth, AccessPolicy access) { this.auth = auth; this.access = access; }

    @GetMapping("/tenants")
    public List<PublicTenantSummary> activeTenants() { return auth.listActiveTenants(); }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }

    @PostMapping("/register-staff") @ResponseStatus(HttpStatus.CREATED)
    public StaffRegisterResponse registerStaff(@Valid @RequestBody StaffRegisterRequest r) { return auth.registerStaff(r); }

    @PostMapping("/register-staff-account") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse registerStaffAccount(@Valid @RequestBody RegisterRequest r) { return auth.registerStaffAccount(r); }

    @PostMapping("/staff-role-request")
    public StaffRegisterResponse submitStaffRoleRequest(@Valid @RequestBody StaffRoleRequest r) { return auth.submitStaffRoleRequest(r); }

    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest r) { auth.changeOwnPassword(r); }

    @PostMapping("/change-username")
    public AuthResponse changeUsername(@Valid @RequestBody ChangeUsernameRequest r) { return auth.changeOwnUsername(r); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { auth.logout(); }

    @GetMapping("/me")
    public AccountPrincipal me() { return access.current(); }

    @GetMapping("/health")
    public String health() { return "Identity Service is running"; }
}
