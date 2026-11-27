package com.scentstation.identity.dto;

public record StaffRegisterResponse(
    Long userId,
    String username,
    Long tenantId,
    String requestedRole,
    String status,
    String message,
    String token
) {}
