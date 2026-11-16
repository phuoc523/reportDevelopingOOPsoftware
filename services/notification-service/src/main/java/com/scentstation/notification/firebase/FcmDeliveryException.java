package com.scentstation.notification.firebase;

import java.util.List;

public class FcmDeliveryException extends RuntimeException {
    private final List<String> retryableTokens;

    public FcmDeliveryException(Throwable cause, List<String> retryableTokens) {
        super("Firebase Cloud Messaging delivery failed", cause);
        this.retryableTokens = List.copyOf(retryableTokens);
    }

    public FcmDeliveryException(Throwable cause) {
        this(cause, List.of());
    }

    public List<String> getRetryableTokens() { return retryableTokens; }
}
