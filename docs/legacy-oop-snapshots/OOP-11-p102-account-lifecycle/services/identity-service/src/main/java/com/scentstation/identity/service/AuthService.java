package com.scentstation.identity.service;

import com.scentstation.identity.dto.AuthResponse;
import com.scentstation.identity.dto.AdminUserResponse;
import com.scentstation.identity.dto.LoginRequest;
import com.scentstation.identity.dto.RegisterRequest;
import com.scentstation.identity.dto.AdminUserRequests.CreateUserRequest;
import com.scentstation.identity.dto.AdminUserRequests.UpdateUserRequest;
import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username already exists");
        }
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already exists");
        }

        User user = new User(username, email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(), Role.CUSTOMER);

        users.save(user);
        return response(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"));

        if (!user.isActive() || user.isLocked()
                || !passwordEncoder.matches(
                        request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid username or password");
        }
        return response(user);
    }

    public List<AdminUserResponse> adminFindAll() {
        return users.findAll().stream()
                .map(AdminUserResponse::from)
                .toList();
    }

    public AdminUserResponse adminFindById(Long id) {
        return AdminUserResponse.from(findUser(id));
    }

    public AdminUserResponse adminCreateUser(CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase();

        if (users.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Username already exists");
        }
        if (users.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already exists");
        }

        User user = new User(username, email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(), request.role());

        return AdminUserResponse.from(users.save(user));
    }

    public AdminUserResponse adminUpdateUser(Long id, UpdateUserRequest request) {
        User user = findUser(id);
        String email = request.email().trim().toLowerCase();

        if (users.existsByEmailAndIdNot(email, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Email already exists");
        }

        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setRole(request.role());
        return AdminUserResponse.from(users.save(user));
    }

    public AdminUserResponse adminSetActive(Long id, boolean active) {
        User user = findUser(id);
        user.setActive(active);
        return AdminUserResponse.from(users.save(user));
    }

    public AdminUserResponse adminSetLocked(Long id, boolean locked) {
        User user = findUser(id);
        user.setLocked(locked);
        return AdminUserResponse.from(users.save(user));
    }

    public void adminResetPassword(Long id, String newPassword) {
        User user = findUser(id);
        user.setPassword(passwordEncoder.encode(newPassword));
        users.save(user);
    }
    private User findUser(Long id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
    }

    private AuthResponse response(User user) {
        return new AuthResponse(jwtService.generateToken(user), "Bearer",
                user.getId(), user.getUsername(), user.getRole().name());
    }
}
