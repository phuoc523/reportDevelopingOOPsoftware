package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueDetailResponse(
        LocalDate date,
        long transactionCount,
        BigDecimal revenue
) {}

