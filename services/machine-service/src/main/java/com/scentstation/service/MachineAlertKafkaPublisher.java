package com.scentstation.service;

import com.scentstation.event.MachineAlertChangedEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class MachineAlertKafkaPublisher {
    private final KafkaTemplate<String, MachineAlertChangedEvent> kafka;
    private final boolean enabled;
    private final String topic;

    public MachineAlertKafkaPublisher(
            KafkaTemplate<String, MachineAlertChangedEvent> kafka,
            @Value("${machine.notification.kafka.enabled:false}") boolean enabled,
            @Value("${machine.notification.kafka.topic:machine-alert.v1}") String topic
    ) {
        this.kafka = kafka;
        this.enabled = enabled;
        this.topic = topic;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void publish(MachineAlertChangedEvent event) {
        try {
            kafka.send(topic, event.getTenantId(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka did not acknowledge the machine alert", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Kafka did not acknowledge the machine alert", exception);
        }
    }
}
