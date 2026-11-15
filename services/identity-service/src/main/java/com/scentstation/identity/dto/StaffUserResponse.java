package com.scentstation.identity.dto;

import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import java.time.LocalDateTime;
import java.util.List;

public record StaffUserResponse(
        String id,
        String username,
        String tenantId,
        List<StaffRole> roles,
        boolean enabled,
        boolean manuallyLocked,
        int failedAttempts,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt) {

    public static StaffUserResponse from(StaffUser user) {
        return new StaffUserResponse(
                user.getId(),
                user.getUsername(),
                user.getTenantId(),
                user.getRoles().stream()
                        .map(role -> StaffRole.valueOf(role.getId().getRoleName()))
                        .sorted()
                        .toList(),
                user.isEnabled(),
                user.isManuallyLocked(),
                user.getFailedAttempts(),
                user.getLockedUntil(),
                user.getLastLoginAt());
    }
}
