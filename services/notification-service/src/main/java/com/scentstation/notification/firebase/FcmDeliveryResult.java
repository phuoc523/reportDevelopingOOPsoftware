package com.scentstation.notification.firebase;

import java.util.List;

public record FcmDeliveryResult(
    int requestedCount,
    int successCount,
    int failureCount,
    List<String> invalidTokens,
    List<String> retryableTokens
) {
    public FcmDeliveryResult {
        invalidTokens = List.copyOf(invalidTokens);
        retryableTokens = List.copyOf(retryableTokens);
    }

    public FcmDeliveryResult(int requestedCount, int successCount, int failureCount,
                             List<String> invalidTokens) {
        this(requestedCount, successCount, failureCount, invalidTokens, List.of());
    }

    public static FcmDeliveryResult empty() {
        return new FcmDeliveryResult(0, 0, 0, List.of(), List.of());
    }
}
