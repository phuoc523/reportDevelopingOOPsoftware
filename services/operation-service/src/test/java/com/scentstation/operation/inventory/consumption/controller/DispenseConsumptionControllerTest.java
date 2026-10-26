package com.scentstation.operation.inventory.consumption.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.inventory.batch.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.bottle.enums.BottleStatus;
import com.scentstation.operation.inventory.consumption.dto.request.RecordDispenseRequest;
import com.scentstation.operation.inventory.consumption.dto.response.DispenseConsumptionResponse;
import com.scentstation.operation.inventory.consumption.enums.DispenseResult;
import com.scentstation.operation.inventory.consumption.enums.DispenseType;
import com.scentstation.operation.inventory.consumption.exception.DispenseConsumptionNotFoundException;
import com.scentstation.operation.inventory.consumption.exception.DuplicateDispenseCommandException;
import com.scentstation.operation.inventory.consumption.exception.InsufficientBottleQuantityException;
import com.scentstation.operation.inventory.consumption.service.DispenseConsumptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DispenseConsumptionController.class)
@Import(OperationExceptionHandler.class)
class DispenseConsumptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DispenseConsumptionService dispenseConsumptionService;

    @Test
    void shouldRecordSuccessfulDispense() throws Exception {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        DispenseConsumptionResponse response =
                DispenseConsumptionResponse.builder()
                        .id(1L)
                        .commandId("CMD-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .quantityBefore(new BigDecimal("20.000"))
                        .quantityAfter(new BigDecimal("19.800"))
                        .inventoryDeducted(true)
                        .bottleStatus(BottleStatus.INSTALLED)
                        .build();

        when(dispenseConsumptionService.recordDispense(
                any(RecordDispenseRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/consumptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId").value("CMD-001"))
                .andExpect(jsonPath("$.inventoryDeducted").value(true))
                .andExpect(jsonPath("$.quantityAfter").value(19.800));
    }

    @Test
    void shouldReturn409WhenCommandIsDuplicate() throws Exception {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("0.200"))
                        .build();

        when(dispenseConsumptionService.recordDispense(
                any(RecordDispenseRequest.class)
        )).thenThrow(
                new DuplicateDispenseCommandException(
                        1L,
                        "CMD-001"
                )
        );

        mockMvc.perform(
                        post("/api/inventory/consumptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn409WhenQuantityIsInsufficient() throws Exception {
        RecordDispenseRequest request =
                RecordDispenseRequest.builder()
                        .tenantId(1L)
                        .commandId("CMD-002")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .consumedQuantity(new BigDecimal("5.000"))
                        .build();

        when(dispenseConsumptionService.recordDispense(
                any(RecordDispenseRequest.class)
        )).thenThrow(
                new InsufficientBottleQuantityException(
                        10L,
                        new BigDecimal("1.000"),
                        new BigDecimal("5.000")
                )
        );

        mockMvc.perform(
                        post("/api/inventory/consumptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldGetConsumptionByCommandId() throws Exception {
        DispenseConsumptionResponse response =
                DispenseConsumptionResponse.builder()
                        .id(1L)
                        .commandId("CMD-001")
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .dispenseType(DispenseType.CUSTOMER)
                        .result(DispenseResult.SUCCESS)
                        .inventoryDeducted(true)
                        .build();

        when(dispenseConsumptionService.getByCommandId(
                1L,
                "CMD-001"
        )).thenReturn(response);

        mockMvc.perform(
                        get("/api/inventory/consumptions/command/CMD-001")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commandId").value("CMD-001"))
                .andExpect(jsonPath("$.bottleId").value(10));
    }

    @Test
    void shouldReturn404WhenCommandDoesNotExist() throws Exception {
        when(dispenseConsumptionService.getByCommandId(
                1L,
                "CMD-999"
        )).thenThrow(
                new DispenseConsumptionNotFoundException(
                        1L,
                        "CMD-999"
                )
        );

        mockMvc.perform(
                        get("/api/inventory/consumptions/command/CMD-999")
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn400WhenRequestIsInvalid() throws Exception {
        String requestBody = """
                {
                  "tenantId": 1,
                  "commandId": "",
                  "bottleId": 10,
                  "machineId": 3,
                  "slotId": 2,
                  "dispenseType": "CUSTOMER",
                  "result": "SUCCESS",
                  "consumedQuantity": 0
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/consumptions")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}