package com.scentstation.order.repository;

import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryPaymentRepository implements PaymentRepository {

    private final ConcurrentMap<String, Payment> paymentsByReference = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> referencesByOrderId = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PaymentCallbackEvent> callbackEvents = new ConcurrentHashMap<>();

    @Override
    public synchronized Payment saveIfAbsent(Payment payment) {
        String existingReference = referencesByOrderId.get(payment.getOrderId());
        if (existingReference != null) {
            return paymentsByReference.get(existingReference);
        }

        paymentsByReference.put(payment.getReference(), payment);
        referencesByOrderId.put(payment.getOrderId(), payment.getReference());
        return payment;
    }

    @Override
    public Optional<Payment> findByReference(String reference) {
        return Optional.ofNullable(paymentsByReference.get(reference));
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        String reference = referencesByOrderId.get(orderId);
        return reference == null ? Optional.empty() : findByReference(reference);
    }

    @Override
    public List<Payment> findAll() {
        return paymentsByReference.values()
                .stream()
                .sorted(Comparator.comparing(Payment::getReference))
                .toList();
    }

    @Override
    public Optional<PaymentCallbackEvent> findCallbackEvent(String eventId) {
        return Optional.ofNullable(callbackEvents.get(eventId));
    }

    @Override
    public void saveCallbackEvent(PaymentCallbackEvent event) {
        callbackEvents.putIfAbsent(event.eventId(), event);
    }

    @Override
    public synchronized void deleteAll() {
        paymentsByReference.clear();
        referencesByOrderId.clear();
        callbackEvents.clear();
    }
}
