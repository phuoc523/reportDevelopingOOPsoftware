package com.scentstation.order.security;

import com.scentstation.order.exception.IdentifierDataIntegrityException;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderIdentifiersTest {
    @Test
    void acceptsCanonicalPositiveNumericText() {
        assertEquals(1L, OrderIdentifiers.request("1", "tenantId"));
        assertEquals("12", OrderIdentifiers.requestText("12", "machineId"));
    }

    @Test
    void rejectsAliasesAndInvalidRequestValues() {
        for (String value : new String[]{"001", "TENANT-1", "", "-1", "0", " 1"}) {
            assertThrows(ResponseStatusException.class,
                    () -> OrderIdentifiers.request(value, "tenantId"));
        }
        assertThrows(ResponseStatusException.class,
                () -> OrderIdentifiers.request(null, "tenantId"));
    }

    @Test
    void malformedStoredTenantIsADataIntegrityFailure() {
        assertThrows(IdentifierDataIntegrityException.class,
                () -> OrderIdentifiers.stored("TENANT-1", "order tenantId"));
        assertThrows(IdentifierDataIntegrityException.class,
                () -> OrderIdentifiers.stored("001", "order tenantId"));
    }
}
