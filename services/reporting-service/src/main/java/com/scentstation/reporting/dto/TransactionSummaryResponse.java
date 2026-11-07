
package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionSummaryResponse(
        long totalTransactions,
        long successfulTransactions,
        long pendingTransactions,
        long failedTransactions,
        long expiredTransactions,
        long refundedTransactions,
        BigDecimal totalRevenue,
        String period,
        LocalDate startDate,
        LocalDate endDate
) {
}
