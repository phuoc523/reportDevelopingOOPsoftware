package com.scentstation.identity.tenant;

/** Optional display-data cache, never used to make authorization decisions. */
public interface TenantViewCache {
    TenantView get(Long tenantId);
    void put(Long tenantId, TenantView view);
    void evict(Long tenantId);
}
