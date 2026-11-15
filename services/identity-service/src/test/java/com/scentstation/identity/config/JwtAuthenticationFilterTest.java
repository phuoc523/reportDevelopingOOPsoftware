package com.scentstation.identity.config;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.security.StaffPrincipal;
import com.scentstation.identity.service.JwtService;
import com.scentstation.identity.tenant.Tenant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private UserRepository users;
    private StaffUserRepository staffUsers;
    private TenantRepository tenants;
    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        staffUsers = mock(StaffUserRepository.class);
        tenants = mock(TenantRepository.class);
        jwtService = new JwtService(
                Base64.getEncoder().encodeToString(new byte[32]), 60_000);
        filter = new JwtAuthenticationFilter(jwtService, users, staffUsers, tenants);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void principalTypePreventsCustomerAndStaffUsernameCollision() throws Exception {
        String username = "same.username";
        User customer = new User(
                username, "customer@example.com", "customer-hash",
                "Customer", Role.CUSTOMER);
        StaffUser staff = new StaffUser(username, "staff-hash", "tenant-id");
        staff.addRole(StaffRole.VIEWER);
        when(users.findByUsername(username)).thenReturn(Optional.of(customer));
        when(staffUsers.findWithRolesById(staff.getId())).thenReturn(Optional.of(staff));
        when(tenants.findById("tenant-id"))
                .thenReturn(Optional.of(new Tenant("DEMO")));

        Authentication customerAuthentication = authenticate(jwtService.generateToken(customer));
        assertEquals(username, customerAuthentication.getPrincipal());
        assertTrue(customerAuthentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_CUSTOMER")));

        SecurityContextHolder.clearContext();
        String staffToken = jwtService.generateStaffToken(
                staff.getId(), username, "tenant-id", List.of(StaffRole.VIEWER.name()));
        Authentication staffAuthentication = authenticate(staffToken);
        assertInstanceOf(StaffPrincipal.class, staffAuthentication.getPrincipal());
        assertEquals(username, ((StaffPrincipal) staffAuthentication.getPrincipal()).username());
        assertTrue(staffAuthentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_VIEWER")));
    }

    @Test
    void invalidSignatureIsNotAuthenticated() throws Exception {
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        JwtService otherSigner = new JwtService(
                Base64.getEncoder().encodeToString(otherKey), 60_000);
        String token = otherSigner.generateStaffToken(
                "staff-id", "demo.d", "tenant-id", List.of(StaffRole.VIEWER.name()));

        assertNull(authenticate(token));
        verifyNoInteractions(users, staffUsers, tenants);
    }

    private Authentication authenticate(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
