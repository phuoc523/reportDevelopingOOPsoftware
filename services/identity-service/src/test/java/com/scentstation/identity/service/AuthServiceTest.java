package com.scentstation.identity.service;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
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
        authService = new AuthService(users, passwordEncoder, jwtService);
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
    void loginFailsWhenAccountIsLocked() throws Exception {
        User user = new User("alice", "alice@example.com",
                "hashed-password", "Alice", Role.CUSTOMER);

        var lockedField = User.class.getDeclaredField("locked");
        lockedField.setAccessible(true);
        lockedField.set(user, true);

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
}