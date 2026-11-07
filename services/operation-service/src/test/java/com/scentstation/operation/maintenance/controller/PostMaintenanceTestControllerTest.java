package com.scentstation.operation.maintenance.controller;

import com.scentstation.operation.maintenance.dto.response.PostMaintenanceTestResponse;
import com.scentstation.operation.maintenance.enums.PostMaintenanceTestStatus;
import com.scentstation.operation.maintenance.service.PostMaintenanceTestService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PostMaintenanceTestController.class)
class PostMaintenanceTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostMaintenanceTestService service;

    @Test
    void shouldRequestPostMaintenanceTest() throws Exception {
        when(service.requestTest(eq(7L), any())).thenReturn(response());
        mockMvc.perform(post("/api/maintenance/tickets/7/post-tests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1,"requestedByActorId":5}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.externalTestId").value("EXT-1"))
                .andExpect(jsonPath("$.machineId").value(99))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void shouldGetPostMaintenanceHistory() throws Exception {
        when(service.getHistory(1L, 7L)).thenReturn(List.of(response()));
        mockMvc.perform(get("/api/maintenance/tickets/7/post-tests")
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticketId").value(7));
    }

    @Test
    void shouldReceiveFinalResult() throws Exception {
        PostMaintenanceTestResponse passed = PostMaintenanceTestResponse.builder()
                .id(11L).tenantId(1L).ticketId(7L).machineId(99L)
                .requestedByActorId(5L).externalTestId("EXT-1")
                .status(PostMaintenanceTestStatus.PASSED).build();
        when(service.recordResult(any())).thenReturn(passed);
        mockMvc.perform(post("/api/maintenance/post-tests/results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantId":1,"externalTestId":"EXT-1","status":"PASSED","resultMessage":"ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSED"));
    }

    private PostMaintenanceTestResponse response() {
        return PostMaintenanceTestResponse.builder()
                .id(11L).tenantId(1L).ticketId(7L).machineId(99L)
                .requestedByActorId(5L).externalTestId("EXT-1")
                .status(PostMaintenanceTestStatus.REQUESTED).build();
    }
}
