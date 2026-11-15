package com.scentstation.identity.service;

import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.StaffAuthResponse;
import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StaffAuthServiceTest {

    private StaffUserRepository staffUsers;
    private TenantRepository tenants;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private StaffAuthService authService;
    private final Instant instant = Instant.parse("2026-11-15T04:00:00Z");

    @BeforeEach
    void setUp() {
        staffUsers = mock(StaffUserRepository.class);
        tenants = mock(TenantRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authService = serviceAt(instant);
    }

    @Test
    void allSixDemoRolesCanAuthenticateWithoutCrossingCustomerIdentity() {
        Map<String, StaffRole> accounts = Map.of(
                "platform.admin", StaffRole.PLATFORM_SUPER_ADMIN,
                "demo.admin", StaffRole.BRAND_ADMIN,
                "demo.a", StaffRole.OPERATIONS_MANAGER,
                "demo.b", StaffRole.TECHNICIAN,
                "demo.c", StaffRole.INVENTORY_STAFF,
                "demo.d", StaffRole.VIEWER);

        for (var account : accounts.entrySet()) {
            String tenantId = account.getValue() == StaffRole.PLATFORM_SUPER_ADMIN
                    ? null : "tenant-demo";
            StaffUser staffUser = new StaffUser(
                    account.getKey(), "bcrypt", tenantId);
            staffUser.addRole(account.getValue());
            when(staffUsers.findByUsernameForUpdate(account.getKey()))
                    .thenReturn(Optional.of(staffUser));
            when(passwordEncoder.matches("demo-password", "bcrypt")).thenReturn(true);
            when(jwtService.generateStaffToken(
                    staffUser.getId(), account.getKey(), tenantId,
                    List.of(account.getValue().name())))
                    .thenReturn("token-" + account.getKey());
            when(tenants.findById("tenant-demo"))
                    .thenReturn(Optional.of(new com.scentstation.identity.tenant.Tenant("DEMO")));

            StaffAuthResponse response = authService.login(
                    new LoginRequest(account.getKey(), "demo-password"));

            assertEquals("token-" + account.getKey(), response.token());
            assertEquals(account.getKey(), response.username());
            assertEquals(List.of(account.getValue().name()), response.roles());
            assertEquals(tenantId, response.tenantId());
            assertEquals(staffUser.getId(), response.staffUserId());
            assertEquals(0, staffUser.getFailedAttempts());
        }

        verify(staffUsers, times(6)).save(any(StaffUser.class));
    }

    @Test
    void invalidPasswordIncrementsAttemptsAndUsesGenericMessage() {
        StaffUser staffUser = staffUser("demo.a", StaffRole.OPERATIONS_MANAGER, "tenant-demo");
        when(staffUsers.findByUsernameForUpdate("demo.a"))
                .thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("wrong", "bcrypt")).thenReturn(false);
        when(tenants.findById("tenant-demo"))
                .thenReturn(Optional.of(new com.scentstation.identity.tenant.Tenant("DEMO")));

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo.a", "wrong")));

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
        assertEquals("Invalid username or password", failure.getReason());
        assertEquals(1, staffUser.getFailedAttempts());
        assertNotNull(staffUser.getLastFailedLoginAt());
        verify(staffUsers).save(staffUser);
    }

    @Test
    void unknownUsernameUsesSameErrorAsWrongPassword() {
        when(staffUsers.findByUsernameForUpdate("missing"))
                .thenReturn(Optional.empty());

        ResponseStatusException failure = assertThrows(
                ResponseStatusException.class,
                () -> authService.login(new LoginRequest("missing", "wrong")));

        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
        assertEquals("Invalid username or password", failure.getReason());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void locksAfterFiveFailuresAndAllowsLoginAfterFifteenMinutes() {
        StaffUser staffUser = staffUser("demo.a", StaffRole.OPERATIONS_MANAGER, "tenant-demo");
        when(staffUsers.findByUsernameForUpdate("demo.a"))
                .thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("wrong", "bcrypt")).thenReturn(false);
        when(passwordEncoder.matches("correct", "bcrypt")).thenReturn(true);
        when(tenants.findById("tenant-demo"))
                .thenReturn(Optional.of(new com.scentstation.identity.tenant.Tenant("DEMO")));

        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(ResponseStatusException.class,
                    () -> authService.login(new LoginRequest("demo.a", "wrong")));
        }
        assertEquals(5, staffUser.getFailedAttempts());
        assertEquals(
                instant.plus(Duration.ofMinutes(15)),
                staffUser.getLockedUntil().toInstant(ZoneOffset.UTC));
        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo.a", "correct")));
        verify(passwordEncoder, times(5)).matches("wrong", "bcrypt");
        verify(passwordEncoder, never()).matches("correct", "bcrypt");

        StaffAuthService afterLockout = serviceAt(instant.plus(Duration.ofMinutes(16)));
        when(jwtService.generateStaffToken(
                staffUser.getId(), "demo.a", "tenant-demo",
                List.of(StaffRole.OPERATIONS_MANAGER.name())))
                .thenReturn("unlocked-token");
        StaffAuthResponse response = afterLockout.login(
                new LoginRequest("demo.a", "correct"));

        assertEquals("unlocked-token", response.token());
        assertEquals(0, staffUser.getFailedAttempts());
        assertNull(staffUser.getLockedUntil());
    }

    @Test
    void manuallyLockedOrDisabledAccountCannotAuthenticate() {
        StaffUser manuallyLocked = staffUser(
                "demo.a", StaffRole.OPERATIONS_MANAGER, "tenant-demo");
        manuallyLocked.setManuallyLocked(true);
        when(staffUsers.findByUsernameForUpdate("demo.a"))
                .thenReturn(Optional.of(manuallyLocked));

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo.a", "correct")));
        verify(passwordEncoder, never()).matches(any(), any());

        StaffUser disabled = staffUser(
                "demo.b", StaffRole.TECHNICIAN, "tenant-demo");
        disabled.setEnabled(false);
        when(staffUsers.findByUsernameForUpdate("demo.b"))
                .thenReturn(Optional.of(disabled));

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo.b", "correct")));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void staffWithMissingOrInactiveTenantCannotAuthenticate() {
        StaffUser staffUser = staffUser("demo.a", StaffRole.OPERATIONS_MANAGER, "tenant-demo");
        when(staffUsers.findByUsernameForUpdate("demo.a"))
                .thenReturn(Optional.of(staffUser));
        when(tenants.findById("tenant-demo")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("demo.a", "correct")));
        verify(passwordEncoder, never()).matches(any(), any());
    }

    private StaffUser staffUser(String username, StaffRole role, String tenantId) {
        StaffUser staffUser = new StaffUser(username, "bcrypt", tenantId);
        staffUser.addRole(role);
        return staffUser;
    }

    private StaffAuthService serviceAt(Instant time) {
        return new StaffAuthService(
                staffUsers, tenants, passwordEncoder, jwtService,
                Clock.fixed(time, ZoneOffset.UTC), 5, Duration.ofMinutes(15));
    }
}
