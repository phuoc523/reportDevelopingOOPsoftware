package com.scentstation.operation.inventory.refill.dto.response;

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
public class RefillBottleResponse {

    private Long bottleId;

    private Long tenantId;

    private BigDecimal quantityBefore;

    private BigDecimal refillQuantity;

    private BigDecimal quantityAfter;

    private BottleStatus status;

    private Long actorId;

    private String reason;

    private LocalDateTime refilledAt;
}