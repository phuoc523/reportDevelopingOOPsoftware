package com.scentstation.gateway;

public class RateLimitIdentityUnavailableException extends RuntimeException {
    public RateLimitIdentityUnavailableException(String message) {
        super(message);
    }

    public RateLimitIdentityUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
