package com.scentstation.reporting.dto.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityActor(Long id, String username, Long tenantId, Set<String> roles,
        Set<String> grants, Set<Long> locationIds, Set<Long> machineIds) {}
