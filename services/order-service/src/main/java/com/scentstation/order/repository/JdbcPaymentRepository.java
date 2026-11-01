package com.scentstation.order.repository;

import com.scentstation.order.entity.Payment;
import com.scentstation.order.entity.PaymentCallbackEvent;
import com.scentstation.order.entity.PaymentStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcTemplate jdbc;

    public JdbcPaymentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public synchronized Payment saveIfAbsent(Payment payment) {
        Optional<Payment> existing = findByOrderId(payment.getOrderId());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.update("""
                            INSERT INTO payments
                                (reference, order_id, amount, currency, qr_payload, status,
                                 created_at, expires_at, paid_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    payment.getReference(), payment.getOrderId(), payment.getAmount(), payment.getCurrency(),
                    payment.getQrPayload(), payment.getStatus().name(), timestamp(payment.getCreatedAt()),
                    timestamp(payment.getExpiresAt()), timestamp(payment.getPaidAt()));
            return payment;
        } catch (DuplicateKeyException exception) {
            return findByOrderId(payment.getOrderId()).orElseThrow(() -> exception);
        }
    }

    @Override
    public Payment save(Payment payment) {
        jdbc.update("""
                        UPDATE payments SET amount = ?, currency = ?, qr_payload = ?, status = ?,
                            created_at = ?, expires_at = ?, paid_at = ?
                        WHERE reference = ?
                        """,
                payment.getAmount(), payment.getCurrency(), payment.getQrPayload(), payment.getStatus().name(),
                timestamp(payment.getCreatedAt()), timestamp(payment.getExpiresAt()), timestamp(payment.getPaidAt()),
                payment.getReference());
        return payment;
    }

    @Override
    public Optional<Payment> findByReference(String reference) {
        return jdbc.query("""
                        SELECT reference, order_id, amount, currency, qr_payload, status,
                               created_at, expires_at, paid_at
                        FROM payments WHERE reference = ?
                        """, (rs, row) -> new Payment(
                        rs.getString("reference"),
                        rs.getLong("order_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("qr_payload"),
                        localDateTime(rs.getTimestamp("created_at")),
                        localDateTime(rs.getTimestamp("expires_at")),
                        PaymentStatus.valueOf(rs.getString("status")),
                        localDateTime(rs.getTimestamp("paid_at"))
                ), reference).stream().findFirst();
    }

    @Override
    public Optional<Payment> findByOrderId(Long orderId) {
        return jdbc.query("SELECT reference FROM payments WHERE order_id = ?",
                        (rs, row) -> rs.getString("reference"), orderId)
                .stream()
                .findFirst()
                .flatMap(this::findByReference);
    }

    @Override
    public List<Payment> findAll() {
        return jdbc.query("SELECT reference FROM payments ORDER BY reference",
                        (rs, row) -> rs.getString("reference"))
                .stream()
                .map(this::findByReference)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public Optional<PaymentCallbackEvent> findCallbackEvent(String eventId) {
        return jdbc.query("""
                        SELECT event_id, reference, order_id, amount, currency, payment_status,
                               outcome, error_status, message, received_at
                        FROM payment_callback_events WHERE event_id = ?
                        """, (rs, row) -> new PaymentCallbackEvent(
                        rs.getString("event_id"),
                        rs.getString("reference"),
                        rs.getLong("order_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("payment_status"),
                        PaymentCallbackEvent.Outcome.valueOf(rs.getString("outcome")),
                        rs.getInt("error_status"),
                        rs.getString("message"),
                        localDateTime(rs.getTimestamp("received_at"))
                ), eventId).stream().findFirst();
    }

    @Override
    public void saveCallbackEvent(PaymentCallbackEvent event) {
        try {
            jdbc.update("""
                            INSERT INTO payment_callback_events
                                (event_id, reference, order_id, amount, currency, payment_status,
                                 outcome, error_status, message, received_at)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    event.eventId(), event.reference(), event.orderId(), event.amount(), event.currency(),
                    event.status(), event.outcome().name(), event.errorStatus(), event.message(),
                    timestamp(event.receivedAt()));
        } catch (DuplicateKeyException exception) {
            if (findCallbackEvent(event.eventId()).isEmpty()) {
                throw exception;
            }
        }
    }

    @Override
    @Transactional
    public void deleteAll() {
        jdbc.update("DELETE FROM payment_callback_events");
        jdbc.update("DELETE FROM payments");
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
