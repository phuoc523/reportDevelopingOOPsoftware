package com.scentstation.identity.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(@Size(max=160) String name, @Valid TenantProfile profile) {}
