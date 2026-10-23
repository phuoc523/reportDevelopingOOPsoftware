package com.scentstation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, Long> {
    boolean existsBySerialNumberIgnoreCase(String serialNumber);
    boolean existsByDeviceIdentityIgnoreCase(String deviceIdentity);
    List<Machine> findAllByTenantIdOrderByIdAsc(String tenantId);
    Optional<Machine> findByIdAndTenantId(Long id, String tenantId);
}
