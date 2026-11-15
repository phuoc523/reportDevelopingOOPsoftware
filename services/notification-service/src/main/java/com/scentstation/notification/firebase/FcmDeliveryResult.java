package com.scentstation.notification.firebase;

import java.util.List;

public record FcmDeliveryResult(
    int requestedCount,
    int successCount,
    int failureCount,
    List<String> invalidTokens
) {
    public FcmDeliveryResult {
        invalidTokens = List.copyOf(invalidTokens);
    }

    public static FcmDeliveryResult empty() {
        return new FcmDeliveryResult(0, 0, 0, List.of());
    }
}
