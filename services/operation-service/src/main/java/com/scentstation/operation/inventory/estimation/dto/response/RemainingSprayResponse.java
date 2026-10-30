package com.scentstation.operation.inventory.estimation.dto.response;

import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemainingSprayResponse {

    private Long bottleId;

    private Long tenantId;

    private BigDecimal currentQuantity;

    private BigDecimal calibratedConsumptionPerSpray;

    private Long estimatedRemainingSprays;

    private Long successfulSprayCount;

    private BottleStatus bottleStatus;
}