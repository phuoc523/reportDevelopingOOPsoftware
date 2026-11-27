package com.scentstation.identity.dto;

import com.scentstation.identity.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StaffRegisterRequest(
    @NotBlank @Size(min = 3, max = 80) String username,
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(min = 4, max = 72) String password,
    @NotBlank @Size(max = 120) String fullName,
    @NotNull @Positive Long tenantId,
    @NotNull Role requestedRole
) {}
