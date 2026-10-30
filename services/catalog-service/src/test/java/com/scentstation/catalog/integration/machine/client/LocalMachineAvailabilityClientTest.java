package com.scentstation.catalog.integration.machine.client;

import com.scentstation.catalog.integration.machine.dto.MachineAvailabilityInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LocalMachineAvailabilityClientTest {

    private final LocalMachineAvailabilityClient client =
            new LocalMachineAvailabilityClient();

    @Test
    void getProductAvailability_shouldReturnRequestedReferences() {
        MachineAvailabilityInfo result =
                client.getProductAvailability(
                        1L,
                        3L,
                        10L
                );

        assertEquals(1L, result.getTenantId());
        assertEquals(3L, result.getMachineId());
        assertEquals(10L, result.getProductId());

        assertTrue(result.isAvailable());
        assertEquals(1, result.getAvailableSlotCount());
    }
}