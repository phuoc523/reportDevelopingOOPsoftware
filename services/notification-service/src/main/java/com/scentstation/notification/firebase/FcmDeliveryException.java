package com.scentstation.notification.firebase;

public class FcmDeliveryException extends RuntimeException {
    public FcmDeliveryException(Throwable cause) {
        super("Firebase Cloud Messaging delivery failed", cause);
    }
}
