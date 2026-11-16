package com.scentstation.identity.tenant;

import java.io.Serializable;

/** Cache-safe, non-sensitive tenant profile fields; excludes live status and authorization data. */
public record TenantProfileSnapshot(
    String logoUrl,
    String description,
    String contactEmail,
    String contactPhone,
    String kioskContent
) implements Serializable {
    private static final long serialVersionUID = 1L;
}
