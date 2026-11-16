package com.scentstation.order.dto.response;

import java.util.List;

public record PaymentReconciliationResponse(
        int checkedTransactions,
        int matchedTransactions,
        int discrepancyCount,
        List<TransactionReviewResponse> discrepancies
) {
}
