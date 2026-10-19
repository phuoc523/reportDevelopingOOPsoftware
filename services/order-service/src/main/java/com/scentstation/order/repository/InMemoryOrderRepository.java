package com.scentstation.order.repository;

import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderStatus;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final ConcurrentMap<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public Order save(Order order) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getId() == null) {
            order.setId(nextId.getAndIncrement());
            order.setStatus(OrderStatus.PENDING);
            order.setCreatedAt(now);
        }
        order.setUpdatedAt(now);
        orders.put(order.getId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(orders.get(id));
    }

    @Override
    public List<Order> findAll() {
        return orders.values()
                .stream()
                .sorted(Comparator.comparing(Order::getId))
                .toList();
    }

    @Override
    public void deleteAll() {
        orders.clear();
        nextId.set(1);
    }
}
