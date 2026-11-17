package com.scentstation.identity.tenant;

public interface TenantProfileCache {
    TenantProfileSnapshot get(Long tenantId);

    void put(Long tenantId, TenantProfileSnapshot profile);

    void evict(Long tenantId);
}
