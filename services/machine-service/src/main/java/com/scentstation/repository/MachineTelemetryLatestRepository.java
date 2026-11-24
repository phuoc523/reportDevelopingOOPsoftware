package com.scentstation.repository;

import com.scentstation.entity.MachineTelemetryLatest;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MachineTelemetryLatestRepository extends JpaRepository<MachineTelemetryLatest, Long> {
    Optional<MachineTelemetryLatest> findByMachineIdAndSlotId(Long machineId, Long slotId);
}
