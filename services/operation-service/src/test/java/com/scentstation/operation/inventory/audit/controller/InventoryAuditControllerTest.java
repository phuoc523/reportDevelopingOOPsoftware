package com.scentstation.operation.inventory.audit.controller;

import com.scentstation.operation.inventory.audit.enums.InventoryAuditAction;
import com.scentstation.operation.inventory.audit.service.InventoryAuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryAuditController.class)
class InventoryAuditControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryAuditService auditService;

    @Test
    void shouldGetTenantFilteredAndEntityHistory() throws Exception {
        when(auditService.getTenantHistory(1L)).thenReturn(List.of());
        when(auditService.getHistoryByAction(
                1L,
                InventoryAuditAction.INVENTORY_ADJUSTMENT
        )).thenReturn(List.of());
        when(auditService.getEntityHistory(1L, "BOTTLE", 10L))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/inventory/audit-events?tenantId=1"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/inventory/audit-events")
                        .param("tenantId", "1")
                        .param("action", "INVENTORY_ADJUSTMENT"))
                .andExpect(status().isOk());
        mockMvc.perform(get(
                        "/api/inventory/audit-events/entities/BOTTLE/10"
                ).param("tenantId", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectInvalidTenantAndEntityIds() throws Exception {
        mockMvc.perform(get("/api/inventory/audit-events?tenantId=0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(
                        "/api/inventory/audit-events/entities/BOTTLE/0"
                ).param("tenantId", "1"))
                .andExpect(status().isBadRequest());
    }
}
