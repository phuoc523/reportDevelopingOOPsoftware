package com.scentstation.notification.exception;

public class DeviceRegistrationNotFoundException extends RuntimeException {
    public DeviceRegistrationNotFoundException() {
        super("Device registration was not found");
    }
}
