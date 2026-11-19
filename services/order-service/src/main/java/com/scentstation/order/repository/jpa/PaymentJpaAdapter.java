package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;
import com.scentstation.order.entity.PaymentEventOutbox;
import com.scentstation.order.repository.PaymentRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class PaymentJpaAdapter implements PaymentRepository {

    private final SpringDataOrderRepository orders;
    private final SpringDataPaymentRepository payments;
    private final SpringDataPaymentCallbackEventRepository callbackEvents;
    private final PaymentEventOutboxRepository paymentOutbox;

    public PaymentJpaAdapter(
            SpringDataOrderRepository orders,
            SpringDataPaymentRepository payments,
            SpringDataPaymentCallbackEventRepository callbackEvents,
            PaymentEventOutboxRepository paymentOutbox
    ) {
        this.orders = orders;
        this.payments = payments;
        this.callbackEvents = callbackEvents;
        this.paymentOutbox = paymentOutbox;
    }

    @Override
    @Transactional
    public Payment saveIfAbsent(Payment payment) {
        orders.findByIdForUpdate(payment.getOrderId());
        Payment stored = payments.findByOrderId(payment.getOrderId()).orElse(null);
        if (stored != null) return stored;
        stored = payments.save(payment);
        enqueue(stored);
        return stored;
    }

    @Override
    @Transactional
    public Payment save(Payment payment) {
        Payment stored = payments.save(payment);
        enqueue(stored);
        return stored;
    }

    @Override
    public Optional<Payment> findByReference(String reference) {
        return payments.findById(reference);
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return payments.findByOrderId(orderId);
    }

    @Override
    public List<Payment> findAll() {
        return payments.findAllByOrderByReferenceAsc();
    }

    @Override
    public Optional<PaymentCallbackEvent> findCallbackEvent(String eventId) {
        return callbackEvents.findById(eventId);
    }

    @Override
    @Transactional
    public void saveCallbackEvent(PaymentCallbackEvent event) {
        if (callbackEvents.findById(event.eventId()).isEmpty()) {
            callbackEvents.save(event);
        }
    }

    @Override
    @Transactional
    public void deleteAll() {
        paymentOutbox.deleteAll();
        callbackEvents.deleteAll();
        payments.deleteAll();
    }

    private void enqueue(Payment payment) {
        PaymentEventOutbox event = new PaymentEventOutbox(
                payment,
                orders.findById(payment.getOrderId()).orElse(null)
        );
        if (!paymentOutbox.existsById(event.getEventId())) {
            paymentOutbox.save(event);
        }
    }
}
