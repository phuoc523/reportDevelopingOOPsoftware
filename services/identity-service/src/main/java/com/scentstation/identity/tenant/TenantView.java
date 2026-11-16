package com.scentstation.identity.tenant;

/** Tenant display snapshot. JSON properties match the existing GET response. */
public record TenantView(
    Long id, String code, String name, String logoUrl, String description,
    String contactEmail, String contactPhone, String kioskContent, Tenant.Status status
) {
    public static TenantView from(Tenant tenant) {
        return new TenantView(
            tenant.getId(), tenant.getCode(), tenant.getName(),
            tenant.getLogoUrl(), tenant.getDescription(), tenant.getContactEmail(),
            tenant.getContactPhone(), tenant.getKioskContent(), tenant.getStatus());
    }
}
