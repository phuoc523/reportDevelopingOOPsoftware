package com.scentstation.operation.maintenance.repository;

import com.scentstation.operation.maintenance.entity.MaintenanceChecklistItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MaintenanceChecklistRepositoryTest {

    @Autowired
    private MaintenanceChecklistRepository maintenanceChecklistRepository;

    @Test
    void shouldReturnChecklistOnlyInsideTenantAndTicket() {
        maintenanceChecklistRepository.save(
                createItem(
                        1L,
                        10L,
                        "Check pump",
                        true
                )
        );

        maintenanceChecklistRepository.save(
                createItem(
                        2L,
                        10L,
                        "Other tenant",
                        true
                )
        );

        maintenanceChecklistRepository.save(
                createItem(
                        1L,
                        20L,
                        "Other ticket",
                        true
                )
        );

        List<MaintenanceChecklistItem> result =
                maintenanceChecklistRepository
                        .findAllByTenantIdAndTicketIdOrderByIdAsc(
                                1L,
                                10L
                        );

        assertEquals(1, result.size());
        assertEquals(
                "Check pump",
                result.get(0).getItemName()
        );
    }

    @Test
    void shouldDetectIncompleteRequiredChecklist() {
        maintenanceChecklistRepository.save(
                createItem(
                        1L,
                        10L,
                        "Required item",
                        true
                )
        );

        boolean result =
                maintenanceChecklistRepository
                        .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                                1L,
                                10L
                        );

        assertTrue(result);
    }

    @Test
    void shouldNotReportIncompleteWhenRequiredChecklistCompleted() {
        MaintenanceChecklistItem item =
                createItem(
                        1L,
                        10L,
                        "Required item",
                        true
                );

        item.setCompleted(true);

        maintenanceChecklistRepository.save(item);

        boolean result =
                maintenanceChecklistRepository
                        .existsByTenantIdAndTicketIdAndRequiredTrueAndCompletedFalse(
                                1L,
                                10L
                        );

        assertFalse(result);
    }

    // Tạo checklist item tối thiểu phục vụ repository test.
    private MaintenanceChecklistItem createItem(
            Long tenantId,
            Long ticketId,
            String name,
            boolean required
    ) {
        return MaintenanceChecklistItem.builder()
                .tenantId(tenantId)
                .ticketId(ticketId)
                .itemName(name)
                .required(required)
                .completed(false)
                .build();
    }
}