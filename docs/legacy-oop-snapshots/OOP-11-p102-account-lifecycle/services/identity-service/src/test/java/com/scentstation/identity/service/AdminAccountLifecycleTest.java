package com.scentstation.identity.service;

import com.scentstation.identity.dto.AdminUserRequests.CreateUserRequest;
import com.scentstation.identity.dto.AdminUserRequests.UpdateUserRequest;
import com.scentstation.identity.dto.AdminUserResponse;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminAccountLifecycleTest {

    private UserRepository users;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        authService = new AuthService(users, passwordEncoder, jwtService);
    }

    private User sampleUser() {
        return new User(
                "alice",
                "alice@example.com",
                "old-hash",
                "Alice",
                Role.CUSTOMER
        );
    }

    @Test
    void adminCreateUserNormalizesDataAndEncodesPassword() {
        CreateUserRequest request = new CreateUserRequest(
                " bob ",
                "BOB@example.com ",
                "password123",
                " Bob ",
                Role.STAFF
        );

        when(users.existsByUsername("bob")).thenReturn(false);
        when(users.existsByEmail("bob@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("new-hash");
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return user;
        });

        AdminUserResponse response = authService.adminCreateUser(request);

        assertEquals("bob", response.username());
        assertEquals("bob@example.com", response.email());
        assertEquals("Bob", response.fullName());
        assertEquals(Role.STAFF, response.role());

        verify(users).save(argThat(user ->
                user.getUsername().equals("bob")
                && user.getEmail().equals("bob@example.com")
                && user.getPassword().equals("new-hash")
                && user.getRole() == Role.STAFF));
    }

    @Test
    void adminCreateUserRejectsDuplicateUsername() {
        CreateUserRequest request = new CreateUserRequest(
                "alice",
                "new@example.com",
                "password123",
                "Alice",
                Role.CUSTOMER
        );

        when(users.existsByUsername("alice")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.adminCreateUser(request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void adminCreateUserRejectsDuplicateEmail() {
        CreateUserRequest request = new CreateUserRequest(
                "bob",
                "alice@example.com",
                "password123",
                "Bob",
                Role.CUSTOMER
        );

        when(users.existsByUsername("bob")).thenReturn(false);
        when(users.existsByEmail("alice@example.com")).thenReturn(true);

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.adminCreateUser(request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void adminFindAllMapsUsersToSafeResponses() {
        when(users.findAll()).thenReturn(List.of(sampleUser()));

        List<AdminUserResponse> result = authService.adminFindAll();

        assertEquals(1, result.size());
        assertEquals("alice", result.get(0).username());
        assertEquals("alice@example.com", result.get(0).email());
        assertEquals(Role.CUSTOMER, result.get(0).role());
    }

    @Test
    void adminFindByIdReturnsNotFoundForMissingUser() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.adminFindById(99L)
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void adminUpdateUserNormalizesEmailAndUpdatesFields() {
        User user = sampleUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.existsByEmailAndIdNot("new@example.com", 1L))
                .thenReturn(false);
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        UpdateUserRequest request = new UpdateUserRequest(
                " NEW@example.com ",
                " Alice Updated ",
                Role.STAFF
        );

        AdminUserResponse response = authService.adminUpdateUser(1L, request);

        assertEquals("new@example.com", response.email());
        assertEquals("Alice Updated", response.fullName());
        assertEquals(Role.STAFF, response.role());
        assertEquals("alice", response.username());
    }

    @Test
    void adminUpdateUserRejectsEmailUsedByAnotherUser() {
        User user = sampleUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.existsByEmailAndIdNot("other@example.com", 1L))
                .thenReturn(true);

        UpdateUserRequest request = new UpdateUserRequest(
                "other@example.com",
                "Alice",
                Role.CUSTOMER
        );

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.adminUpdateUser(1L, request)
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void adminCanDeactivateAndLockUser() {
        User user = sampleUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        AdminUserResponse inactive =
                authService.adminSetActive(1L, false);
        AdminUserResponse locked =
                authService.adminSetLocked(1L, true);

        assertFalse(inactive.active());
        assertTrue(locked.locked());
        verify(users, times(2)).save(user);
    }

    @Test
    void adminCanActivateAndUnlockUser() {
        User user = sampleUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        AdminUserResponse active =
                authService.adminSetActive(1L, true);
        AdminUserResponse unlocked =
                authService.adminSetLocked(1L, false);

        assertTrue(active.active());
        assertFalse(unlocked.locked());
        verify(users, times(2)).save(user);
    }

    @Test
    void adminResetPasswordEncodesAndSavesNewPassword() {
        User user = sampleUser();
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPassword123"))
                .thenReturn("new-hash");
        when(users.save(any(User.class))).thenAnswer(invocation ->
                invocation.getArgument(0));

        authService.adminResetPassword(1L, "newPassword123");

        assertEquals("new-hash", user.getPassword());
        verify(passwordEncoder).encode("newPassword123");
        verify(users).save(user);
    }

    @Test
    void adminResetPasswordReturnsNotFoundForMissingUser() {
        when(users.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> authService.adminResetPassword(99L, "newPassword123")
        );

        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verify(users, never()).save(any(User.class));
    }
}