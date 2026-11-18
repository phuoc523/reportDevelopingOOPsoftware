package com.scentstation.order.event;

import com.scentstation.order.entity.DispenseEventOutbox;
import com.scentstation.order.repository.jpa.DispenseEventOutboxRepository;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "order.events.kafka.enabled", havingValue = "true")
public class DispenseEventOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(DispenseEventOutboxPublisher.class);

    private final DispenseEventOutboxRepository outbox;
    private final KafkaTemplate<String, DispenseCompletedEvent> kafka;
    private final String topic;

    public DispenseEventOutboxPublisher(
            DispenseEventOutboxRepository outbox,
            KafkaTemplate<String, DispenseCompletedEvent> kafka,
            @Value("${order.events.kafka.dispense-topic:dispense-events.v1}") String topic
    ) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.topic = topic;
    }

    @Scheduled(
        fixedDelayString = "${order.events.outbox.poll-interval-ms:1000}",
        initialDelayString = "${order.events.outbox.initial-delay-ms:5000}"
    )
    public void publishPending() {
        for (DispenseEventOutbox item : outbox
                .findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(LocalDateTime.now())) {
            try {
                kafka.send(topic, item.getCommandId(), toEvent(item)).get(10, TimeUnit.SECONDS);
                outbox.delete(item);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                scheduleRetry(item, exception);
                return;
            } catch (Exception exception) {
                scheduleRetry(item, exception);
            }
        }
    }

    private DispenseCompletedEvent toEvent(DispenseEventOutbox item) {
        return new DispenseCompletedEvent(
            item.getEventVersion(), item.getEventId(), item.getCommandId(), item.getTenantId(),
            item.getMachineId(), item.getSlotId(), item.getDispenseType(), item.getResult(),
            item.getOccurredAt());
    }

    private void scheduleRetry(DispenseEventOutbox item, Exception exception) {
        long seconds = Math.min(3600L, 5L * (1L << Math.min(item.getAttemptCount(), 9)));
        item.attempted(LocalDateTime.now().plusSeconds(seconds));
        outbox.save(item);
        log.warn("Publishing dispense event {} failed; retry {} scheduled in {} seconds",
            item.getEventId(), item.getAttemptCount(), seconds, exception);
    }
}
