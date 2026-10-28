package com.scentstation.identity.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantRegistry registry;

    public TenantController(TenantRegistry registry) {
        this.registry = registry;
    }

    public record TenantRequest(
            @NotBlank @Size(max = 80) String tenantCode,
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String brandName,
            @Size(max = 10000) String configurationJson) {
    }

    public record TenantUpdateRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Size(max = 120) String brandName,
            @Size(max = 10000) String configurationJson) {
    }

    public record TenantStatusRequest(boolean active) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(@Valid @RequestBody TenantRequest request) {
        return registry.create(
                request.tenantCode(),
                request.name(),
                request.brandName(),
                request.configurationJson());
    }

    @GetMapping
    public List<Tenant> findAll() {
        return registry.findAll();
    }

    @GetMapping("/{id}")
    public Tenant findById(@PathVariable UUID id) {
        return registry.findById(id);
    }

    @PutMapping("/{id}")
    public Tenant update(
            @PathVariable UUID id,
            @Valid @RequestBody TenantUpdateRequest request) {
        return registry.update(
                id,
                request.name(),
                request.brandName(),
                request.configurationJson());
    }

    @PatchMapping("/{id}/status")
    public Tenant setStatus(
            @PathVariable UUID id,
            @RequestBody TenantStatusRequest request) {
        return registry.setActive(id, request.active());
    }
}
