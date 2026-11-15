package com.scentstation.notification.event;

import com.scentstation.notification.dto.command.CreateNotificationCommand;
import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.service.NotificationService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MachineAlertEventProcessor {
    private final NotificationService notifications;
    private final IdentityAuthorizationClient identity;

    public MachineAlertEventProcessor(
            NotificationService notifications,
            IdentityAuthorizationClient identity
    ) {
        this.notifications = notifications;
        this.identity = identity;
    }

    public boolean process(MachineAlertEvent event) {
        List<String> recipientIds = identity.resolveNotificationRecipients(event.tenantId());
        if (recipientIds.isEmpty()) {
            throw new IllegalStateException("No notification recipients are configured for tenant");
        }
        try {
            notifications.create(new CreateNotificationCommand(
                event.tenantId(),
                "Machine alert: " + event.alertType().replace('_', ' '),
                "Machine %d at location %s reported %s with severity %s (status %s)"
                    .formatted(event.machineId(), event.locationId(), event.alertType(),
                        event.severity(), event.status()),
                "MACHINE_ALERT",
                event.severity(),
                event.source(),
                event.eventId(),
                recipientIds
            ));
            return true;
        } catch (DuplicateNotificationEventException exception) {
            return false;
        }
    }
}
