package com.scentstation.repository;

import com.scentstation.entity.MachineTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MachineTelemetryRepository extends JpaRepository<MachineTelemetry, Long> {
    Optional<MachineTelemetry> findByEventId(String eventId);

    @Query("""
        select telemetry from MachineTelemetry telemetry
        where telemetry.machineId = :machineId
          and (:fromTime is null or telemetry.occurredAt >= :fromTime)
          and (:untilTime is null or telemetry.occurredAt <= :untilTime)
        """)
    Page<MachineTelemetry> findHistory(@Param("machineId") Long machineId,
                                       @Param("fromTime") Instant fromTime,
                                       @Param("untilTime") Instant untilTime,
                                       Pageable pageable);

    List<MachineTelemetry> findTop500ByOccurredAtBeforeOrderByOccurredAtAsc(Instant cutoff);
}
