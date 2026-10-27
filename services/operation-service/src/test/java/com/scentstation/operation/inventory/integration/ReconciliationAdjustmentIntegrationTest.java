package com.scentstation.operation.inventory.integration;

import com.scentstation.operation.inventory.adjustment.dto.request.AdjustInventoryRequest;
import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.reconciliation.dto.request.RecordSensorReadingRequest;
import com.scentstation.operation.inventory.reconciliation.enums.ReconciliationStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconciliationAdjustmentIntegrationTest extends InventoryIntegrationTestSupport {

    @Test
    void reconciliationObservesAndAdjustmentPerformsTheInventoryMutation() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "RECON-BATCH");
        var bottle = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "RECON-BOTTLE", "20.000");
        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, bottle.getId());

        var reconciliation = reconciliationService.reconcile(RecordSensorReadingRequest.builder()
                .tenantId(TENANT_ID)
                .sensorEventId("SENSOR-001")
                .bottleId(bottle.getId())
                .machineId(MACHINE_ID)
                .slotId(SLOT_ID)
                .sensorQuantity(new BigDecimal("14.000"))
                .measuredAt(LocalDateTime.now())
                .build());

        assertEquals(0, new BigDecimal("20.000").compareTo(reconciliation.getPredictedQuantity()));
        assertEquals(0, new BigDecimal("6.000").compareTo(reconciliation.getDiscrepancy()));
        assertEquals(ReconciliationStatus.POSSIBLE_LEAK, reconciliation.getStatus());
        assertEquals(0, new BigDecimal("20.000").compareTo(
                bottleRepository.findById(bottle.getId()).orElseThrow().getCurrentQuantity()));

        adjustmentService.adjust(AdjustInventoryRequest.builder()
                .tenantId(TENANT_ID)
                .bottleId(bottle.getId())
                .newQuantity(new BigDecimal("14.000"))
                .actorId(ACTOR_ID)
                .reason("Confirmed by sensor")
                .build());

        assertEquals(0, new BigDecimal("14.000").compareTo(
                bottleRepository.findById(bottle.getId()).orElseThrow().getCurrentQuantity()));
        assertEquals(1, adjustmentRepository
                .findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(TENANT_ID, bottle.getId()).size());
        var movement = movementRepository
                .findAllByTenantIdAndBottleIdOrderByCreatedAtDesc(TENANT_ID, bottle.getId()).getFirst();
        assertEquals(ACTOR_ID, movement.getActorId());
        assertEquals("Confirmed by sensor", movement.getReason());
        var audit = auditRepository.findAllByTenantIdAndActionOrderByCreatedAtDesc(
                TENANT_ID, InventoryAuditAction.INVENTORY_ADJUSTMENT).getFirst();
        assertEquals("20.000", audit.getBeforeValue());
        assertEquals("14.000", audit.getAfterValue());
        assertEquals(ACTOR_ID, audit.getActorId());
    }
}
