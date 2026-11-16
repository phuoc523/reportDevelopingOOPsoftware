package com.scentstation.order.repository.jpa;

import com.scentstation.order.entity.Order;
import com.scentstation.order.repository.OrderRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class OrderJpaAdapter implements OrderRepository {

    private final SpringDataOrderRepository repository;

    public OrderJpaAdapter(SpringDataOrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Order save(Order order) {
        return repository.save(order);
    }

    @Override
    public Optional<Order> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Order> findByIdAndTenantId(Long id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public List<Order> findAll() {
        return repository.findAllByOrderByIdAsc();
    }

    @Override
    public List<Order> findAllByTenantId(String tenantId) {
        return repository.findAllByTenantIdOrderByIdAsc(tenantId);
    }

    @Override
    public List<Order> findAllByTenantIdAndCustomerId(String tenantId, Long customerId) {
        return repository.findAllByTenantIdAndCustomerIdOrderByIdAsc(tenantId, customerId);
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }
}
