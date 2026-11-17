package com.scentstation.identity.tenant;

public record TenantView(
        Long id,
        String code,
        String name,
        String logoUrl,
        String description,
        String contactEmail,
        String contactPhone,
        String kioskContent,
        Tenant.Status status
) {
    public static TenantView from(TenantProfileSnapshot profile, Tenant.Status liveStatus) {
        return new TenantView(
                profile.id(),
                profile.code(),
                profile.name(),
                profile.logoUrl(),
                profile.description(),
                profile.contactEmail(),
                profile.contactPhone(),
                profile.kioskContent(),
                liveStatus
        );
    }
}
