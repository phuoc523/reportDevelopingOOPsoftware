package com.scentstation.identity.config;

import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.security.StaffPrincipal;
import com.scentstation.identity.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository users;
    private final StaffUserRepository staffUsers;
    private final TenantRepository tenants;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserRepository users,
            StaffUserRepository staffUsers,
            TenantRepository tenants) {
        this.jwtService = jwtService;
        this.users = users;
        this.staffUsers = staffUsers;
        this.tenants = tenants;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null
                && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                authenticate(header.substring(7));
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
            }
        }

        chain.doFilter(request, response);
    }

    private void authenticate(String token) {
        String type = jwtService.extractPrincipalType(token);
        if ("STAFF".equals(type)) {
            authenticateStaff(token);
            return;
        }
        if (type != null && !"CUSTOMER".equals(type)) {
            throw new IllegalArgumentException("Invalid JWT principal type");
        }
        authenticateCustomer(token);
    }

    private void authenticateCustomer(String token) {
        String username = jwtService.extractUsername(token);
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Invalid JWT subject");
        }

        User user = users.findByUsername(username)
                .filter(User::isActive)
                .filter(account -> !account.isLocked())
                .orElseThrow(() -> new IllegalArgumentException("Account is unavailable"));

        var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
        var authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, List.of(authority));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void authenticateStaff(String token) {
        String id = jwtService.extractPrincipalId(token);
        String username = jwtService.extractUsername(token);
        if (id == null || id.isBlank() || username == null || username.isBlank()) {
            throw new IllegalArgumentException("Invalid staff JWT");
        }

        StaffUser staffUser = staffUsers.findWithRolesById(id)
                .filter(user -> user.getUsername().equals(username))
                .filter(user -> !user.isUnavailableAt(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalArgumentException("Account is unavailable"));

        if (staffUser.getTenantId() != null
                && !tenants.findById(staffUser.getTenantId())
                        .filter(tenant -> tenant.isActive())
                        .isPresent()) {
            throw new IllegalArgumentException("Tenant is unavailable");
        }

        Set<StaffRole> roles = staffUser.getRoles().stream()
                .map(role -> StaffRole.valueOf(role.getId().getRoleName()))
                .collect(Collectors.toUnmodifiableSet());
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("Staff account has no role");
        }

        var authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        var principal = new StaffPrincipal(
                staffUser.getId(), staffUser.getUsername(), staffUser.getTenantId(), roles);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
