package com.scentstation.identity.rbac;

import com.scentstation.identity.entity.User;
import com.scentstation.identity.entity.Role;
import java.util.Set;

public record AccountPrincipal(
    Long id,
    String username,
    Long tenantId,
    Set<com.scentstation.identity.entity.Role> roles,
    Set<Permission> grants,
    Set<Long> locationIds,
    Set<Long> machineIds
) {
    public static AccountPrincipal from(User user) {
        Set<Role> roles = RolePermissions.effectiveRoles(user.getRoles(), user.getRole());
        return new AccountPrincipal(user.getId(), user.getUsername(), user.getTenantId(), roles,
            RolePermissions.effectivePermissions(roles, user.getPermissionGrants()), Set.copyOf(user.getLocationIds()),
            Set.copyOf(user.getMachineIds()));
    }
}
