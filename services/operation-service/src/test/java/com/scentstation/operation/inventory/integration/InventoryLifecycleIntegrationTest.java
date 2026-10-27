package com.scentstation.operation.inventory.integration;

import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
import com.scentstation.operation.inventory.estimation.dto.request.UpdateSprayCalibrationRequest;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryLifecycleIntegrationTest extends InventoryIntegrationTestSupport {

    @Test
    void persistsLifecycleAcrossServicesRepositoriesMovementAndAudit() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "LIFE-BATCH");
        BottleResponse bottle = createBottle(
                TENANT_ID, PRODUCT_ID, batch.getId(), "LIFE-BOTTLE", "100.000");

        assertTrue(batchRepository.findByIdAndTenantId(batch.getId(), TENANT_ID).isPresent());
        assertEquals(PRODUCT_ID, bottleRepository.findById(bottle.getId()).orElseThrow().getProductId());

        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, bottle.getId());
        assertEquals(BottleStatus.INSTALLED,
                bottleRepository.findById(bottle.getId()).orElseThrow().getStatus());

        remainingSprayService.updateCalibration(UpdateSprayCalibrationRequest.builder()
                .tenantId(TENANT_ID)
                .bottleId(bottle.getId())
                .consumptionPerSpray(new BigDecimal("0.500"))
                .build());

        dispenseService.recordDispense(RecordDispenseRequest.builder()
                .tenantId(TENANT_ID)
                .commandId("LIFE-CMD")
                .bottleId(bottle.getId())
                .machineId(MACHINE_ID)
                .slotId(SLOT_ID)
                .dispenseType(DispenseType.CUSTOMER)
                .result(DispenseResult.SUCCESS)
                .consumedQuantity(new BigDecimal("2.000"))
                .build());

        assertEquals(0, new BigDecimal("98.000").compareTo(
                bottleRepository.findById(bottle.getId()).orElseThrow().getCurrentQuantity()));
        assertEquals(196L,
                remainingSprayService.getRemainingSprays(TENANT_ID, bottle.getId())
                        .getEstimatedRemainingSprays());
        assertEquals(1, consumptionRepository.count());
        assertEquals(1, movementRepository
                .findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                        TENANT_ID, BottleMovementType.INSTALL).size());
        assertEquals(1, auditRepository
                .findAllByTenantIdAndActionOrderByCreatedAtDesc(
                        TENANT_ID, InventoryAuditAction.DISPENSE_RECORDED).size());
    }
}
