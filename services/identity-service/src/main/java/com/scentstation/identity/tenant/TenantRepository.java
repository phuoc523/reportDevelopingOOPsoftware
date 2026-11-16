package com.scentstation.identity.tenant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    boolean existsByCode(String code);

    @Query("select t.id as id, t.code as code, t.name as name, t.status as status from Tenant t where t.id = :id")
    Optional<TenantLiveFields> findLiveFieldsById(@Param("id") Long id);
}
