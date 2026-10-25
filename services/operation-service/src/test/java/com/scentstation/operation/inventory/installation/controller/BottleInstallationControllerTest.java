package com.scentstation.operation.inventory.installation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.operation.inventory.batch.exception.OperationExceptionHandler;
import com.scentstation.operation.inventory.installation.dto.request.InstallBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.RemoveBottleRequest;
import com.scentstation.operation.inventory.installation.dto.request.ReplaceBottleRequest;
import com.scentstation.operation.inventory.installation.dto.response.BottleInstallationResponse;
import com.scentstation.operation.inventory.installation.exception.ActiveInstallationNotFoundException;
import com.scentstation.operation.inventory.installation.exception.BottleAlreadyInstalledException;
import com.scentstation.operation.inventory.installation.exception.BottleNotInstallableException;
import com.scentstation.operation.inventory.installation.exception.SlotAlreadyOccupiedException;
import com.scentstation.operation.inventory.installation.service.BottleInstallationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BottleInstallationController.class)
@Import(OperationExceptionHandler.class)
class BottleInstallationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BottleInstallationService bottleInstallationService;

    @Test
    void shouldInstallBottle() throws Exception {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        BottleInstallationResponse response =
                BottleInstallationResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .installedBy(5L)
                        .active(true)
                        .build();

        when(bottleInstallationService.installBottle(
                any(InstallBottleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/installations/install")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bottleId").value(10))
                .andExpect(jsonPath("$.machineId").value(3))
                .andExpect(jsonPath("$.slotId").value(2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldRemoveBottle() throws Exception {
        RemoveBottleRequest request =
                RemoveBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .actorId(5L)
                        .reason("Bottle nearly empty")
                        .build();

        BottleInstallationResponse response =
                BottleInstallationResponse.builder()
                        .id(1L)
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .active(false)
                        .removedBy(5L)
                        .build();

        when(bottleInstallationService.removeBottle(
                any(RemoveBottleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/installations/remove")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bottleId").value(10))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.removedBy").value(5));
    }

    @Test
    void shouldReplaceBottle() throws Exception {
        ReplaceBottleRequest request =
                ReplaceBottleRequest.builder()
                        .tenantId(1L)
                        .oldBottleId(10L)
                        .newBottleId(20L)
                        .actorId(5L)
                        .reason("Replace nearly empty bottle")
                        .build();

        BottleInstallationResponse response =
                BottleInstallationResponse.builder()
                        .id(2L)
                        .tenantId(1L)
                        .bottleId(20L)
                        .machineId(3L)
                        .slotId(2L)
                        .installedBy(5L)
                        .active(true)
                        .build();

        when(bottleInstallationService.replaceBottle(
                any(ReplaceBottleRequest.class)
        )).thenReturn(response);

        mockMvc.perform(
                        post("/api/inventory/installations/replace")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bottleId").value(20))
                .andExpect(jsonPath("$.machineId").value(3))
                .andExpect(jsonPath("$.slotId").value(2))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void shouldReturn409WhenBottleAlreadyInstalled() throws Exception {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleInstallationService.installBottle(
                any(InstallBottleRequest.class)
        )).thenThrow(
                new BottleAlreadyInstalledException(10L)
        );

        mockMvc.perform(
                        post("/api/inventory/installations/install")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn409WhenSlotAlreadyOccupied() throws Exception {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleInstallationService.installBottle(
                any(InstallBottleRequest.class)
        )).thenThrow(
                new SlotAlreadyOccupiedException(
                        3L,
                        2L
                )
        );

        mockMvc.perform(
                        post("/api/inventory/installations/install")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shouldReturn400WhenBottleNotInstallable() throws Exception {
        InstallBottleRequest request =
                InstallBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .machineId(3L)
                        .slotId(2L)
                        .actorId(5L)
                        .build();

        when(bottleInstallationService.installBottle(
                any(InstallBottleRequest.class)
        )).thenThrow(
                new BottleNotInstallableException(
                        10L,
                        "DAMAGED"
                )
        );

        mockMvc.perform(
                        post("/api/inventory/installations/install")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shouldReturn404WhenActiveInstallationNotFound() throws Exception {
        RemoveBottleRequest request =
                RemoveBottleRequest.builder()
                        .tenantId(1L)
                        .bottleId(10L)
                        .actorId(5L)
                        .build();

        when(bottleInstallationService.removeBottle(
                any(RemoveBottleRequest.class)
        )).thenThrow(
                new ActiveInstallationNotFoundException(
                        10L,
                        1L
                )
        );

        mockMvc.perform(
                        post("/api/inventory/installations/remove")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shouldReturn400WhenInstallRequestIsInvalid() throws Exception {
        String requestBody = """
                {
                  "tenantId": 1,
                  "bottleId": 10,
                  "machineId": 0,
                  "slotId": 2,
                  "actorId": 5
                }
                """;

        mockMvc.perform(
                        post("/api/inventory/installations/install")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}