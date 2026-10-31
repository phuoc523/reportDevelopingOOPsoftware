package com.scentstation.catalog.service;

import com.scentstation.catalog.dto.response.ProductMachineAvailabilityResponse;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.exception.MachineAvailabilityReferenceMismatchException;
import com.scentstation.catalog.exception.ProductNotFoundException;
import com.scentstation.catalog.integration.machine.client.MachineAvailabilityClient;
import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.CurrentActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductMachineAvailabilityServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MachineAvailabilityClient machineAvailabilityClient;

    @Mock
    private CurrentActorContext currentActorContext;

    @InjectMocks
    private ProductMachineAvailabilityService availabilityService;

    private Product product;

    @BeforeEach
    void setUp() {
        lenient().when(currentActorContext.requireTenant(1L))
                .thenReturn(1L);

        product = Product.builder()
                .id(10L)
                .tenantId(1L)
                .name("Perfume A")
                .defaultPrice(new BigDecimal("100000.00"))
                .available(true)
                .build();
    }

    @Test
    void getAvailability_shouldReturnTrueWhenCatalogAndMachineAreAvailable() {
        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(machineAvailabilityClient
                .getProductAvailability(1L, 3L, 10L))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(3L)
                                .productId(10L)
                                .available(true)
                                .availableSlotCount(2)
                                .build()
                );

        ProductMachineAvailabilityResponse response =
                availabilityService.getAvailability(
                        1L,
                        3L,
                        10L
                );

        assertTrue(response.getCatalogAvailable());
        assertTrue(response.getMachineAvailable());
        assertTrue(response.getAvailable());
        assertEquals(2, response.getAvailableSlotCount());
    }

    @Test
    void getAvailability_shouldReturnFalseWhenCatalogIsUnavailable() {
        product.setAvailable(false);

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(machineAvailabilityClient
                .getProductAvailability(1L, 3L, 10L))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(3L)
                                .productId(10L)
                                .available(true)
                                .availableSlotCount(2)
                                .build()
                );

        ProductMachineAvailabilityResponse response =
                availabilityService.getAvailability(
                        1L,
                        3L,
                        10L
                );

        assertFalse(response.getAvailable());
    }

    @Test
    void getAvailability_shouldReturnFalseWhenMachineIsUnavailable() {
        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(machineAvailabilityClient
                .getProductAvailability(1L, 3L, 10L))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(3L)
                                .productId(10L)
                                .available(false)
                                .availableSlotCount(0)
                                .build()
                );

        ProductMachineAvailabilityResponse response =
                availabilityService.getAvailability(
                        1L,
                        3L,
                        10L
                );

        assertFalse(response.getAvailable());
        assertEquals(0, response.getAvailableSlotCount());
    }

    @Test
    void getAvailability_shouldRejectProductFromAnotherTenant() {
        product.setTenantId(2L);

        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.empty());

        assertThrows(
                ProductNotFoundException.class,
                () ->
                        availabilityService
                                .getAvailability(1L, 3L, 10L)
        );

        verifyNoInteractions(machineAvailabilityClient);
    }

    @Test
    void getAvailability_shouldRejectMismatchedMachineResponse() {
        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(machineAvailabilityClient
                .getProductAvailability(1L, 3L, 10L))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(999L)
                                .productId(10L)
                                .available(true)
                                .availableSlotCount(1)
                                .build()
                );

        assertThrows(
                MachineAvailabilityReferenceMismatchException.class,
                () ->
                        availabilityService
                                .getAvailability(1L, 3L, 10L)
        );
    }

    @Test
    void getAvailability_shouldRejectNullMachineResponse() {
        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(machineAvailabilityClient
                .getProductAvailability(1L, 3L, 10L))
                .thenReturn(null);

        assertThrows(
                MachineAvailabilityReferenceMismatchException.class,
                () ->
                        availabilityService
                                .getAvailability(1L, 3L, 10L)
        );
    }

    @Test
    void getAvailability_shouldRejectNegativeAvailableSlotCount() {
        when(productRepository.findByIdAndTenantId(10L, 1L))
                .thenReturn(Optional.of(product));

        when(machineAvailabilityClient
                .getProductAvailability(1L, 3L, 10L))
                .thenReturn(
                        MachineAvailabilityInfo.builder()
                                .tenantId(1L)
                                .machineId(3L)
                                .productId(10L)
                                .available(true)
                                .availableSlotCount(-1)
                                .build()
                );

        assertThrows(
                MachineAvailabilityReferenceMismatchException.class,
                () ->
                        availabilityService
                                .getAvailability(1L, 3L, 10L)
        );
    }
}
