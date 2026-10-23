package com.scentstation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MachineHistoryRepository extends JpaRepository<MachineHistory, Long> {
    List<MachineHistory> findAllByMachineIdOrderByOccurredAtDesc(Long machineId);
}
