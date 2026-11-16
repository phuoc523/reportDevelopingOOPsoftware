package com.scentstation.identity.config;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.service.JwtService;
import com.scentstation.identity.tenant.Tenant;
import com.scentstation.identity.tenant.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityAuthorizationTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private JwtService jwtService;
    @MockBean private UserRepository users;
    @MockBean private TenantRepository tenants;

    @BeforeEach
    void setup() {
        token("admin-token", "admin", Role.ADMIN);
        token("staff-token", "staff", Role.STAFF);
        token("customer-token", "customer", Role.CUSTOMER);
        token("platform-token", "platform", Role.PLATFORM_SUPER_ADMIN);
        token("brand-token", "brand", Role.BRAND_ADMIN);
        token("operator-token", "operator", Role.OPERATIONS_MANAGER);
        token("suspended-brand-token", "suspended", Role.BRAND_ADMIN);
        var suspended = new User("suspended", "suspended@example.com", "hash", "suspended", Role.BRAND_ADMIN);
        suspended.setTenantId(19L);
        when(users.findByUsername("suspended")).thenReturn(Optional.of(suspended));
        var tenant = new Tenant("blocked", "Blocked Brand");
        tenant.setStatus(Tenant.Status.SUSPENDED);
        when(tenants.findById(19L)).thenReturn(Optional.of(tenant));
        when(jwtService.extractUsername("invalid-role-token")).thenThrow(new IllegalArgumentException("invalid"));
    }

    void token(String token, String username, Role role) {
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.extractTokenVersion(token)).thenReturn(0L);
        when(users.findByUsername(username)).thenReturn(Optional.of(
            new User(username, username + "@example.com", "hash", username, role)));
    }

    @Test void legacyAdminCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isForbidden());
    }
    @Test void customerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer customer-token"))
            .andExpect(status().isForbidden());
    }
    @Test void legacyStaffCannotAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe").header("Authorization", "Bearer staff-token"))
            .andExpect(status().isForbidden());
    }
    @Test void operationsManagerMayAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe").header("Authorization", "Bearer operator-token"))
            .andExpect(status().isOk());
    }
    @Test void suspendedTenantCannotAccessProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe").header("Authorization", "Bearer suspended-brand-token"))
            .andExpect(status().isUnauthorized());
    }
    @Test void customerCannotAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe").header("Authorization", "Bearer customer-token"))
            .andExpect(status().isForbidden());
    }
    @Test void customerCanAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/probe").header("Authorization", "Bearer customer-token"))
            .andExpect(status().isOk());
    }
    @Test void staffCannotAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/probe").header("Authorization", "Bearer staff-token"))
            .andExpect(status().isForbidden());
    }
    @Test void unauthenticatedUserCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe")).andExpect(status().isUnauthorized());
    }
    @Test void invalidTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer invalid-role-token"))
            .andExpect(status().isUnauthorized());
    }
    @Test void platformCanAccessLegacyAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer platform-token"))
            .andExpect(status().isOk());
    }
    @Test void brandCanAccessBrandAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer brand-token"))
            .andExpect(status().isOk());
    }
}
