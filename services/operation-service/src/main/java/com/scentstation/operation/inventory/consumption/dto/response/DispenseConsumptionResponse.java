package com.scentstation.operation.inventory.consumption.dto.response;

import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
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
public class DispenseConsumptionResponse {

    private Long id;

    private String commandId;

    private Long bottleId;

    private Long machineId;

    private Long slotId;

    private DispenseType dispenseType;

    private DispenseResult result;

    private BigDecimal consumedQuantity;

    private BigDecimal quantityBefore;

    private BigDecimal quantityAfter;

    private Boolean inventoryDeducted;

    private BottleStatus bottleStatus;

    private LocalDateTime processedAt;
}