package com.scentstation.order.repository;

import com.scentstation.order.entity.Order;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Order save(Order order);

    Optional<Order> findById(Long id);

    Optional<Order> findByIdAndTenantId(Long id, String tenantId);

    List<Order> findAll();

    List<Order> findAllByTenantId(String tenantId);

    List<Order> findAllByTenantIdAndCustomerId(String tenantId, Long customerId);

    void deleteAll();
}
