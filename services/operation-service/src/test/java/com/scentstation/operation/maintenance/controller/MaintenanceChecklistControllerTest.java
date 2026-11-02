package com.scentstation.operation.maintenance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.common.exception.OperationExceptionHandler;
import com.scentstation.operation.maintenance.dto.request.CompleteChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.request.CreateChecklistItemRequest;
import com.scentstation.operation.maintenance.dto.response.MaintenanceChecklistItemResponse;
import com.scentstation.operation.maintenance.exception.MaintenanceChecklistNotFoundException;
import com.scentstation.operation.maintenance.service.MaintenanceChecklistService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MaintenanceChecklistController.class)
@Import(OperationExceptionHandler.class)
class MaintenanceChecklistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MaintenanceChecklistService
            maintenanceChecklistService;

    @Test
    void shouldCreateChecklistItem() throws Exception {
        CreateChecklistItemRequest request =
                new CreateChecklistItemRequest();

        request.setTenantId(1L);
        request.setItemName("Check pump");
        request.setDescription(
                "Verify pump operation"
        );
        request.setRequired(true);

        MaintenanceChecklistItemResponse response =
                MaintenanceChecklistItemResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .ticketId(10L)
                        .itemName("Check pump")
                        .required(true)
                        .completed(false)
                        .build();

        when(
                maintenanceChecklistService
                        .createChecklistItem(
                                eq(10L),
                                any(CreateChecklistItemRequest.class)
                        )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/checklist"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(
                        jsonPath("$.ticketId").value(10)
                )
                .andExpect(
                        jsonPath("$.itemName")
                                .value("Check pump")
                )
                .andExpect(
                        jsonPath("$.required").value(true)
                )
                .andExpect(
                        jsonPath("$.completed").value(false)
                );
    }

    @Test
    void shouldReturnChecklistByTicket() throws Exception {
        MaintenanceChecklistItemResponse response =
                MaintenanceChecklistItemResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .ticketId(10L)
                        .itemName("Check pump")
                        .required(true)
                        .completed(false)
                        .build();

        when(
                maintenanceChecklistService
                        .getChecklist(
                                1L,
                                10L
                        )
        ).thenReturn(List.of(response));

        mockMvc.perform(
                        get(
                                "/api/maintenance/tickets/10/checklist"
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$[0].id").value(5)
                )
                .andExpect(
                        jsonPath("$[0].ticketId").value(10)
                );
    }

    @Test
    void shouldCompleteChecklistItem() throws Exception {
        CompleteChecklistItemRequest request =
                new CompleteChecklistItemRequest();

        request.setTenantId(1L);
        request.setCompletedByActorId(200L);
        request.setResultNote(
                "Pump inspected successfully"
        );

        MaintenanceChecklistItemResponse response =
                MaintenanceChecklistItemResponse.builder()
                        .id(5L)
                        .tenantId(1L)
                        .ticketId(10L)
                        .itemName("Check pump")
                        .required(true)
                        .completed(true)
                        .completedByActorId(200L)
                        .resultNote(
                                "Pump inspected successfully"
                        )
                        .build();

        when(
                maintenanceChecklistService
                        .completeChecklistItem(
                                eq(10L),
                                eq(5L),
                                any(CompleteChecklistItemRequest.class)
                        )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/checklist/5/complete"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.completed").value(true)
                )
                .andExpect(
                        jsonPath("$.completedByActorId")
                                .value(200)
                );
    }

    @Test
    void shouldReturn404WhenChecklistItemDoesNotExist()
            throws Exception {

        CompleteChecklistItemRequest request =
                new CompleteChecklistItemRequest();

        request.setTenantId(1L);
        request.setCompletedByActorId(200L);

        when(
                maintenanceChecklistService
                        .completeChecklistItem(
                                eq(10L),
                                eq(999L),
                                any(CompleteChecklistItemRequest.class)
                        )
        ).thenThrow(
                new MaintenanceChecklistNotFoundException(
                        999L
                )
        );

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/checklist/999/complete"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper
                                                .writeValueAsString(request)
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status").value(404)
                );
    }

    @Test
    void shouldReturn400ForInvalidChecklistRequest()
            throws Exception {

        String body = """
                {
                  "tenantId": 1,
                  "itemName": ""
                }
                """;

        mockMvc.perform(
                        post(
                                "/api/maintenance/tickets/10/checklist"
                        )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                );
    }
}