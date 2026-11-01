package com.scentstation.order.repository;

import com.scentstation.order.entity.TransactionReview;
import com.scentstation.order.entity.TransactionReviewStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcTransactionReviewRepository implements TransactionReviewRepository {

    private final JdbcTemplate jdbc;

    public JdbcTransactionReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public synchronized TransactionReview openOrUpdate(String reference, Long orderId, String reason) {
        Optional<TransactionReview> existing = findByReference(reference);
        if (existing.isPresent()) {
            TransactionReview review = existing.get();
            review.updateReason(reason);
            return save(review);
        }
        TransactionReview review = new TransactionReview(reference, orderId, reason);
        try {
            jdbc.update("""
                            INSERT INTO transaction_reviews
                                (reference, order_id, reason, status, created_at, resolved_at)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """,
                    review.getReference(), review.getOrderId(), review.getReason(), review.getStatus().name(),
                    timestamp(review.getCreatedAt()), timestamp(review.getResolvedAt()));
            return review;
        } catch (DuplicateKeyException exception) {
            TransactionReview concurrentlyCreated = findByReference(reference).orElseThrow(() -> exception);
            concurrentlyCreated.updateReason(reason);
            return save(concurrentlyCreated);
        }
    }

    @Override
    public TransactionReview save(TransactionReview review) {
        int updated = jdbc.update("""
                        UPDATE transaction_reviews
                        SET order_id = ?, reason = ?, status = ?, resolved_at = ?
                        WHERE reference = ?
                        """,
                review.getOrderId(), review.getReason(), review.getStatus().name(),
                timestamp(review.getResolvedAt()), review.getReference());
        if (updated == 0) {
            throw new IllegalStateException("Transaction review does not exist: " + review.getReference());
        }
        return review;
    }

    @Override
    public Optional<TransactionReview> findByReference(String reference) {
        return jdbc.query("""
                        SELECT reference, order_id, reason, status, created_at, resolved_at
                        FROM transaction_reviews WHERE reference = ?
                        """,
                (rs, row) -> new TransactionReview(
                        rs.getString("reference"),
                        rs.getObject("order_id", Long.class),
                        rs.getString("reason"),
                        TransactionReviewStatus.valueOf(rs.getString("status")),
                        localDateTime(rs.getTimestamp("created_at")),
                        localDateTime(rs.getTimestamp("resolved_at"))
                ), reference).stream().findFirst();
    }

    @Override
    public List<TransactionReview> findAll() {
        return jdbc.query("""
                        SELECT reference, order_id, reason, status, created_at, resolved_at
                        FROM transaction_reviews ORDER BY created_at
                        """,
                (rs, row) -> new TransactionReview(
                        rs.getString("reference"),
                        rs.getObject("order_id", Long.class),
                        rs.getString("reason"),
                        TransactionReviewStatus.valueOf(rs.getString("status")),
                        localDateTime(rs.getTimestamp("created_at")),
                        localDateTime(rs.getTimestamp("resolved_at"))
                ));
    }

    @Override
    @Transactional
    public void deleteAll() {
        jdbc.update("DELETE FROM transaction_reviews");
    }

    private static Timestamp timestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime localDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
