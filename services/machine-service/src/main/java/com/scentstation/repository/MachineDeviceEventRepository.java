package com.scentstation.repository;

import com.scentstation.entity.MachineDeviceEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MachineDeviceEventRepository extends JpaRepository<MachineDeviceEvent, Long> {
    boolean existsByMessageId(String messageId);
    List<MachineDeviceEvent> findAllByMachineIdOrderByOccurredAtDesc(Long machineId);
}
