package com.scentstation.notification.event;

import com.scentstation.notification.security.IdentityAuthorizationClient;
import com.scentstation.notification.security.NotificationAuthorizationUnavailableException;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MachineAlertRecipientResolver {
    private final IdentityAuthorizationClient identity;

    public MachineAlertRecipientResolver(IdentityAuthorizationClient identity) {
        this.identity = identity;
    }

    public List<String> resolve(String tenantId) {
        List<String> resolved = identity.resolveNotificationRecipients(tenantId);
        if (resolved == null) {
            throw new NotificationAuthorizationUnavailableException();
        }
        LinkedHashSet<String> recipientIds = new LinkedHashSet<>();
        for (String recipientId : resolved) {
            if (recipientId == null || !recipientId.trim().matches("[1-9][0-9]*")) {
                throw new NotificationAuthorizationUnavailableException();
            }
            recipientIds.add(recipientId.trim());
        }
        if (recipientIds.isEmpty()) {
            throw new IllegalStateException("No notification recipients are configured for tenant");
        }
        return List.copyOf(recipientIds);
    }
}
