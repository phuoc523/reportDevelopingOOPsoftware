package com.scentstation.identity.repository;

import com.scentstation.identity.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, String> {
    Optional<Tenant> findByCode(String code);
}
