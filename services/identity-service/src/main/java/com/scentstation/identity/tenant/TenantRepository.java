package com.scentstation.identity.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    boolean existsByCode(String code);

    @Query("select tenant.status from Tenant tenant where tenant.id = :id")
    java.util.Optional<Tenant.Status> findStatusById(@Param("id") Long id);
}
