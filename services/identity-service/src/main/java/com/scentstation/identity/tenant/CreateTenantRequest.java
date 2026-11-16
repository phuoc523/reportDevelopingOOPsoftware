package com.scentstation.identity.tenant;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CreateTenantRequest(
    @NotBlank @Pattern(regexp="[a-zA-Z0-9][a-zA-Z0-9_-]{2,63}") String code,
    @NotBlank @Size(max=160) String name,
    @Valid TenantProfile profile
) {}
