package com.scentstation.identity.security;

import com.scentstation.identity.entity.StaffRole;
import java.util.Set;

public record StaffPrincipal(
        String id,
        String username,
        String tenantId,
        Set<StaffRole> roles) {

    public boolean hasRole(StaffRole role) {
        return roles.contains(role);
    }
}
