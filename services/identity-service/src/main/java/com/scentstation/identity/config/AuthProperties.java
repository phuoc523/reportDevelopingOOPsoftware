package com.scentstation.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    @Min(1) @Max(1440)
    private long sessionTtlMinutes = 30;
    @Min(1) @Max(100)
    private int maxFailedAttempts = 5;
    @Min(1) @Max(1440)
    private long lockMinutes = 15;

    public long getSessionTtlMinutes() { return sessionTtlMinutes; }
    public void setSessionTtlMinutes(long value) { sessionTtlMinutes = value; }
    public int getMaxFailedAttempts() { return maxFailedAttempts; }
    public void setMaxFailedAttempts(int value) { maxFailedAttempts = value; }
    public long getLockMinutes() { return lockMinutes; }
    public void setLockMinutes(long value) { lockMinutes = value; }
}
