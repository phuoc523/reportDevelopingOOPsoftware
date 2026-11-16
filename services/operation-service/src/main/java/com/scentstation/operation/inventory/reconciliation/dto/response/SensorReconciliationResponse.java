package com.scentstation.operation.inventory.reconciliation.dto.response;

import com.scentstation.operation.inventory.reconciliation.enums.ReconciliationStatus;
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
public class SensorReconciliationResponse {

    private Long id;

    private String sensorEventId;

    private Long bottleId;

    private Long machineId;

    private Long slotId;

    private BigDecimal predictedQuantity;

    private BigDecimal sensorQuantity;

    private BigDecimal discrepancy;

    private ReconciliationStatus status;

    private Boolean lowStock;

    private Long estimatedRemainingSprays;

    private LocalDateTime measuredAt;

    private LocalDateTime processedAt;
}