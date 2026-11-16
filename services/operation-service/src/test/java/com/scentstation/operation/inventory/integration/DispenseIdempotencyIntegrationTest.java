package com.scentstation.operation.inventory.integration;

import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.scentstation.operation.inventory.movement.enums.BottleMovementType;

class DispenseIdempotencyIntegrationTest extends InventoryIntegrationTestSupport {

    @Test
    void duplicateCommandDoesNotDeductPersistOrAuditTwice() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "IDEM-BATCH");
        var bottle = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "IDEM-BOTTLE", "10.000");
        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, bottle.getId());

        var request = RecordDispenseRequest.builder()
                .tenantId(TENANT_ID)
                .commandId("CMD-IDEMPOTENT")
                .bottleId(bottle.getId())
                .machineId(MACHINE_ID)
                .slotId(SLOT_ID)
                .dispenseType(DispenseType.CUSTOMER)
                .result(DispenseResult.SUCCESS)
                .consumedQuantity(new BigDecimal("1.250"))
                .build();

        dispenseService.recordDispense(request);
        assertThrows(DuplicateDispenseCommandException.class,
                () -> dispenseService.recordDispense(request));

        assertEquals(0, new BigDecimal("8.750").compareTo(
                bottleRepository.findById(bottle.getId()).orElseThrow().getCurrentQuantity()));
        assertEquals(1, consumptionRepository
                .findAllByTenantIdAndBottleIdOrderByProcessedAtDesc(TENANT_ID, bottle.getId()).size());
        assertEquals(1, auditRepository
                .findAllByTenantIdAndActionOrderByCreatedAtDesc(
                        TENANT_ID, InventoryAuditAction.DISPENSE_RECORDED).size());
        assertEquals(1, movementRepository
                .findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                        TENANT_ID, BottleMovementType.CUSTOMER_CONSUMPTION).size());
    }

    @Test
    void diagnosticSuccessIsClassifiedSeparatelyAndFailureDoesNotCreateMovement() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "DIAG-BATCH");
        var bottle = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "DIAG-BOTTLE", "10.000");
        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, bottle.getId());

        dispenseService.recordDispense(request(bottle.getId(), "CMD-DIAG", DispenseType.DIAGNOSTIC,
                DispenseResult.SUCCESS));
        dispenseService.recordDispense(request(bottle.getId(), "CMD-FAILED", DispenseType.CUSTOMER,
                DispenseResult.FAILED));
        dispenseService.recordDispense(request(bottle.getId(), "CMD-UNCERTAIN", DispenseType.CUSTOMER,
                DispenseResult.UNCERTAIN));

        assertEquals(0, new BigDecimal("9.000").compareTo(
                bottleRepository.findById(bottle.getId()).orElseThrow().getCurrentQuantity()));
        assertEquals(1, movementRepository
                .findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                        TENANT_ID, BottleMovementType.DIAGNOSTIC_CONSUMPTION).size());
        assertEquals(0, movementRepository
                .findAllByTenantIdAndMovementTypeOrderByCreatedAtDesc(
                        TENANT_ID, BottleMovementType.CUSTOMER_CONSUMPTION).size());
    }

    // Tạo dispense result với cùng bottle và vị trí để test phân loại kết quả.
    private RecordDispenseRequest request(Long bottleId, String commandId,
                                          DispenseType type, DispenseResult result) {
        return RecordDispenseRequest.builder()
                .tenantId(TENANT_ID)
                .commandId(commandId)
                .bottleId(bottleId)
                .machineId(MACHINE_ID)
                .slotId(SLOT_ID)
                .dispenseType(type)
                .result(result)
                .consumedQuantity(BigDecimal.ONE)
                .build();
    }
}
