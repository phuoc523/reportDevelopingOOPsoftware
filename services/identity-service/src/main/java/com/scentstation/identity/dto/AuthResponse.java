package com.scentstation.identity.dto;

public record AuthResponse(String token, String tokenType, Long userId, String username, String role) {}
