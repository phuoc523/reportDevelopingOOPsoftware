package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RevenueBreakdownResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalPaidTransactions,
        BigDecimal totalRevenue,
        List<RevenueDimensionItem> byTenant,
        List<RevenueDimensionItem> byMachine,
        List<RevenueDimensionItem> byProduct
) {
    public record RevenueDimensionItem(
            String key,
            long transactionCount,
            BigDecimal revenue
    ) {}
}
