package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceTicket;
import com.scentstation.operation.maintenance.enums.MaintenancePriority;
import com.scentstation.operation.maintenance.enums.MaintenanceSeverity;
import com.scentstation.operation.maintenance.enums.MaintenanceStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MaintenanceTicketRepositoryTest {

    @Autowired
    private MaintenanceTicketRepository maintenanceTicketRepository;

    @Test
    void shouldFindTicketOnlyInsideTenant() {
        MaintenanceTicket tenantOneTicket =
                createTicket(
                        1L,
                        100L,
                        "Tenant 1 ticket"
                );

        MaintenanceTicket tenantTwoTicket =
                createTicket(
                        2L,
                        200L,
                        "Tenant 2 ticket"
                );

        maintenanceTicketRepository.save(tenantOneTicket);
        MaintenanceTicket savedTenantTwo =
                maintenanceTicketRepository.save(
                        tenantTwoTicket
                );

        Optional<MaintenanceTicket> result =
                maintenanceTicketRepository
                        .findByIdAndTenantId(
                                savedTenantTwo.getId(),
                                1L
                        );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldFindTicketsByTenantAndStatus() {
        maintenanceTicketRepository.save(
                createTicket(
                        1L,
                        100L,
                        "Open ticket"
                )
        );

        MaintenanceTicket completed =
                createTicket(
                        1L,
                        100L,
                        "Completed ticket"
                );

        completed.setStatus(
                MaintenanceStatus.COMPLETED
        );

        maintenanceTicketRepository.save(completed);

        List<MaintenanceTicket> tickets =
                maintenanceTicketRepository
                        .findAllByTenantIdAndStatus(
                                1L,
                                MaintenanceStatus.OPEN
                        );

        assertEquals(1, tickets.size());
        assertEquals(
                MaintenanceStatus.OPEN,
                tickets.get(0).getStatus()
        );
    }

    @Test
    void shouldFindTicketsByMachineInsideTenant() {
        maintenanceTicketRepository.save(
                createTicket(
                        1L,
                        100L,
                        "Machine ticket"
                )
        );

        maintenanceTicketRepository.save(
                createTicket(
                        1L,
                        200L,
                        "Other machine"
                )
        );

        List<MaintenanceTicket> result =
                maintenanceTicketRepository
                        .findAllByTenantIdAndMachineId(
                                1L,
                                100L
                        );

        assertEquals(1, result.size());
        assertEquals(
                100L,
                result.get(0).getMachineId()
        );
    }

    // Tạo ticket tối thiểu phục vụ repository test.
    private MaintenanceTicket createTicket(
            Long tenantId,
            Long machineId,
            String title
    ) {
        return MaintenanceTicket.builder()
                .tenantId(tenantId)
                .machineId(machineId)
                .title(title)
                .description("Maintenance test")
                .category("TEST")
                .severity(
                        MaintenanceSeverity.MODERATE
                )
                .priority(
                        MaintenancePriority.MEDIUM
                )
                .status(
                        MaintenanceStatus.OPEN
                )
                .reportedByActorId(999L)
                .build();
    }
}