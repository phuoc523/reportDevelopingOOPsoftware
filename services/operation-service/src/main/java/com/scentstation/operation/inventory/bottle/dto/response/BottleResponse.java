package com.scentstation.operation.inventory.bottle.dto.response;

import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BottleResponse {

    private Long id;

    private Long tenantId;

    private Long productId;

    private Long batchId;

    private String bottleCode;

    private BigDecimal initialQuantity;

    private BigDecimal currentQuantity;

    private BottleStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}