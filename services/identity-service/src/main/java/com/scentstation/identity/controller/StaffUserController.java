package com.scentstation.identity.controller;

import com.scentstation.identity.dto.StaffUserRequests.CreateRequest;
import com.scentstation.identity.dto.StaffUserRequests.EnabledRequest;
import com.scentstation.identity.dto.StaffUserRequests.LockRequest;
import com.scentstation.identity.dto.StaffUserRequests.ResetPasswordRequest;
import com.scentstation.identity.dto.StaffUserRequests.RolesRequest;
import com.scentstation.identity.dto.StaffUserRequests.UpdateRequest;
import com.scentstation.identity.dto.StaffUserResponse;
import com.scentstation.identity.security.StaffPrincipal;
import com.scentstation.identity.service.StaffUserLifecycleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/staff-users")
public class StaffUserController {

    private final StaffUserLifecycleService lifecycleService;

    public StaffUserController(StaffUserLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    @GetMapping
    public List<StaffUserResponse> findAll(
            @AuthenticationPrincipal StaffPrincipal actor) {
        return lifecycleService.findAll(actor);
    }

    @GetMapping("/{id}")
    public StaffUserResponse findById(
            @AuthenticationPrincipal StaffPrincipal actor,
            @PathVariable String id) {
        return lifecycleService.findById(actor, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StaffUserResponse create(
            @AuthenticationPrincipal StaffPrincipal actor,
            @Valid @RequestBody CreateRequest request) {
        return lifecycleService.create(actor, request);
    }

    @PutMapping("/{id}")
    public StaffUserResponse update(
            @AuthenticationPrincipal StaffPrincipal actor,
            @PathVariable String id,
            @Valid @RequestBody UpdateRequest request) {
        return lifecycleService.update(actor, id, request);
    }

    @PatchMapping("/{id}/enabled")
    public StaffUserResponse setEnabled(
            @AuthenticationPrincipal StaffPrincipal actor,
            @PathVariable String id,
            @Valid @RequestBody EnabledRequest request) {
        return lifecycleService.setEnabled(actor, id, request.enabled());
    }

    @PatchMapping("/{id}/lock")
    public StaffUserResponse setLocked(
            @AuthenticationPrincipal StaffPrincipal actor,
            @PathVariable String id,
            @Valid @RequestBody LockRequest request) {
        return lifecycleService.setLocked(actor, id, request.locked());
    }

    @PutMapping("/{id}/roles")
    public StaffUserResponse replaceRoles(
            @AuthenticationPrincipal StaffPrincipal actor,
            @PathVariable String id,
            @Valid @RequestBody RolesRequest request) {
        return lifecycleService.replaceRoles(actor, id, request.roles());
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @AuthenticationPrincipal StaffPrincipal actor,
            @PathVariable String id,
            @Valid @RequestBody ResetPasswordRequest request) {
        lifecycleService.resetPassword(actor, id, request.password());
    }
}
