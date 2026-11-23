package com.scentstation.reporting.repository;

import com.scentstation.reporting.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>,
        JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.reference = :reference")
    Optional<Transaction> findByReferenceForUpdate(@Param("reference") String reference);

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

    @Query(value = """
            SELECT DATE(paid_at) AS reportDate, COUNT(*) AS transactionCount,
                   COALESCE(SUM(amount), 0) AS revenue
            FROM transactions
            WHERE status = 'PAID' AND paid_at >= :startDate AND paid_at < :endDate
              AND (:tenantId IS NULL OR tenant_id = :tenantId)
              AND (:machineId IS NULL OR machine_id = :machineId)
              AND (:productId IS NULL OR product_id = :productId)
            GROUP BY DATE(paid_at) ORDER BY DATE(paid_at)
            """, nativeQuery = true)
    java.util.List<RevenueDailyProjection> findDailyRevenueScoped(
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate,
            @Param("tenantId") String tenantId, @Param("machineId") String machineId,
            @Param("productId") Long productId);

    long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            String status, LocalDateTime startDate, LocalDateTime endDate);

    @Query("""
            SELECT COUNT(t) FROM Transaction t
            WHERE t.createdAt >= :startDate AND t.createdAt < :endDate
              AND (:status IS NULL OR t.status = :status)
              AND (:tenantId IS NULL OR t.tenantId = :tenantId)
              AND (:machineId IS NULL OR t.machineId = :machineId)
              AND (:productId IS NULL OR t.productId = :productId)
            """)
    long countScoped(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate, @Param("status") String status,
            @Param("tenantId") String tenantId, @Param("machineId") String machineId,
            @Param("productId") Long productId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.status = 'PAID' AND t.paidAt >= :startDate AND t.paidAt < :endDate
              AND (:tenantId IS NULL OR t.tenantId = :tenantId)
              AND (:machineId IS NULL OR t.machineId = :machineId)
              AND (:productId IS NULL OR t.productId = :productId)
            """)
    BigDecimal sumPaidAmountScoped(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate, @Param("tenantId") String tenantId,
            @Param("machineId") String machineId, @Param("productId") Long productId);

    @Query("""
            SELECT t.orderId FROM Transaction t
            WHERE t.status = 'PAID' AND t.paidAt >= :startDate AND t.paidAt < :endDate
              AND t.tenantId = :tenantId
              AND (:machineId IS NULL OR t.machineId = :machineId)
              AND (:productId IS NULL OR t.productId = :productId)
            """)
    java.util.List<String> findPaidOrderIds(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate, @Param("tenantId") String tenantId,
            @Param("machineId") String machineId, @Param("productId") Long productId);

    @Query("""
            SELECT t.orderId FROM Transaction t
            WHERE t.status = 'PAID' AND t.paidAt >= :startDate AND t.paidAt < :endDate
              AND t.tenantId = :tenantId
              AND t.machineId IN :machineIds
              AND (:productId IS NULL OR t.productId = :productId)
            """)
    java.util.List<String> findPaidOrderIdsInMachines(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate, @Param("tenantId") String tenantId,
            @Param("machineIds") java.util.Collection<String> machineIds,
            @Param("productId") Long productId);

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

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t
            WHERE t.status = 'REFUNDED' AND t.createdAt >= :startDate AND t.createdAt < :endDate
              AND (:tenantId IS NULL OR t.tenantId = :tenantId)
              AND (:machineId IS NULL OR t.machineId = :machineId)
              AND (:productId IS NULL OR t.productId = :productId)
            """)
    BigDecimal sumRefundAmountScoped(@Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate, @Param("tenantId") String tenantId,
            @Param("machineId") String machineId, @Param("productId") Long productId);

    interface RevenueDailyProjection {
        java.sql.Date getReportDate();
        Long getTransactionCount();
        BigDecimal getRevenue();
    }

    @Query(value = """
            SELECT COALESCE(tenant_id, 'UNASSIGNED') AS dimensionKey,
                   COUNT(*) AS transactionCount,
                   COALESCE(SUM(amount), 0) AS revenue
            FROM transactions
            WHERE status = 'PAID' AND paid_at >= :startDate AND paid_at < :endDate
              AND (:tenantId IS NULL OR tenant_id = :tenantId)
              AND (:machineId IS NULL OR machine_id = :machineId)
              AND (:productId IS NULL OR product_id = :productId)
            GROUP BY COALESCE(tenant_id, 'UNASSIGNED')
            ORDER BY revenue DESC
            """, nativeQuery = true)
    java.util.List<RevenueDimensionProjection> findRevenueByTenant(
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate,
            @Param("tenantId") String tenantId, @Param("machineId") String machineId,
            @Param("productId") Long productId);

    @Query(value = """
            SELECT COALESCE(machine_id, 'UNASSIGNED') AS dimensionKey,
                   COUNT(*) AS transactionCount,
                   COALESCE(SUM(amount), 0) AS revenue
            FROM transactions
            WHERE status = 'PAID' AND paid_at >= :startDate AND paid_at < :endDate
              AND (:tenantId IS NULL OR tenant_id = :tenantId)
              AND (:machineId IS NULL OR machine_id = :machineId)
              AND (:productId IS NULL OR product_id = :productId)
            GROUP BY COALESCE(machine_id, 'UNASSIGNED')
            ORDER BY revenue DESC
            """, nativeQuery = true)
    java.util.List<RevenueDimensionProjection> findRevenueByMachine(
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate,
            @Param("tenantId") String tenantId, @Param("machineId") String machineId,
            @Param("productId") Long productId);

    @Query(value = """
            SELECT COALESCE(CAST(product_id AS CHAR), 'UNASSIGNED') AS dimensionKey,
                   COUNT(*) AS transactionCount,
                   COALESCE(SUM(amount), 0) AS revenue
            FROM transactions
            WHERE status = 'PAID' AND paid_at >= :startDate AND paid_at < :endDate
              AND (:tenantId IS NULL OR tenant_id = :tenantId)
              AND (:machineId IS NULL OR machine_id = :machineId)
              AND (:productId IS NULL OR product_id = :productId)
            GROUP BY COALESCE(CAST(product_id AS CHAR), 'UNASSIGNED')
            ORDER BY revenue DESC
            """, nativeQuery = true)
    java.util.List<RevenueDimensionProjection> findRevenueByProduct(
            @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate,
            @Param("tenantId") String tenantId, @Param("machineId") String machineId,
            @Param("productId") Long productId);

    interface RevenueDimensionProjection {
        String getDimensionKey();
        Long getTransactionCount();
        BigDecimal getRevenue();
    }
}
