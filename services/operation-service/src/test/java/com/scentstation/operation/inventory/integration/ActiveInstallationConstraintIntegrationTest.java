package com.scentstation.operation.inventory.integration;

import com.scentstation.operation.inventory.installation.entity.BottleInstallation;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ActiveInstallationConstraintIntegrationTest extends InventoryIntegrationTestSupport {

    @Test
    void databaseRejectsSecondActiveBottleInSameSlot() {
        var batch = createBatch(TENANT_ID, PRODUCT_ID, "ACTIVE-SLOT-BATCH");
        var first = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "ACTIVE-FIRST", "10.000");
        var second = createBottle(TENANT_ID, PRODUCT_ID, batch.getId(), "ACTIVE-SECOND", "10.000");
        stubSlot(TENANT_ID, PRODUCT_ID, true, false);
        install(TENANT_ID, first.getId());
        installationRepository.flush();

        BottleInstallation duplicate = BottleInstallation.builder()
                .tenantId(TENANT_ID)
                .bottleId(second.getId())
                .machineId(MACHINE_ID)
                .slotId(SLOT_ID)
                .installedBy(ACTOR_ID)
                .quantityBeforeInstallation(new BigDecimal("10.000"))
                .active(true)
                .build();

        assertThrows(DataIntegrityViolationException.class,
                () -> installationRepository.saveAndFlush(duplicate));
    }
}
