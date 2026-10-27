package com.scentstation.operation.inventory.integration;

import com.scentstation.operation.inventory.adjustment.repository.InventoryAdjustmentRepository;
import com.scentstation.operation.inventory.adjustment.service.InventoryAdjustmentService;
import com.scentstation.operation.inventory.audit.repository.InventoryAuditEventRepository;
import com.scentstation.operation.inventory.batch.dto.request.CreateInventoryBatchRequest;
import com.scentstation.operation.inventory.batch.dto.response.InventoryBatchResponse;
import com.scentstation.operation.inventory.batch.repository.InventoryBatchRepository;
import com.scentstation.operation.inventory.batch.service.InventoryBatchService;
import com.scentstation.operation.inventory.bottle.dto.request.CreateBottleRequest;
import com.scentstation.operation.inventory.bottle.dto.response.BottleResponse;
import com.scentstation.operation.inventory.bottle.repository.BottleRepository;
import com.scentstation.operation.inventory.bottle.service.BottleService;
import com.scentstation.operation.inventory.consumption.repository.DispenseConsumptionRepository;
import com.scentstation.operation.inventory.consumption.service.DispenseConsumptionService;
import com.scentstation.operation.inventory.estimation.service.RemainingSprayService;
import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.repository.BottleInstallationRepository;
import com.scentstation.operation.inventory.installation.service.BottleInstallationService;
import com.scentstation.operation.inventory.integration.machine.client.MachineSlotClient;
import com.scentstation.operation.inventory.integration.machine.dto.MachineSlotInfo;
import com.scentstation.operation.inventory.movement.repository.BottleMovementRepository;
import com.scentstation.operation.inventory.movement.service.BottleMovementService;
import com.scentstation.operation.inventory.reconciliation.repository.SensorReconciliationRepository;
import com.scentstation.operation.inventory.reconciliation.service.SensorReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
abstract class InventoryIntegrationTestSupport {

    static final Long TENANT_ID = 1L;
    static final Long OTHER_TENANT_ID = 2L;
    static final Long PRODUCT_ID = 101L;
    static final Long MACHINE_ID = 201L;
    static final Long SLOT_ID = 301L;
    static final Long ACTOR_ID = 401L;

    @Autowired InventoryBatchService batchService;
    @Autowired BottleService bottleService;
    @Autowired BottleInstallationService installationService;
    @Autowired DispenseConsumptionService dispenseService;
    @Autowired RemainingSprayService remainingSprayService;
    @Autowired SensorReconciliationService reconciliationService;
    @Autowired InventoryAdjustmentService adjustmentService;
    @Autowired BottleMovementService movementService;

    @Autowired InventoryBatchRepository batchRepository;
    @Autowired BottleRepository bottleRepository;
    @Autowired BottleInstallationRepository installationRepository;
    @Autowired DispenseConsumptionRepository consumptionRepository;
    @Autowired SensorReconciliationRepository reconciliationRepository;
    @Autowired InventoryAdjustmentRepository adjustmentRepository;
    @Autowired BottleMovementRepository movementRepository;
    @Autowired InventoryAuditEventRepository auditRepository;

    @MockBean MachineSlotClient machineSlotClient;

    InventoryBatchResponse createBatch(Long tenantId, Long productId, String code) {
        return batchService.createBatch(CreateInventoryBatchRequest.builder()
                .tenantId(tenantId)
                .productId(productId)
                .batchCode(code)
                .receivedQuantity(10)
                .receivedAt(LocalDate.now())
                .expiryDate(LocalDate.now().plusYears(1))
                .build());
    }

    BottleResponse createBottle(Long tenantId, Long productId, Long batchId,
                                String code, String quantity) {
        return bottleService.createBottle(CreateBottleRequest.builder()
                .tenantId(tenantId)
                .productId(productId)
                .batchId(batchId)
                .bottleCode(code)
                .initialQuantity(new BigDecimal(quantity))
                .build());
    }

    void stubSlot(Long tenantId, Long productId, boolean active, boolean maintenanceMode) {
        when(machineSlotClient.getSlot(tenantId, MACHINE_ID, SLOT_ID))
                .thenReturn(MachineSlotInfo.builder()
                        .tenantId(tenantId)
                        .machineId(MACHINE_ID)
                        .slotId(SLOT_ID)
                        .productId(productId)
                        .active(active)
                        .maintenanceMode(maintenanceMode)
                        .build());
    }

    void install(Long tenantId, Long bottleId) {
        installationService.installBottle(InstallBottleRequest.builder()
                .tenantId(tenantId)
                .bottleId(bottleId)
                .machineId(MACHINE_ID)
                .slotId(SLOT_ID)
                .actorId(ACTOR_ID)
                .build());
    }
}
