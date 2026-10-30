
package com.scentstation.reporting.repository;

import com.scentstation.reporting.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReference(String reference);

    List<Transaction> findByOrderId(String orderId);

    List<Transaction> findByStatus(String status);

    List<Transaction> findByCreatedAtBetween(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    List<Transaction> findByPaidAtBetween(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.status = 'PAID'
          AND t.paidAt >= :startDate
          AND t.paidAt < :endDate
        """)
    BigDecimal sumPaidAmountBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
);
}

