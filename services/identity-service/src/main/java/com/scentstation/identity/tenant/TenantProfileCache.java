package com.scentstation.identity.tenant;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantProfileCache {
    private final TenantRepository tenants;

    public TenantProfileCache(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Cacheable(cacheNames = "identity-tenant-profile", key = "#p0", sync = true)
    public TenantProfileSnapshot get(Long tenantId) {
        Tenant tenant = tenants.findById(tenantId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        return snapshot(tenant);
    }

    static TenantProfileSnapshot snapshot(Tenant tenant) {
        return new TenantProfileSnapshot(tenant.getLogoUrl(), tenant.getDescription(),
            tenant.getContactEmail(), tenant.getContactPhone(), tenant.getKioskContent());
    }

    @CacheEvict(cacheNames = "identity-tenant-profile", allEntries = true)
    public void invalidateAll() {
        // Status and authorization fields are not cached.
    }
}
