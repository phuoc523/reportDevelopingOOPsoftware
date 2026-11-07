package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.PostMaintenanceTest;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PostMaintenanceTestRepositoryTest {

    @Autowired
    private PostMaintenanceTestRepository repository;

    @Test
    void shouldKeepQueriesTenantScopedAndFindPassedTest() {
        repository.save(test(1L, "SHARED", PostMaintenanceTestStatus.PASSED));
        repository.save(test(2L, "SHARED", PostMaintenanceTestStatus.FAILED));

        assertEquals(
                1L,
                repository.findByTenantIdAndExternalTestId(1L, "SHARED")
                        .orElseThrow().getTenantId()
        );
        assertTrue(repository.existsByTenantIdAndTicketIdAndStatus(
                1L, 7L, PostMaintenanceTestStatus.PASSED
        ));
        assertFalse(repository.existsByTenantIdAndTicketIdAndStatus(
                2L, 7L, PostMaintenanceTestStatus.PASSED
        ));
        assertEquals(
                1,
                repository.findAllByTenantIdAndTicketIdOrderByRequestedAtAsc(1L, 7L).size()
        );
    }

    private PostMaintenanceTest test(
            Long tenantId,
            String externalTestId,
            PostMaintenanceTestStatus status
    ) {
        return PostMaintenanceTest.builder()
                .tenantId(tenantId)
                .ticketId(7L)
                .machineId(99L)
                .requestedByActorId(5L)
                .externalTestId(externalTestId)
                .status(status)
                .build();
    }
}
