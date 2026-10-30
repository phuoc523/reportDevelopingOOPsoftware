package com.scentstation.service;

import com.scentstation.entity.AlertHandoffOutbox;
import com.scentstation.repository.AlertHandoffOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertHandoffOutboxService {
    private final AlertHandoffOutboxRepository outbox;

    public AlertHandoffOutboxService(AlertHandoffOutboxRepository outbox) {
        this.outbox = outbox;
    }

    @Transactional
    public void enqueue(AlertHandoffOutbox item) {
        outbox.save(item);
    }
}
