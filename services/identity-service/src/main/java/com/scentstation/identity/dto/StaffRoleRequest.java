package com.scentstation.identity.dto;

import com.scentstation.identity.entity.Role;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StaffRoleRequest(
    @NotNull @Positive Long tenantId,
    @NotNull Role requestedRole
) {}
