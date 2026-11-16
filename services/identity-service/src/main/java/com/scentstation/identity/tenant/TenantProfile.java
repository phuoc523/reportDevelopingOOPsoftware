package com.scentstation.identity.tenant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record TenantProfile(
    @Size(max=1024) String logoUrl,
    @Size(max=2000) String description,
    @Email @Size(max=160) String contactEmail,
    @Size(max=40) String contactPhone,
    @Size(max=3000) String kioskContent
) {}
