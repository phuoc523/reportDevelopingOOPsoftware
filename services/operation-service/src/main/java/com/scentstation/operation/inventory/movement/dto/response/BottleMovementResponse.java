package com.scentstation.operation.inventory.movement.dto.response;

import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
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
public class BottleMovementResponse {

    private Long id;

    private Long tenantId;

    private Long bottleId;

    private BottleMovementType movementType;

    private Long machineId;

    private Long slotId;

    private BigDecimal quantityBefore;

    private BigDecimal quantityAfter;

    private BigDecimal difference;

    private Long actorId;

    private String reason;

    private LocalDateTime createdAt;
}