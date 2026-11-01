package com.scentstation.order.repository;

import com.scentstation.order.entity.DispensingCommand;
import com.scentstation.order.entity.DispensingStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class JdbcDispensingRepository implements DispensingRepository {

    private final JdbcTemplate jdbc;

    public JdbcDispensingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public synchronized DispensingCommand saveIfAbsent(DispensingCommand command) {
        Optional<DispensingCommand> existing = findByOrderId(command.getOrderId());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbc.update("""
                            INSERT INTO dispensing_commands
                                (command_id, order_id, payment_reference, machine_id, slot_id, status,
                                 created_at, result_received_at, manual_review_required, manual_review_reason)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    command.getCommandId(), command.getOrderId(), command.getPaymentReference(),
                    command.getMachineId(), command.getSlotId(), command.getStatus().name(),
                    timestamp(command.getCreatedAt()), timestamp(command.getResultReceivedAt()),
                    command.isManualReviewRequired(), command.getManualReviewReason());
            return command;
        } catch (DuplicateKeyException exception) {
            return findByOrderId(command.getOrderId()).orElseThrow(() -> exception);
        }
    }

    @Override
    public DispensingCommand save(DispensingCommand command) {
        int updated = jdbc.update("""
                        UPDATE dispensing_commands
                        SET status = ?, result_received_at = ?, manual_review_required = ?, manual_review_reason = ?
                        WHERE command_id = ?
                        """,
                command.getStatus().name(), timestamp(command.getResultReceivedAt()),
                command.isManualReviewRequired(), command.getManualReviewReason(), command.getCommandId());
        if (updated == 0) {
            throw new IllegalStateException("Dispensing command does not exist: " + command.getCommandId());
        }
        return command;
    }

    @Override
    public Optional<DispensingCommand> findByOrderId(Long orderId) {
        return find("order_id", orderId);
    }

    @Override
    public Optional<DispensingCommand> findByCommandId(String commandId) {
        return find("command_id", commandId);
    }

    @Override
    @Transactional
    public void deleteAll() {
        jdbc.update("DELETE FROM dispensing_commands");
    }

    private Optional<DispensingCommand> find(String column, Object value) {
        if (!column.equals("order_id") && !column.equals("command_id")) {
            throw new IllegalArgumentException("Unsupported dispensing lookup column");
        }
        return jdbc.query("""
                        SELECT command_id, order_id, payment_reference, machine_id, slot_id, status,
                               created_at, result_received_at, manual_review_required, manual_review_reason
                        FROM dispensing_commands WHERE %s = ?
                        """.formatted(column),
                (rs, row) -> new DispensingCommand(
                        rs.getString("command_id"),
                        rs.getLong("order_id"),
                        rs.getString("payment_reference"),
                        rs.getString("machine_id"),
                        rs.getString("slot_id"),
                        localDateTime(rs.getTimestamp("created_at")),
                        DispensingStatus.valueOf(rs.getString("status")),
                        localDateTime(rs.getTimestamp("result_received_at")),
                        rs.getBoolean("manual_review_required"),
                        rs.getString("manual_review_reason")
                ), value).stream().findFirst();
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
