package com.scentstation.identity.tenant;

/** API view assembled from live identity/status fields and cached profile fields. */
public record TenantView(
    Long id,
    String code,
    String name,
    Tenant.Status status,
    String logoUrl,
    String description,
    String contactEmail,
    String contactPhone,
    String kioskContent
) {
    static TenantView from(TenantLiveFields live, TenantProfileSnapshot profile) {
        return new TenantView(live.getId(), live.getCode(), live.getName(), live.getStatus(),
            profile.logoUrl(), profile.description(), profile.contactEmail(),
            profile.contactPhone(), profile.kioskContent());
    }
}
