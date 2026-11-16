package com.scentstation.security;

import com.scentstation.exception.IdentifierDataIntegrityException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MachineIdentifiersTest {
    @Test
    void tenantAndLocationUseExactPositiveDecimalText() {
        assertEquals(1L, MachineIdentifiers.requestTenant("1"));
        assertEquals(99L, MachineIdentifiers.exactNumericScope("99"));
        assertNull(MachineIdentifiers.exactNumericScope("099"));
        assertNull(MachineIdentifiers.exactNumericScope("location-a"));
    }

    @Test
    void malformedRequestAndStoredTenantHaveDifferentFailureClasses() {
        assertThrows(ResponseStatusException.class, () -> MachineIdentifiers.requestTenant("001"));
        assertThrows(ResponseStatusException.class, () -> MachineIdentifiers.requestTenant("-1"));
        assertThrows(ResponseStatusException.class, () -> MachineIdentifiers.requirePositive(0L, "machineId"));
        assertThrows(IdentifierDataIntegrityException.class,
                () -> MachineIdentifiers.storedTenant("TENANT-1"));
    }
}
