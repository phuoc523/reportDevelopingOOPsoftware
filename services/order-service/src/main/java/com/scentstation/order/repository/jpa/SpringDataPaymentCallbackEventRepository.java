package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.PaymentCallbackEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataPaymentCallbackEventRepository
        extends JpaRepository<PaymentCallbackEvent, String> {
}
