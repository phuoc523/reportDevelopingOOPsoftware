package com.scentstation.integration.identity.dto;

import java.util.Set;

public record IdentityPrincipal(
        Long id,
        String username,
        Long tenantId,
        Set<String> roles,
        Set<String> grants,
        Set<Long> locationIds,
        Set<Long> machineIds
) {
}
