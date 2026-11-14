package com.scentstation.notification.security;

public class NotificationAuthorizationUnavailableException extends RuntimeException {
    public NotificationAuthorizationUnavailableException() {
        super("Identity authorization is unavailable");
    }
}
