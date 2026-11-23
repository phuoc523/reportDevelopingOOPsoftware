package com.scentstation.reporting.service;

import com.scentstation.reporting.dto.*;
import com.scentstation.reporting.repository.TransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdvancedReportService {
    private final TransactionRepository repository;

    public AdvancedReportService(TransactionRepository repository) {
        this.repository = repository;
    }

    public List<RevenueDetailResponse> revenueDetails(LocalDate start, LocalDate end) {
        validateDates(start, end);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        return repository.findDailyRevenue(from, to).stream()
                .map(row -> new RevenueDetailResponse(
                        row.getReportDate().toLocalDate(),
                        row.getTransactionCount(),
                        row.getRevenue()))
                .toList();
    }

    public List<RevenueDetailResponse> revenueDetails(LocalDate start, LocalDate end,
            String tenantId, String machineId, Long productId) {
        validateDates(start, end);
        return repository.findDailyRevenueScoped(start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                tenantId, machineId, productId).stream()
                .map(row -> new RevenueDetailResponse(row.getReportDate().toLocalDate(),
                        row.getTransactionCount(), row.getRevenue())).toList();
    }

    public RevenueBreakdownResponse revenueBreakdown(LocalDate start, LocalDate end,
            String tenantId, String machineId, Long productId) {
        validateDates(start, end);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        var byTenant = mapDimensions(repository.findRevenueByTenant(from, to, tenantId, machineId, productId));
        var byMachine = mapDimensions(repository.findRevenueByMachine(from, to, tenantId, machineId, productId));
        var byProduct = mapDimensions(repository.findRevenueByProduct(from, to, tenantId, machineId, productId));
        long totalPaid = byTenant.stream().mapToLong(RevenueBreakdownResponse.RevenueDimensionItem::transactionCount).sum();
        BigDecimal totalRevenue = byTenant.stream()
                .map(RevenueBreakdownResponse.RevenueDimensionItem::revenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new RevenueBreakdownResponse(start, end, totalPaid, totalRevenue, byTenant, byMachine, byProduct);
    }

    private List<RevenueBreakdownResponse.RevenueDimensionItem> mapDimensions(
            List<TransactionRepository.RevenueDimensionProjection> rows) {
        if (rows == null) return List.of();
        return rows.stream()
                .map(row -> new RevenueBreakdownResponse.RevenueDimensionItem(
                        row.getDimensionKey(),
                        row.getTransactionCount() == null ? 0L : row.getTransactionCount(),
                        row.getRevenue() == null ? BigDecimal.ZERO : row.getRevenue()))
                .toList();
    }

    public TransactionStatusSummaryResponse statusSummary(LocalDate start, LocalDate end) {
        validateDates(start, end);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        long total = repository.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(from, to);
        long paid = count("PAID", from, to);
        long pending = count("PENDING", from, to);
        long failed = count("FAILED", from, to);
        long expired = count("EXPIRED", from, to);
        long refunded = count("REFUNDED", from, to);
        return new TransactionStatusSummaryResponse(total, paid, pending, failed, expired,
                refunded, rate(paid, total), rate(pending, total), rate(failed, total),
                rate(expired, total), rate(refunded, total), start, end);
    }

    public TransactionStatusSummaryResponse statusSummary(LocalDate start, LocalDate end,
            String tenantId, String machineId, Long productId) {
        validateDates(start, end);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        long total = repository.countScoped(from, to, null, tenantId, machineId, productId);
        long paid = repository.countScoped(from, to, "PAID", tenantId, machineId, productId);
        long pending = repository.countScoped(from, to, "PENDING", tenantId, machineId, productId);
        long failed = repository.countScoped(from, to, "FAILED", tenantId, machineId, productId);
        long expired = repository.countScoped(from, to, "EXPIRED", tenantId, machineId, productId);
        long refunded = repository.countScoped(from, to, "REFUNDED", tenantId, machineId, productId);
        return new TransactionStatusSummaryResponse(total, paid, pending, failed, expired, refunded,
                rate(paid, total), rate(pending, total), rate(failed, total), rate(expired, total),
                rate(refunded, total), start, end);
    }

    public RefundSummaryResponse refundSummary(LocalDate start, LocalDate end) {
        validateDates(start, end);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        long count = count("REFUNDED", from, to);
        BigDecimal amount = repository.sumRefundAmountBetween(from, to);
        return new RefundSummaryResponse(count, amount, start, end);
    }

    public RefundSummaryResponse refundSummary(LocalDate start, LocalDate end,
            String tenantId, String machineId, Long productId) {
        validateDates(start, end);
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime to = end.plusDays(1).atStartOfDay();
        long count = repository.countScoped(from, to, "REFUNDED", tenantId, machineId, productId);
        return new RefundSummaryResponse(count,
                repository.sumRefundAmountScoped(from, to, tenantId, machineId, productId), start, end);
    }

    private long count(String status, LocalDateTime from, LocalDateTime to) {
        return repository.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                status, from, to);
    }

    private double rate(long count, long total) {
        return total == 0 ? 0.0 : Math.round((count * 10000.0) / total) / 100.0;
    }

    private void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate and endDate are required");
        if (start.isAfter(end))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
    }
}

