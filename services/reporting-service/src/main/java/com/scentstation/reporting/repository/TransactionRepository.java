package com.scentstation.reporting.repository;

import com.scentstation.reporting.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByReference(String reference);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            LocalDateTime startDate, LocalDateTime endDate);

    long countByCreatedAtGreaterThanEqualAndCreatedAtLessThanAndStatus(
            LocalDateTime startDate, LocalDateTime endDate, String status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.status = 'PAID'
              AND t.paidAt >= :startDate
              AND t.paidAt < :endDate
            """)
    BigDecimal sumPaidAmountBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query(value = """
            SELECT DATE(paid_at) AS reportDate,
                   COUNT(*) AS transactionCount,
                   COALESCE(SUM(amount), 0) AS revenue
            FROM transactions
            WHERE status = 'PAID'
              AND paid_at >= :startDate
              AND paid_at < :endDate
            GROUP BY DATE(paid_at)
            ORDER BY DATE(paid_at)
            """, nativeQuery = true)
    java.util.List<RevenueDailyProjection> findDailyRevenue(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String status, LocalDateTime startDate, LocalDateTime endDate);

        @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM Transaction t
            WHERE t.status = 'REFUNDED'
              AND t.createdAt >= :startDate
              AND t.createdAt < :endDate
            """)
        BigDecimal sumRefundAmountBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    interface RevenueDailyProjection {
        java.sql.Date getReportDate();
        Long getTransactionCount();
        BigDecimal getRevenue();
    }
}
