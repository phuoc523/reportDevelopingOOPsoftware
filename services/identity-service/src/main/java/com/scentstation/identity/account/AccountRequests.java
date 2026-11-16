package com.scentstation.identity.account;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.rbac.Permission;
import jakarta.validation.constraints.*;
import java.util.Set;

public final class AccountRequests {
    private AccountRequests() {}

    public record Create(
        @NotBlank @Size(min=3, max=80) String username,
        @NotBlank @Email @Size(max=160) String email,
        @NotBlank @Size(min=12, max=72) String password,
        @NotBlank @Size(max=120) String fullName,
        Long tenantId,
        @NotEmpty Set<@NotNull Role> roles,
        Set<Long> locationIds,
        Set<Long> machineIds
    ) {}
    public record Profile(
        @NotBlank @Email @Size(max=160) String email,
        @NotBlank @Size(max=120) String fullName
    ) {}
    public record Roles(@NotEmpty Set<@NotNull Role> roles) {}
    public record Grants(@NotNull Set<@NotNull Permission> permissions) {}
    public record Status(@NotNull Boolean active, @NotNull Boolean locked) {}
    public record Password(@NotBlank @Size(min=12, max=72) String newPassword) {}
    public record Scopes(@NotNull Set<Long> locationIds, @NotNull Set<Long> machineIds) {}
}
