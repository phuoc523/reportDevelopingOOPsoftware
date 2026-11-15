package com.scentstation.notification.event;

import com.scentstation.notification.exception.DuplicateNotificationEventException;
import com.scentstation.notification.service.NotificationService;
import org.springframework.stereotype.Service;

@Service
public class MachineAlertEventProcessor {
    private final NotificationService notifications;
    private final MachineAlertNotificationMapper mapper;
    private final MachineAlertRecipientResolver recipients;

    public MachineAlertEventProcessor(
            NotificationService notifications,
            MachineAlertNotificationMapper mapper,
            MachineAlertRecipientResolver recipients
    ) {
        this.notifications = notifications;
        this.mapper = mapper;
        this.recipients = recipients;
    }

    public boolean process(MachineAlertEvent event) {
        if (notifications.existsForSourceEvent(event.source(), event.eventId())) {
            return false;
        }
        try {
            notifications.create(mapper.map(event, recipients.resolve(event.tenantId())));
            return true;
        } catch (DuplicateNotificationEventException exception) {
            return false;
        }
    }
}
