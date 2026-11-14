package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SpringDataOrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByIdAsc();

    List<Order> findAllByTenantIdOrderByIdAsc(String tenantId);

    List<Order> findAllByTenantIdAndCustomerIdOrderByIdAsc(String tenantId, Long customerId);

    Optional<Order> findByIdAndTenantId(Long id, String tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select orderEntity from Order orderEntity where orderEntity.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") Long id);
}
