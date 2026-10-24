package com.scentstation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MachineSlotRepository extends JpaRepository<MachineSlot, Long> {
    List<MachineSlot> findAllByMachineIdOrderBySlotNumberAsc(Long machineId);
    Optional<MachineSlot> findByIdAndMachineId(Long id, Long machineId);
    boolean existsByMachineIdAndSlotNumber(Long machineId, Integer slotNumber);
    boolean existsByMachineIdAndSlotCodeIgnoreCase(Long machineId, String slotCode);
    boolean existsByCartridgeIdIgnoreCase(String cartridgeId);
    boolean existsByCartridgeIdIgnoreCaseAndIdNot(String cartridgeId, Long id);
}
