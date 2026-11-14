package com.scentstation.notification.security;

import org.springframework.http.HttpStatus;

public class NotificationAuthorizationException extends RuntimeException {
    private final HttpStatus status;

    public NotificationAuthorizationException(HttpStatus status) {
        super("Caller is not authorized for this notification operation");
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
