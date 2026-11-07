package com.scentstation.catalog.integration;

import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import com.scentstation.catalog.security.HeaderCurrentActorContext;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MachineAvailabilityApiIntegrationTest
        extends CatalogApiIntegrationTestSupport {

    @Test
    void shouldCombineCatalogAndMachineAvailability()
            throws Exception {

        Product product =
                productRepository.save(
                        Product.builder()
                                .tenantId(1L)
                                .name("Rose Garden")
                                .defaultPrice(
                                        new BigDecimal("100000.00")
                                )
                                .available(true)
                                .build()
                );

        when(machineAvailabilityClient
                .getProductAvailability(
                        1L,
                        3L,
                        product.getId()
                ))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(3L)
                                .productId(
                                        product.getId()
                                )
                                .available(true)
                                .availableSlotCount(2)
                                .build()
                );

        mockMvc.perform(
                        get(
                                "/api/catalog/availability/machines/{machineId}/products/{productId}",
                                3L,
                                product.getId()
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.catalogAvailable")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.machineAvailable")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.available")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.availableSlotCount")
                                .value(2)
                );
    }

    @Test
    void shouldReturnUnavailableWhenCatalogProductIsDisabled()
            throws Exception {

        Product product =
                productRepository.save(
                        Product.builder()
                                .tenantId(1L)
                                .name("Disabled Product")
                                .defaultPrice(
                                        new BigDecimal("100000.00")
                                )
                                .available(false)
                                .build()
                );

        when(machineAvailabilityClient
                .getProductAvailability(
                        1L,
                        3L,
                        product.getId()
                ))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(3L)
                                .productId(
                                        product.getId()
                                )
                                .available(true)
                                .availableSlotCount(1)
                                .build()
                );

        mockMvc.perform(
                        get(
                                "/api/catalog/availability/machines/{machineId}/products/{productId}",
                                3L,
                                product.getId()
                        )
                                .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.catalogAvailable")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.machineAvailable")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.available")
                                .value(false)
                );
    }
}
