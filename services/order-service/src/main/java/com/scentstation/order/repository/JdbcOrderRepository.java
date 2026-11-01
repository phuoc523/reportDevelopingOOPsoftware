package com.scentstation.order.repository;

import com.scentstation.order.entity.Order;
import com.scentstation.order.entity.OrderItem;
import com.scentstation.order.entity.OrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public Order save(Order order) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getId() == null) {
            if (order.getStatus() == null) {
                order.setStatus(OrderStatus.PENDING);
            }
            order.setCreatedAt(now);
            order.setUpdatedAt(now);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO orders
                            (tenant_id, customer_id, machine_id, slot_id, total_amount, currency,
                             status, expires_at, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, Statement.RETURN_GENERATED_KEYS);
                statement.setString(1, order.getTenantId());
                statement.setLong(2, order.getCustomerId());
                statement.setString(3, order.getMachineId());
                statement.setString(4, order.getSlotId());
                statement.setBigDecimal(5, order.getTotalAmount());
                statement.setString(6, order.getCurrency());
                statement.setString(7, order.getStatus().name());
                statement.setTimestamp(8, timestamp(order.getExpiresAt()));
                statement.setTimestamp(9, timestamp(order.getCreatedAt()));
                statement.setTimestamp(10, timestamp(order.getUpdatedAt()));
                return statement;
            }, keyHolder);
            Number key = keyHolder.getKey();
            if (key == null) {
                throw new IllegalStateException("Database did not return the created order ID");
            }
            order.setId(key.longValue());
        } else {
            order.setUpdatedAt(now);
            jdbc.update("""
                            UPDATE orders
                            SET tenant_id = ?, customer_id = ?, machine_id = ?, slot_id = ?,
                                total_amount = ?, currency = ?, status = ?, expires_at = ?, updated_at = ?
                            WHERE id = ?
                            """,
                    order.getTenantId(), order.getCustomerId(), order.getMachineId(), order.getSlotId(),
                    order.getTotalAmount(), order.getCurrency(), order.getStatus().name(),
                    timestamp(order.getExpiresAt()), timestamp(order.getUpdatedAt()), order.getId());
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", order.getId());
        }

        jdbc.batchUpdate("""
                        INSERT INTO order_items
                            (order_id, product_id, product_name, unit_price, quantity, line_total)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, order.getItems(), order.getItems().size(), (statement, item) -> {
            statement.setLong(1, order.getId());
            statement.setLong(2, item.productId());
            statement.setString(3, item.productName());
            statement.setBigDecimal(4, item.unitPrice());
            statement.setInt(5, item.quantity());
            statement.setBigDecimal(6, item.lineTotal());
        });
        return order;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return jdbc.query("""
                        SELECT id, tenant_id, customer_id, machine_id, slot_id, total_amount,
                               currency, status, expires_at, created_at, updated_at
                        FROM orders WHERE id = ?
                        """,
                (rs, row) -> {
                    Order order = new Order(
                            rs.getString("tenant_id"),
                            rs.getLong("customer_id"),
                            rs.getString("machine_id"),
                            rs.getString("slot_id"),
                            findItems(rs.getLong("id")),
                            rs.getBigDecimal("total_amount"),
                            rs.getString("currency"),
                            localDateTime(rs.getTimestamp("expires_at"))
                    );
                    order.setId(rs.getLong("id"));
                    order.setStatus(OrderStatus.valueOf(rs.getString("status")));
                    order.setCreatedAt(localDateTime(rs.getTimestamp("created_at")));
                    order.setUpdatedAt(localDateTime(rs.getTimestamp("updated_at")));
                    return order;
                }, id).stream().findFirst();
    }

    @Override
    public List<Order> findAll() {
        return jdbc.query("SELECT id FROM orders ORDER BY id",
                        (rs, row) -> rs.getLong("id"))
                .stream()
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAll() {
        jdbc.update("DELETE FROM orders");
    }

    private List<OrderItem> findItems(Long orderId) {
        return jdbc.query("""
                        SELECT product_id, product_name, unit_price, quantity, line_total
                        FROM order_items WHERE order_id = ? ORDER BY id
                        """,
                (rs, row) -> new OrderItem(
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getBigDecimal("unit_price"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("line_total")
                ), orderId);
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
