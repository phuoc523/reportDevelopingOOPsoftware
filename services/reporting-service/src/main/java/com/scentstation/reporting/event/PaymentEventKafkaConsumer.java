package com.scentstation.reporting.event;

import com.scentstation.reporting.dto.TransactionSyncRequest;
import com.scentstation.reporting.service.TransactionService;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "reporting.events.kafka.enabled", havingValue = "true")
public class PaymentEventKafkaConsumer {
    private static final Set<String> STATUSES = Set.of("PENDING", "PAID", "FAILED", "EXPIRED", "REFUNDED");
    private final TransactionService transactions;

    public PaymentEventKafkaConsumer(TransactionService transactions) {
        this.transactions = transactions;
    }

    @KafkaListener(
        topics = "${reporting.events.kafka.payment-topic:payment-events.v1}",
        groupId = "${reporting.events.kafka.group-id:reporting-payment-v1}"
    )
    public void consume(PaymentChangedEvent event) {
        validate(event);
        transactions.syncTransaction(new TransactionSyncRequest(
            event.reference(), event.orderId().toString(), event.amount(), event.currency(),
            event.status(), event.createdAt(), event.paidAt()));
    }

    private void validate(PaymentChangedEvent event) {
        if (event == null || event.eventVersion() != 1
                || blank(event.eventId()) || event.eventId().length() > 100
                || blank(event.reference()) || event.reference().length() > 100
                || event.orderId() == null || event.orderId() <= 0
                || event.amount() == null || event.amount().signum() < 0
                || blank(event.currency()) || event.currency().length() > 10
                || blank(event.status()) || !STATUSES.contains(event.status().trim().toUpperCase())
                || event.createdAt() == null || event.occurredAt() == null) {
            throw new PaymentEventPayloadException("Invalid payment event payload");
        }
        if ("PAID".equalsIgnoreCase(event.status()) && event.paidAt() == null) {
            throw new PaymentEventPayloadException("Paid payment event requires paidAt");
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
