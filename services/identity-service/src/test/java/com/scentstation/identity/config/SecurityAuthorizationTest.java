package com.scentstation.identity.config;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.UserRepository;
import com.scentstation.identity.service.JwtService;
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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        when(jwtService.extractUsername("admin-token")).thenReturn("admin");
        when(jwtService.extractUsername("staff-token")).thenReturn("staff");
        when(jwtService.extractUsername("customer-token")).thenReturn("customer");
        when(jwtService.extractUsername("invalid-role-token")).thenReturn("unknown");

        when(userRepository.findByUsername("admin"))
                .thenReturn(Optional.of(new User(
                        "admin", "admin@test.local", "test-password",
                        "Test Admin", Role.ADMIN)));

        when(userRepository.findByUsername("staff"))
                .thenReturn(Optional.of(new User(
                        "staff", "staff@test.local", "test-password",
                        "Test Staff", Role.STAFF)));

        when(userRepository.findByUsername("customer"))
                .thenReturn(Optional.of(new User(
                        "customer", "customer@test.local", "test-password",
                        "Test Customer", Role.CUSTOMER)));

        when(userRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());
    }

    @Test
    void adminCanAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe")
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessStaffEndpoint() throws Exception {
        mockMvc.perform(get("/api/staff/probe")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCanAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/probe")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isOk());
    }

    @Test
    void staffCannotAccessCustomerEndpoint() throws Exception {
        mockMvc.perform(get("/api/customer/probe")
                        .header("Authorization", "Bearer staff-token"))
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