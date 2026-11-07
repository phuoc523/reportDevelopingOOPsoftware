package com.scentstation.identity.tenant;

import com.scentstation.identity.rbac.AccessPolicy;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class TenantController {
    private final TenantService service;
    private final AccessPolicy access;
    public TenantController(TenantService service, AccessPolicy access) { this.service = service; this.access = access; }

    @GetMapping("/api/platform/tenants")
    @PreAuthorize("@accessPolicy.has('TENANT_MANAGE')")
    public List<Tenant> all() { return service.all(); }

    @PostMapping("/api/platform/tenants") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@accessPolicy.has('TENANT_MANAGE')")
    public Tenant create(@Valid @RequestBody CreateTenantRequest r) { return service.create(r); }

    @GetMapping("/api/platform/tenants/{id}")
    @PreAuthorize("@accessPolicy.has('TENANT_MANAGE')")
    public Tenant get(@PathVariable Long id) { return service.require(id); }

    @PatchMapping("/api/platform/tenants/{id}")
    @PreAuthorize("@accessPolicy.has('TENANT_MANAGE')")
    public Tenant update(@PathVariable Long id, @Valid @RequestBody UpdateTenantRequest r) {
        return service.platformUpdate(id, r);
    }

    @PatchMapping("/api/platform/tenants/{id}/status")
    @PreAuthorize("@accessPolicy.has('TENANT_MANAGE')")
    public Tenant status(@PathVariable Long id, @Valid @RequestBody TenantStatusRequest r) {
        return service.status(id, r.status());
    }

    @GetMapping("/api/brand/tenant")
    @PreAuthorize("@accessPolicy.has('BRAND_PROFILE_READ')")
    public Tenant brand() { return service.require(access.current().tenantId()); }

    @PatchMapping("/api/brand/tenant")
    @PreAuthorize("@accessPolicy.has('BRAND_PROFILE_EDIT')")
    public Tenant brandUpdate(@Valid @RequestBody UpdateTenantRequest r) { return service.brandUpdate(r); }

    public record TenantStatusRequest(@jakarta.validation.constraints.NotNull Tenant.Status status) {}
}
