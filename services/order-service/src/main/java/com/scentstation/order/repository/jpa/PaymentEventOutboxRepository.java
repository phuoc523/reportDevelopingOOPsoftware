package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.PaymentEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentEventOutboxRepository extends JpaRepository<PaymentEventOutbox, String> {
    List<PaymentEventOutbox> findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(LocalDateTime now);
}
