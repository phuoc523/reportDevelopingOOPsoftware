package com.scentstation.identity.controller;

import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.StaffAuthResponse;
import com.scentstation.identity.service.StaffAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/staff")
public class StaffAuthController {

    private final StaffAuthService staffAuthService;

    public StaffAuthController(StaffAuthService staffAuthService) {
        this.staffAuthService = staffAuthService;
    }

    @PostMapping("/login")
    public StaffAuthResponse login(@Valid @RequestBody LoginRequest request) {
        return staffAuthService.login(request);
    }
}
