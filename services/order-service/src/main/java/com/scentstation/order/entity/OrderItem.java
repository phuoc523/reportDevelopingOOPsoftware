package com.scentstation.order.entity;

import java.math.BigDecimal;

public record OrderItem(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
