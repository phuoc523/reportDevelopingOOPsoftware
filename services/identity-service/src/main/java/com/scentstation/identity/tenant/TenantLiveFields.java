package com.scentstation.identity.tenant;

/** Live tenant identity/status fields; status is deliberately never cached. */
public interface TenantLiveFields {
    Long getId();
    String getCode();
    String getName();
    Tenant.Status getStatus();
}
