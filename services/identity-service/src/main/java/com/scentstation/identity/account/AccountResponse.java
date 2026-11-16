package com.scentstation.identity.account;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.rbac.Permission;
import com.scentstation.identity.rbac.RolePermissions;
import java.util.Set;

public record AccountResponse(
    Long id, String username, String email, String fullName, Long tenantId,
    Set<Role> roles, Set<Permission> explicitGrants,
    Set<Long> locationIds, Set<Long> machineIds,
    boolean active, boolean locked
) {
    public static AccountResponse from(User u) {
        return new AccountResponse(u.getId(), u.getUsername(), u.getEmail(), u.getFullName(), u.getTenantId(),
            RolePermissions.effectiveRoles(u.getRoles(), u.getRole()), Set.copyOf(u.getPermissionGrants()),
            Set.copyOf(u.getLocationIds()), Set.copyOf(u.getMachineIds()), u.isActive(), u.isLocked());
    }
}
