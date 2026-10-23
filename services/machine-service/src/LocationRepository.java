package com.scentstation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface LocationRepository extends JpaRepository<Location, String> {
    List<Location> findAllByTenantIdOrderByNameAsc(String tenantId);
}
