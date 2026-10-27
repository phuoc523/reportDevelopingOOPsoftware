package com.scentstation.operation.inventory.integration;

import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.bottle.exception.BottleNotFoundException;
import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.ReplaceBottleRequest;
import com.scentstation.operation.inventory.integration.machine.exception.MachineSlotProductMismatchException;
import com.scentstation.operation.inventory.integration.machine.exception.MachineSlotUnavailableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class InstallationContractIntegrationTest extends InventoryIntegrationTestSupport {

    @Test
    void compatibleSlotInstallsAndRejectedSlotsLeaveNoPartialState() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "SLOT-BATCH");
        var compatible = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "SLOT-OK", "10.000");
        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, compatible.getId());
        assertTrue(installationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(TENANT_ID, compatible.getId()).isPresent());

        assertRejectedWithoutMutation(batch.getId(), "SLOT-INACTIVE", false, false, PRODUCT_ID,
                MachineSlotUnavailableException.class);
        assertRejectedWithoutMutation(batch.getId(), "SLOT-MAINT", true, true, PRODUCT_ID,
                MachineSlotUnavailableException.class);
        assertRejectedWithoutMutation(batch.getId(), "SLOT-MISMATCH", true, false, 999L,
                MachineSlotProductMismatchException.class);
    }

    @Test
    void failedReplacementPreservesOldAndNewBottleState() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "REPLACE-BATCH");
        var oldBottle = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "REPLACE-OLD", "10.000");
        var newBottle = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "REPLACE-NEW", "10.000");
        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, oldBottle.getId());
        long installations = installationRepository.count();
        long movements = movementRepository.count();
        long audits = auditRepository.count();

        stubSlot(TENANT_ID, 999L, true, false);
        assertThrows(MachineSlotProductMismatchException.class,
                () -> installationService.replaceBottle(ReplaceBottleRequest.builder()
                        .tenantId(TENANT_ID)
                        .oldBottleId(oldBottle.getId())
                        .newBottleId(newBottle.getId())
                        .actorId(ACTOR_ID)
                        .reason("Routine replacement")
                        .build()));

        assertTrue(installationRepository
                .findByTenantIdAndBottleIdAndActiveTrue(TENANT_ID, oldBottle.getId()).isPresent());
        assertEquals(BottleStatus.INSTALLED,
                bottleRepository.findById(oldBottle.getId()).orElseThrow().getStatus());
        assertEquals(BottleStatus.AVAILABLE,
                bottleRepository.findById(newBottle.getId()).orElseThrow().getStatus());
        assertEquals(installations, installationRepository.count());
        assertEquals(movements, movementRepository.count());
        assertEquals(audits, auditRepository.count());
    }

    @Test
    void tenantCannotReadAdjustOrInstallAnotherTenantsBottle() {
        var batch = createBatch(OTHER_TENANT_ID, PRODUCT_ID, "TENANT-BATCH");
        var bottle = createBottle(
                OTHER_TENANT_ID, PRODUCT_ID, batch.getId(), "TENANT-BOTTLE", "10.000");

        assertThrows(BottleNotFoundException.class,
                () -> bottleService.getBottleById(TENANT_ID, bottle.getId()));
        assertThrows(BottleNotFoundException.class,
                () -> movementService.getBottleHistory(TENANT_ID, bottle.getId()));
        assertTrue(auditRepository
                .findAllByTenantIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        TENANT_ID, "BOTTLE", bottle.getId()).isEmpty());
        assertThrows(BottleNotFoundException.class,
                () -> adjustmentService.adjust(com.scentstation.operation.inventory.adjustment.dto.request
                        .AdjustInventoryRequest.builder()
                        .tenantId(TENANT_ID)
                        .bottleId(bottle.getId())
                        .newQuantity(java.math.BigDecimal.ONE)
                        .actorId(ACTOR_ID)
                        .reason("Wrong tenant")
                        .build()));
        assertThrows(BottleNotFoundException.class,
                () -> installationService.installBottle(InstallBottleRequest.builder()
                        .tenantId(TENANT_ID)
                        .bottleId(bottle.getId())
                        .machineId(MACHINE_ID)
                        .slotId(SLOT_ID)
                        .actorId(ACTOR_ID)
                        .build()));
    }

    private void assertRejectedWithoutMutation(Long batchId, String code, boolean active,
                                               boolean maintenance, Long slotProduct,
                                               Class<? extends RuntimeException> exceptionType) {
        var bottle = createBottle(TENANT_ID, PRODUCT_ID, batchId, code, "10.000");
        long installations = installationRepository.count();
        long movements = movementRepository.count();
        long audits = auditRepository.count();
        long rejectedSlotId = SLOT_ID + 1;
        when(machineSlotClient.getSlot(TENANT_ID, MACHINE_ID, rejectedSlotId))
                .thenReturn(com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo.builder()
                        .tenantId(TENANT_ID)
                        .machineId(MACHINE_ID)
                        .slotId(rejectedSlotId)
                        .productId(slotProduct)
                        .active(active)
                        .maintenanceMode(maintenance)
                        .build());

        assertThrows(exceptionType, () -> installationService.installBottle(InstallBottleRequest.builder()
                .tenantId(TENANT_ID)
                .bottleId(bottle.getId())
                .machineId(MACHINE_ID)
                .slotId(rejectedSlotId)
                .actorId(ACTOR_ID)
                .build()));

        assertEquals(BottleStatus.AVAILABLE,
                bottleRepository.findById(bottle.getId()).orElseThrow().getStatus());
        assertEquals(installations, installationRepository.count());
        assertEquals(movements, movementRepository.count());
        assertEquals(audits, auditRepository.count());
    }
}
