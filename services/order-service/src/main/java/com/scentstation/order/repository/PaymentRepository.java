package com.scentstation.order.repository;

import com.scentstation.order.entity.Payment;

import java.util.Optional;

public interface PaymentRepository {

    Payment saveIfAbsent(Payment payment);

    Optional<Payment> findByReference(String reference);

    Optional<Payment> findByOrderId(Long orderId);

    void deleteAll();
}
