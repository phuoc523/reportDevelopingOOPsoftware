package com.scentstation.order.repository;

import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;

import java.util.Optional;

public interface PaymentRepository {

    Payment saveIfAbsent(Payment payment);

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByOrderId(Long orderId);

    Optional<PaymentCallbackEvent> findCallbackEvent(String eventId);

    void saveCallbackEvent(PaymentCallbackEvent event);

    void deleteAll();
}
