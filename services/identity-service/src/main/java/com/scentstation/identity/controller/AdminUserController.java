package com.scentstation.identity.controller;

import com.scentstation.identity.dto.AdminUserResponse;
import com.scentstation.identity.dto.AdminUserRequests.*;
import com.scentstation.identity.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public List<AdminUserResponse> getAllUsers() {
        return authService.adminFindAll();
    }

    @GetMapping("/{id}")
    public AdminUserResponse getUserById(@PathVariable Long id) {
        return authService.adminFindById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserResponse createUser(
            @Valid @RequestBody CreateUserRequest request) {
        return authService.adminCreateUser(request);
    }

    @PutMapping("/{id}")
    public AdminUserResponse updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request) {
        return authService.adminUpdateUser(id, request);
    }

    @PatchMapping("/{id}/active")
    public AdminUserResponse toggleActive(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return authService.adminSetActive(id, request.value());
    }

    @PatchMapping("/{id}/lock")
    public AdminUserResponse toggleLock(
            @PathVariable Long id,
            @Valid @RequestBody StatusRequest request) {
        return authService.adminSetLocked(id, request.value());
    }

    @PutMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.adminResetPassword(id, request.newPassword());
    }
}
