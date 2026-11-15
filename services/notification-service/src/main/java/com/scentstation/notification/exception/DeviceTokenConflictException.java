package com.scentstation.notification.exception;

public class DeviceTokenConflictException extends RuntimeException {
    public DeviceTokenConflictException() {
        super("Firebase token is already registered to another active device");
    }
}
