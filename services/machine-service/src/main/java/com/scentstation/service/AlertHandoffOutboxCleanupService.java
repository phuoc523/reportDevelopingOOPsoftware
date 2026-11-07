package com.scentstation.service;

import com.scentstation.repository.AlertHandoffOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertHandoffOutboxCleanupService {
    private final AlertHandoffOutboxRepository outbox;

    public AlertHandoffOutboxCleanupService(AlertHandoffOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public void removeDelivered(String eventId, String targetService) {
        outbox.deleteByEventIdAndTargetService(eventId, targetService);
    }
}
