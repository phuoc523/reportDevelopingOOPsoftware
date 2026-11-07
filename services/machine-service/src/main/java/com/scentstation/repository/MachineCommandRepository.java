package com.scentstation.repository;

import com.scentstation.entity.MachineCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;

public interface MachineCommandRepository extends JpaRepository<MachineCommand, Long> {
    boolean existsByCommandId(String commandId);
    Optional<com.scentstation.entity.MachineCommand> findByCommandId(String commandId);
    boolean existsByOrderIdAndStatusIn(String orderId,
        java.util.Collection<com.scentstation.entity.MachineCommandStatus> statuses);

    @Modifying
    @Transactional
    @Query("""
        update MachineCommand command
        set command.status = com.scentstation.entity.MachineCommandStatus.EXPIRED,
            command.completedAt = :expiredAt
        where command.status in (
            com.scentstation.entity.MachineCommandStatus.ACCEPTED,
            com.scentstation.entity.MachineCommandStatus.PROCESSING,
            com.scentstation.entity.MachineCommandStatus.ACKNOWLEDGED
        )
        and command.expiresAt <= :expiredAt
        """)
    int expireOverdueCommands(@Param("expiredAt") Instant expiredAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select command from MachineCommand command
        where command.commandId = :commandId and command.machineId = :machineId
        """)
    Optional<MachineCommand> findByCommandIdAndMachineIdForUpdate(
        @Param("commandId") String commandId, @Param("machineId") Long machineId);
}
