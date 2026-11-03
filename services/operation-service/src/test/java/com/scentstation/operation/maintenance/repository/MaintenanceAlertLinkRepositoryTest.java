package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceAlertLink;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MaintenanceAlertLinkRepositoryTest {

    @Autowired
    private MaintenanceAlertLinkRepository maintenanceAlertLinkRepository;

    @Test
    void shouldFindAlertLinkInsideTenant() {
        maintenanceAlertLinkRepository.save(
                createLink(
                        1L,
                        "ALERT-001",
                        100L
                )
        );

        assertTrue(
                maintenanceAlertLinkRepository
                        .findByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
                        .isPresent()
        );
    }

    @Test
    void shouldNotExposeAlertAcrossTenant() {
        maintenanceAlertLinkRepository.save(
                createLink(
                        2L,
                        "ALERT-001",
                        100L
                )
        );

        assertTrue(
                maintenanceAlertLinkRepository
                        .findByTenantIdAndSourceAlertId(
                                1L,
                                "ALERT-001"
                        )
                        .isEmpty()
        );
    }

    @Test
    void shouldRejectDuplicateAlertInsideSameTenant() {
        maintenanceAlertLinkRepository.saveAndFlush(
                createLink(
                        1L,
                        "ALERT-001",
                        100L
                )
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () ->
                        maintenanceAlertLinkRepository
                                .saveAndFlush(
                                        createLink(
                                                1L,
                                                "ALERT-001",
                                                101L
                                        )
                                )
        );
    }

    @Test
    void shouldAllowSameAlertIdAcrossTenants() {
        maintenanceAlertLinkRepository.saveAndFlush(
                createLink(1L, "ALERT-SHARED", 100L)
        );
        maintenanceAlertLinkRepository.saveAndFlush(
                createLink(2L, "ALERT-SHARED", 200L)
        );

        assertEquals(2, maintenanceAlertLinkRepository.count());
    }

    // Tạo alert link tối thiểu phục vụ repository test.
    private MaintenanceAlertLink createLink(
            Long tenantId,
            String alertId,
            Long ticketId
    ) {
        return MaintenanceAlertLink.builder()
                .tenantId(tenantId)
                .sourceAlertId(alertId)
                .alertType("PUMP_FAILURE")
                .ticketId(ticketId)
                .machineId(10L)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
