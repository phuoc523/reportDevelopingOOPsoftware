package com.scentstation.catalog.controller;

import com.scentstation.catalog.dto.response.ProductMachineAvailabilityResponse;
import com.scentstation.catalog.service.ProductMachineAvailabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductMachineAvailabilityController.class)
class ProductMachineAvailabilityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductMachineAvailabilityService availabilityService;

    @Test
    void getAvailability_shouldReturnMachineAvailability()
            throws Exception {

        ProductMachineAvailabilityResponse response =
                ProductMachineAvailabilityResponse.builder()
                        .tenantId(1L)
                        .productId(10L)
                        .machineId(3L)
                        .catalogAvailable(true)
                        .machineAvailable(true)
                        .availableSlotCount(2)
                        .available(true)
                        .build();

        when(availabilityService
                .getAvailability(1L, 3L, 10L))
                .thenReturn(response);

        mockMvc.perform(
                        get(
                                "/api/catalog/availability/machines/{machineId}/products/{productId}",
                                3L,
                                10L
                        )
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.tenantId")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.machineId")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.productId")
                                .value(10)
                )
                .andExpect(
                        jsonPath("$.catalogAvailable")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.machineAvailable")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.availableSlotCount")
                                .value(2)
                )
                .andExpect(
                        jsonPath("$.available")
                                .value(true)
                );
    }

    @Test
    void getAvailability_shouldRejectInvalidTenantId()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/catalog/availability/machines/{machineId}/products/{productId}",
                                3L,
                                10L
                        )
                                .param("tenantId", "0")
                )
                .andExpect(status().isBadRequest());
    }
}