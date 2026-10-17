package com.scentstation.identity.config;

import com.scentstation.identity.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProbeController.class)
@AutoConfigureMockMvc
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class SecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        when(jwtService.extractUsername("admin-token")).thenReturn("admin");
        when(jwtService.extractRole("admin-token")).thenReturn("ADMIN");

        when(jwtService.extractUsername("staff-token")).thenReturn("staff");
        when(jwtService.extractRole("staff-token")).thenReturn("STAFF");

        when(jwtService.extractUsername("customer-token")).thenReturn("customer");
        when(jwtService.extractRole("customer-token")).thenReturn("CUSTOMER");

        when(jwtService.extractUsername("invalid-role-token")).thenReturn("unknown");
        when(jwtService.extractRole("invalid-role-token")).thenReturn("SUPERUSER");
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/probe").header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
    }

    @Test
    void staffCannotAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/probe").header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRoleIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer invalid-role-token"))
                .andExpect(status().isUnauthorized());
    }
}
