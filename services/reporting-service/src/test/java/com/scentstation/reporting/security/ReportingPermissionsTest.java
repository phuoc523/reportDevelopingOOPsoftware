package com.scentstation.reporting.security;

import com.scentstation.reporting.dto.integration.IdentityActor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class ReportingPermissionsTest {
    @ParameterizedTest
    @CsvSource({"BRAND_ADMIN,true,true,false", "OPERATIONS_MANAGER,true,false,false", "INVENTORY_STAFF,true,false,false",
            "VIEWER,true,false,false", "TECHNICIAN,false,false,false", "CUSTOMER,false,false,false",
            "ADMIN,false,false,false", "STAFF,false,false,false", "PLATFORM_SUPER_ADMIN,true,true,true"})
    void roleProjectionMatchesIdentitySource(String role, boolean read, boolean export, boolean platform) {
        var actor = new IdentityActor(1L, "user", 12L, Set.of(role), Set.of(), Set.of(), Set.of());
        assertEquals(read, ReportingPermissions.has(actor, ReportingPermissions.READ));
        assertEquals(export, ReportingPermissions.has(actor, ReportingPermissions.EXPORT));
        assertEquals(platform, ReportingPermissions.has(actor, ReportingPermissions.PLATFORM));
    }
    @Test
    void explicitGrantCannotInventPlatformPermission() {
        var actor = new IdentityActor(1L, "user", 12L, Set.of("TECHNICIAN"),
                Set.of("REPORT_READ", "REPORT_EXPORT", "PLATFORM_REPORT_READ"), Set.of(), Set.of());
        assertTrue(ReportingPermissions.has(actor, ReportingPermissions.READ));
        assertTrue(ReportingPermissions.has(actor, ReportingPermissions.EXPORT));
        assertFalse(ReportingPermissions.has(actor, ReportingPermissions.PLATFORM));
    }
}
