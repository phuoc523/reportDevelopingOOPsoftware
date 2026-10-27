package com.scentstation.repository;


import com.scentstation.entity.Machine;
import com.scentstation.entity.DeviceRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import com.scentstation.entity.MachineOperationalStatus;

public interface MachineRepository extends JpaRepository<Machine, Long> {
    boolean existsBySerialNumberIgnoreCase(String serialNumber);
    boolean existsByDeviceIdentityIgnoreCase(String deviceIdentity);
    Optional<Machine> findByDeviceIdentityIgnoreCase(String deviceIdentity);
    List<Machine> findAllByDeviceRegistrationStatus(DeviceRegistrationStatus status);
    List<Machine> findAllByTenantIdOrderByIdAsc(String tenantId);
    Optional<Machine> findByIdAndTenantId(Long id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select machine from Machine machine where machine.id = :id")
    Optional<Machine> findByIdForAlertUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select machine from Machine machine where lower(machine.deviceIdentity) = lower(:identity)")
    Optional<Machine> findByDeviceIdentityForUpdate(@Param("identity") String identity);

    List<Machine> findTop100ByDeviceRegistrationStatusAndOperationalStatusInAndLastSeenAtLessThanEqualOrderByLastSeenAtAsc(
        DeviceRegistrationStatus registrationStatus, List<MachineOperationalStatus> operationalStatuses,
        Instant cutoff);

    List<Machine> findTop100ByDeviceRegistrationStatusAndOperationalStatusAndLastSeenAtAfterAndLastSeenAtBeforeOrderByLastSeenAtAsc(
        DeviceRegistrationStatus registrationStatus, MachineOperationalStatus operationalStatus,
        Instant after, Instant before);

    List<Machine> findTop100ByDeviceRegistrationStatusAndOperationalStatusInAndLastSeenAtAfterOrderByLastSeenAtAsc(
        DeviceRegistrationStatus registrationStatus, List<MachineOperationalStatus> operationalStatuses,
        Instant cutoff);
}
