package com.scentstation.notification.firebase;

import com.scentstation.notification.service.FirebaseDeviceRegistrationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "notification.firebase", name = "enabled", havingValue = "true")
public class FirebaseNotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(FirebaseNotificationDeliveryService.class);
    private final FirebaseDeviceRegistrationService devices;
    private final FirebaseCloudMessagingProvider provider;

    public FirebaseNotificationDeliveryService(
            FirebaseDeviceRegistrationService devices,
            FirebaseCloudMessagingProvider provider
    ) {
        this.devices = devices;
        this.provider = provider;
    }

    public void deliver(NotificationCreatedEvent event) {
        try {
            FcmDeliveryResult result = send(event);
            log.info("FCM delivery completed notificationId={} requested={} succeeded={} failed={} invalid={}",
                event.notificationId(), result.requestedCount(), result.successCount(),
                result.failureCount(), result.invalidTokens().size());
        } catch (RuntimeException exception) {
            // Inbox persistence has already committed; push delivery must not change its outcome.
            log.warn("FCM delivery failed notificationId={} errorType={}",
                event.notificationId(), exception.getClass().getSimpleName());
        }
    }

    public FcmDeliveryResult send(NotificationCreatedEvent event) {
        return send(event, List.of());
    }

    public FcmDeliveryResult send(NotificationCreatedEvent event, List<String> retryTokens) {
        List<String> tokens = retryTokens == null || retryTokens.isEmpty()
            ? devices.findActiveTokens(event.tenantId(), event.recipientIds())
            : List.copyOf(retryTokens);
        if (tokens.isEmpty()) return FcmDeliveryResult.empty();
        FcmDeliveryResult result = provider.send(new FcmMessage(
            tokens, event.title(), event.content(), data(event)));
        if (!result.invalidTokens().isEmpty()) devices.deactivateTokens(result.invalidTokens());
        return result;
    }

    private Map<String, String> data(NotificationCreatedEvent event) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("notificationId", event.notificationId().toString());
        data.put("tenantId", event.tenantId());
        data.put("notificationType", event.notificationType());
        data.put("priority", event.priority());
        data.put("source", event.source());
        if (event.sourceEventId() != null) {
            data.put("sourceEventId", event.sourceEventId());
        }
        return data;
    }
}
