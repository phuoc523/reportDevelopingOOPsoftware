package com.scentstation.notification.event;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.kafka.enabled", havingValue = "true")
public class MachineAlertKafkaConsumer {
    private final MachineAlertEventProcessor processor;
    private final Validator validator;

    public MachineAlertKafkaConsumer(MachineAlertEventProcessor processor, Validator validator) {
        this.processor = processor;
        this.validator = validator;
    }

    @KafkaListener(
        topics = "${notification.kafka.machine-alert-topic:machine-alert.v1}",
        groupId = "${notification.kafka.group-id:notification-service-v1}"
    )
    public void consume(MachineAlertEvent event) {
        Set<ConstraintViolation<MachineAlertEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            throw new MachineAlertPayloadException("Invalid machine alert payload");
        }
        processor.process(event);
    }
}
