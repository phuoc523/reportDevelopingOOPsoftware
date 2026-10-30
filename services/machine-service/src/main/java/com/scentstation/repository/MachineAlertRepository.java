package com.scentstation.repository;

import com.scentstation.entity.MachineAlert;
import com.scentstation.entity.MachineAlertStatus;
import com.scentstation.entity.MachineAlertSource;
import com.scentstation.entity.MachineAlertType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MachineAlertRepository extends JpaRepository<MachineAlert, Long> {
    Optional<MachineAlert> findFirstByDedupeKeyAndStatusInOrderByFirstSeenAtDesc(
        String dedupeKey, Collection<MachineAlertStatus> statuses);

    List<MachineAlert> findAllByTenantIdOrderByLastSeenAtDesc(String tenantId);

    List<MachineAlert> findAllByMachineIdAndStatusInOrderByLastSeenAtDesc(
        Long machineId, Collection<MachineAlertStatus> statuses);

    Optional<MachineAlert> findByIdAndTenantId(Long id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select alert from MachineAlert alert where alert.id = :id and alert.tenantId = :tenantId")
    Optional<MachineAlert> findByIdAndTenantIdForUpdate(@Param("id") Long id,
                                                        @Param("tenantId") String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MachineAlert> findAllByMachineIdAndSlotIdIsNullAndTypeAndSourceAndStatusIn(
        Long machineId, MachineAlertType type, MachineAlertSource source,
        Collection<MachineAlertStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MachineAlert> findAllByStatusIn(Collection<MachineAlertStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<MachineAlert> findAllByMachineIdAndSlotIdAndTypeAndSourceAndStatusIn(
        Long machineId, Long slotId, MachineAlertType type, MachineAlertSource source,
        Collection<MachineAlertStatus> statuses);
}
