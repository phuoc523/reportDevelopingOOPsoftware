package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceRepairLog;
import com.scentstation.operation.maintenance.enums.MaintenanceRepairResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MaintenanceRepairLogRepositoryTest {

    @Autowired
    private MaintenanceRepairLogRepository maintenanceRepairLogRepository;

    @Test
    void shouldReturnRepairLogsOnlyInsideTenantAndTicket() {
        maintenanceRepairLogRepository.save(
                createLog(
                        1L,
                        10L,
                        "Tenant 1 ticket"
                )
        );

        maintenanceRepairLogRepository.save(
                createLog(
                        2L,
                        10L,
                        "Other tenant"
                )
        );

        maintenanceRepairLogRepository.save(
                createLog(
                        1L,
                        20L,
                        "Other ticket"
                )
        );

        List<MaintenanceRepairLog> result =
                maintenanceRepairLogRepository
                        .findAllByTenantIdAndTicketIdOrderByCreatedAtAsc(
                                1L,
                                10L
                        );

        assertEquals(1, result.size());

        assertEquals(
                "Tenant 1 ticket",
                result.get(0).getDiagnosticResult()
        );
    }

    @Test
    void shouldDetectExistingRepairResult() {
        maintenanceRepairLogRepository.save(
                createLog(
                        1L,
                        10L,
                        "Pump diagnosed"
                )
        );

        boolean exists =
                maintenanceRepairLogRepository
                        .existsByTenantIdAndTicketId(
                                1L,
                                10L
                        );

        assertTrue(exists);
    }

    @Test
    void shouldNotExposeRepairLogAcrossTenant() {
        MaintenanceRepairLog saved =
                maintenanceRepairLogRepository.save(
                        createLog(
                                2L,
                                10L,
                                "Tenant 2 log"
                        )
                );

        assertTrue(
                maintenanceRepairLogRepository
                        .findByIdAndTenantId(
                                saved.getId(),
                                1L
                        )
                        .isEmpty()
        );
    }

    // Tạo repair log tối thiểu phục vụ repository test.
    private MaintenanceRepairLog createLog(
            Long tenantId,
            Long ticketId,
            String diagnosticResult
    ) {
        return MaintenanceRepairLog.builder()
                .tenantId(tenantId)
                .ticketId(ticketId)
                .diagnosedByActorId(200L)
                .diagnosticResult(diagnosticResult)
                .correctiveAction("Repair action")
                .cost(new BigDecimal("100.00"))
                .repairResult(
                        MaintenanceRepairResult.SUCCESS
                )
                .build();
    }
}