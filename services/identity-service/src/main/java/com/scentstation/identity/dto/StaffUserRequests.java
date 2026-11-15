package com.scentstation.identity.dto;

import com.scentstation.identity.entity.StaffRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public final class StaffUserRequests {

    private StaffUserRequests() {
    }

    public record CreateRequest(
            @NotBlank @Size(min = 3, max = 120) String username,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 36) String tenantId,
            @NotEmpty Set<@NotNull StaffRole> roles) {
    }

    public record UpdateRequest(
            @NotBlank @Size(min = 3, max = 120) String username,
            @Size(max = 36) String tenantId,
            @NotEmpty Set<@NotNull StaffRole> roles) {
    }

    public record EnabledRequest(boolean enabled) {
    }

    public record LockRequest(boolean locked) {
    }

    public record RolesRequest(@NotEmpty Set<@NotNull StaffRole> roles) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72) String password) {
    }
}
