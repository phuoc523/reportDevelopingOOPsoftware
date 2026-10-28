package com.scentstation.identity.dto;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;

public record AdminUserResponse(
        Long id,
        String username,
        String email,
        String fullName,
        Role role,
        boolean active,
        boolean locked
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.isLocked()
        );
    }
}
