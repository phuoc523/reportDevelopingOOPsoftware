package com.scentstation.repository;

import com.scentstation.entity.AlertHandoffOutbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.time.Instant;
import java.util.List;

public interface AlertHandoffOutboxRepository extends JpaRepository<AlertHandoffOutbox, Long> {
    List<AlertHandoffOutbox> findTop100ByNextAttemptAtLessThanEqualOrderByNextAttemptAtAsc(Instant now);

    @Modifying
    void deleteByEventIdAndTargetService(String eventId, String targetService);
}
