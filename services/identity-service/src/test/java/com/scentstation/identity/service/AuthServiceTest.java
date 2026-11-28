package com.scentstation.identity.service;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.dto.StaffRoleRequest;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.audit.AuditService;
import com.scentstation.identity.rbac.AccessPolicy;
import com.scentstation.identity.rbac.AccountPrincipal;
import com.scentstation.identity.tenant.TenantRepository;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private UserRepository users;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(users, passwordEncoder, jwtService,
                mock(AuditService.class), mock(AccessPolicy.class), mock(TenantRepository.class));
    }

    @Test
    void loginSuccess() {
        User user = new User("alice", "alice@example.com",
                "hashed-password", "Alice", Role.CUSTOMER);

        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed-password")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("test-token");

        AuthResponse response = authService.login(
                new LoginRequest("alice", "password123"));

        assertEquals("test-token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals("alice", response.username());
        assertEquals("CUSTOMER", response.role());
    }

    @Test
    void loginFailsWhenUsernameDoesNotExist() {
        when(users.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("unknown", "password123")));

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void loginFailsWhenPasswordIsWrong() {
        User user = new User("alice", "alice@example.com",
                "hashed-password", "Alice", Role.CUSTOMER);

        when(users.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("alice", "wrong-password")));
    }

    @Test
    void loginFailsWhenAccountIsInactive() throws Exception {
        User user = new User("alice", "alice@example.com",
                "hashed-password", "Alice", Role.CUSTOMER);

        var activeField = User.class.getDeclaredField("active");
        activeField.setAccessible(true);
        activeField.set(user, false);

        when(users.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("alice", "password123")));

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void registerCreatesCustomerAndReturnsToken() {
        RegisterRequest request = new RegisterRequest(
                " alice ", "ALICE@example.com", "password123", " Alice ");

        when(users.existsByUsername("alice")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(jwtService.generateToken(any(User.class))).thenReturn("test-token");

        AuthResponse response = authService.register(request);

        assertEquals("test-token", response.token());
        assertEquals("CUSTOMER", response.role());

        verify(users).save(argThat(user ->
                user.getUsername().equals("alice")
                && user.getEmail().equals("alice@example.com")
                && user.getPassword().equals("hashed-password")
                && user.getRole() == Role.CUSTOMER));
    }

    @Test
    void assignedStaffCannotResubmitAndChangeTenant() {
        var access = mock(AccessPolicy.class);
        var tenants = mock(TenantRepository.class);
        authService = new AuthService(users, passwordEncoder, jwtService,
                mock(AuditService.class), access, tenants);
        User user = new User("operator", "operator@example.com", "hash", "Operator", Role.OPERATIONS_MANAGER);
        user.setTenantId(9L);
        when(access.current()).thenReturn(new AccountPrincipal(7L, "operator", 9L,
                Set.of(Role.OPERATIONS_MANAGER), Set.of(), Set.of(), Set.of()));
        when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));

        var failure = assertThrows(ResponseStatusException.class,
                () -> authService.submitStaffRoleRequest(new StaffRoleRequest(10L, Role.TECHNICIAN)));
        assertEquals(HttpStatus.FORBIDDEN, failure.getStatusCode());
        assertEquals(9L, user.getTenantId());
        verify(users, never()).save(any());
        verifyNoInteractions(tenants, jwtService);
    }
}
