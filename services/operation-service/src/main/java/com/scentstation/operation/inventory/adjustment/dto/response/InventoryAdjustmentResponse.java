package com.scentstation.operation.inventory.adjustment.dto.response;

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
public class InventoryAdjustmentResponse {

    private Long id;

    private Long tenantId;

    private Long bottleId;

    private BigDecimal quantityBefore;

    private BigDecimal quantityAfter;

    private BigDecimal difference;

    private Long actorId;

    private String reason;

    private BottleStatus bottleStatus;

    private LocalDateTime createdAt;
}