package com.scentstation.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RefundSummaryResponse(
        long refundedTransactions,
        BigDecimal totalRefundAmount,
        LocalDate startDate,
        LocalDate endDate
) {}

