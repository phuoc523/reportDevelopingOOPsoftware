package com.scentstation.repository;

import com.scentstation.entity.MachineDeviceEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MachineDeviceEventRepository extends JpaRepository<MachineDeviceEvent, Long> {
    Optional<MachineDeviceEvent> findByEventId(String eventId);

    @Query("""
        select event from MachineDeviceEvent event
        where event.machineId = :machineId
          and (:fromTime is null or event.occurredAt >= :fromTime)
          and (:untilTime is null or event.occurredAt <= :untilTime)
        """)
    Page<MachineDeviceEvent> findHistory(@Param("machineId") Long machineId,
                                         @Param("fromTime") Instant fromTime,
                                         @Param("untilTime") Instant untilTime,
                                         Pageable pageable);

    List<MachineDeviceEvent> findTop500ByOccurredAtBeforeOrderByOccurredAtAsc(Instant cutoff);
}
