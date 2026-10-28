package com.scentstation.identity.controller;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) { return authService.register(request); }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }

    @GetMapping("/health")
    public String health() { return "Identity Service is running"; }
}
