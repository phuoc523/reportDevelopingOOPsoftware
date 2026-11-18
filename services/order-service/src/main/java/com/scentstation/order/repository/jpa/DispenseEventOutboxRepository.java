package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.DispenseEventOutbox;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispenseEventOutboxRepository extends JpaRepository<DispenseEventOutbox, String> {
    List<DispenseEventOutbox> findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(LocalDateTime now);
}
