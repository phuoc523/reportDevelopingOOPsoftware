package com.scentstation.identity.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRegistry registry;

    public TenantController(TenantRegistry registry) {
        this.registry = registry;
    }

    public record TenantRequest(@NotBlank @Size(max = 80) String tenantCode) {
    }

    public record TenantUpdateRequest(@NotBlank @Size(max = 80) String tenantCode) {
    }

    public record TenantStatusRequest(boolean active) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(@Valid @RequestBody TenantRequest request) {
        return registry.create(request.tenantCode());
    }

    @GetMapping
    public List<Tenant> findAll() {
        return registry.findAll();
    }

    @GetMapping("/{id}")
    public Tenant findById(@PathVariable String id) {
        return registry.findById(id);
    }

    @PutMapping("/{id}")
    public Tenant update(
            @PathVariable String id,
            @Valid @RequestBody TenantUpdateRequest request) {
        return registry.updateCode(id, request.tenantCode());
    }

    @PatchMapping("/{id}/status")
    public Tenant setStatus(
            @PathVariable String id,
            @RequestBody TenantStatusRequest request) {
        return registry.setActive(id, request.active());
    }
}
