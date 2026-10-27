package com.scentstation.identity.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;

public final class AuthDto {
    private AuthDto() {}

    public record LoginRequest(
            @NotBlank @Size(max = 120) String username,
            @NotBlank @Size(max = 256) String password) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 120) String username,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {}

    public record RegisterResponse(String userId, String username, String email) {}

    public record Identity(String userId, String username, String tenantId, Set<String> roles) {}

    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt, Identity user) {}

    public record ErrorResponse(String code, String message) {}
}
