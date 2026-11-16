package com.scentstation.identity.controller;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccessPolicy access;
    public AuthController(AuthService auth, AccessPolicy access) { this.auth = auth; this.access = access; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { auth.logout(); }

    @GetMapping("/me")
    public AccountPrincipal me() { return access.current(); }

    @GetMapping("/health")
    public String health() { return "Identity Service is running"; }
}
