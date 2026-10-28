package com.scentstation.identity.repository;

import com.scentstation.identity.tenant.Tenant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    boolean existsByTenantCode(String tenantCode);
    Optional<Tenant> findByTenantCode(String tenantCode);
    List<Tenant> findAllByOrderByNameAsc();
}