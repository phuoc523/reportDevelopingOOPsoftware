package com.scentstation.identity.tenant;

public record TenantProfileSnapshot(
        Long id,
        String code,
        String name,
        String logoUrl,
        String description,
        String contactEmail,
        String contactPhone,
        String kioskContent
) {
    public static TenantProfileSnapshot from(Tenant tenant) {
        return new TenantProfileSnapshot(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getLogoUrl(),
                tenant.getDescription(),
                tenant.getContactEmail(),
                tenant.getContactPhone(),
                tenant.getKioskContent()
        );
    }
}
