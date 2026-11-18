package com.scentstation.order.event;

import com.scentstation.order.entity.PaymentEventOutbox;
import com.scentstation.order.repository.jpa.PaymentEventOutboxRepository;
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
public class PaymentEventOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventOutboxPublisher.class);

    private final PaymentEventOutboxRepository outbox;
    private final KafkaTemplate<String, PaymentChangedEvent> kafka;
    private final String topic;

    public PaymentEventOutboxPublisher(
            PaymentEventOutboxRepository outbox,
            KafkaTemplate<String, PaymentChangedEvent> kafka,
            @Value("${order.events.kafka.payment-topic:payment-events.v1}") String topic
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
        for (PaymentEventOutbox item : outbox
                .findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(LocalDateTime.now())) {
            try {
                kafka.send(topic, item.getReference(), toEvent(item)).get(10, TimeUnit.SECONDS);
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

    private PaymentChangedEvent toEvent(PaymentEventOutbox item) {
        return new PaymentChangedEvent(
            item.getEventVersion(), item.getEventId(), item.getReference(), item.getOrderId(),
            item.getAmount(), item.getCurrency(), item.getStatus(), item.getPaymentCreatedAt(),
            item.getPaidAt(), item.getOccurredAt());
    }

    private void scheduleRetry(PaymentEventOutbox item, Exception exception) {
        long seconds = Math.min(3600L, 5L * (1L << Math.min(item.getAttemptCount(), 9)));
        item.attempted(LocalDateTime.now().plusSeconds(seconds));
        outbox.save(item);
        log.warn("Publishing payment event {} failed; retry {} scheduled in {} seconds",
            item.getEventId(), item.getAttemptCount(), seconds, exception);
    }
}
