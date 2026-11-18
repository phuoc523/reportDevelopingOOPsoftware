package com.scentstation.order.event;

import com.scentstation.order.repository.jpa.DispenseEventOutboxRepository;
import com.scentstation.order.repository.jpa.PaymentEventOutboxRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.stereotype.Component;

@Component
public class OrderOutboxMetrics {
    public OrderOutboxMetrics(
            MeterRegistry registry,
            PaymentEventOutboxRepository paymentOutbox,
            DispenseEventOutboxRepository dispenseOutbox
    ) {
        registry.gauge("scentstation.order.outbox.pending", Tags.of("event", "payment"),
            paymentOutbox, repository -> repository.count());
        registry.gauge("scentstation.order.outbox.pending", Tags.of("event", "dispense"),
            dispenseOutbox, repository -> repository.count());
    }
}
