package com.scentstation.identity.dto;

import java.util.List;

public record StaffAuthResponse(
        String token,
        String tokenType,
        String staffUserId,
        String username,
        List<String> roles,
        String tenantId) {
}
