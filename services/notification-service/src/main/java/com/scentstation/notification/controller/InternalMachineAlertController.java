package com.scentstation.notification.controller;

import com.scentstation.notification.event.MachineAlertEvent;
import com.scentstation.notification.event.MachineAlertEventProcessor;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/events/machine-alerts")
public class InternalMachineAlertController {
    private final MachineAlertEventProcessor processor;
    private final String serviceKey;

    public InternalMachineAlertController(
            MachineAlertEventProcessor processor,
            @Value("${notification.identity.internal-service-key:}") String serviceKey
    ) {
        this.processor = processor;
        this.serviceKey = serviceKey;
    }

    @PostMapping
    public ResponseEntity<Void> accept(
            @RequestHeader(value = "X-Internal-Service-Key", required = false) String suppliedKey,
            @Valid @RequestBody MachineAlertEvent event
    ) {
        requireInternalKey(suppliedKey);
        boolean created = processor.process(event);
        return ResponseEntity.status(created ? HttpStatus.ACCEPTED : HttpStatus.OK).build();
    }

    private void requireInternalKey(String suppliedKey) {
        if (serviceKey == null || serviceKey.isBlank() || suppliedKey == null
                || !MessageDigest.isEqual(
                    serviceKey.getBytes(StandardCharsets.UTF_8),
                    suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Service authentication required");
        }
    }
}
