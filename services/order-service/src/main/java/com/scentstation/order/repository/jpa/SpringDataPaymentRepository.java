package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SpringDataPaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByOrderId(Long orderId);

    List<Payment> findAllByOrderByReferenceAsc();
}
