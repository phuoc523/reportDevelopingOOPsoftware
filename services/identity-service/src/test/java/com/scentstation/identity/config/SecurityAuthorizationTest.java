package com.scentstation.identity.config;

import com.scentstation.identity.entity.Role;
import com.scentstation.identity.entity.StaffRole;
import com.scentstation.identity.entity.StaffUser;
import com.scentstation.identity.entity.User;
import com.scentstation.identity.repository.StaffUserRepository;
import com.scentstation.identity.repository.TenantRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockBean
    private StaffUserRepository staffUserRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() {
        when(jwtService.extractUsername("platform-token")).thenReturn("platform");
        when(jwtService.extractUsername("admin-token")).thenReturn("admin");
        when(jwtService.extractUsername("staff-token")).thenReturn("staff");
        when(jwtService.extractUsername("customer-token")).thenReturn("customer");
        when(jwtService.extractUsername("invalid-role-token")).thenReturn("unknown");

        StaffUser platformUser = new StaffUser("platform", "test-password", null);
        platformUser.addRole(StaffRole.PLATFORM_SUPER_ADMIN);
        when(jwtService.extractPrincipalType("platform-token")).thenReturn("STAFF");
        when(jwtService.extractPrincipalId("platform-token")).thenReturn(platformUser.getId());
        when(staffUserRepository.findWithRolesById(platformUser.getId()))
                .thenReturn(Optional.of(platformUser));

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
    void platformSuperAdminCanAccessAccountManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users/probe")
                        .header("Authorization", "Bearer platform-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/probe")
                        .header("Authorization", "Bearer platform-token"))
                .andExpect(status().isOk());
    }

    @Test
    void brandAdminCanAccessStaffAccountManagementEndpoint() throws Exception {
        StaffUser brandAdmin = new StaffUser("brand", "test-password", "tenant-1");
        brandAdmin.addRole(StaffRole.BRAND_ADMIN);
        when(jwtService.extractPrincipalType("brand-token")).thenReturn("STAFF");
        when(jwtService.extractPrincipalId("brand-token")).thenReturn(brandAdmin.getId());
        when(jwtService.extractUsername("brand-token")).thenReturn("brand");
        when(staffUserRepository.findWithRolesById(brandAdmin.getId()))
                .thenReturn(Optional.of(brandAdmin));
        when(tenantRepository.findById("tenant-1"))
                .thenReturn(Optional.of(new com.scentstation.identity.tenant.Tenant("DEMO")));

        mockMvc.perform(get("/api/admin/staff-users/probe")
                        .header("Authorization", "Bearer brand-token"))
                .andExpect(status().isOk());
    }

    @Test
    void regularAdminCannotAccessAccountManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users/probe")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCannotAccessAccountManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users/probe")
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotAccessAccountManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users/probe")
                        .header("Authorization", "Bearer customer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedUserCannotAccessAccountManagementEndpoint() throws Exception {
        mockMvc.perform(get("/api/admin/users/probe"))
                .andExpect(status().isUnauthorized());
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
    void viewerCanReadBusinessDataButCannotWrite() throws Exception {
        registerStaffToken("viewer-token", "viewer", "tenant-1", StaffRole.VIEWER);

        mockMvc.perform(get("/api/staff/operations/probe")
                        .header("Authorization", "Bearer viewer-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/inventory/probe")
                        .header("Authorization", "Bearer viewer-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/inventory/probe")
                        .header("Authorization", "Bearer viewer-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void operationsManagerCanWriteOperationsAndInventoryButNotManageAccounts()
            throws Exception {
        registerStaffToken(
                "operations-token", "operations", "tenant-2",
                StaffRole.OPERATIONS_MANAGER);

        mockMvc.perform(post("/api/staff/operations/probe")
                        .header("Authorization", "Bearer operations-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/inventory/probe")
                        .header("Authorization", "Bearer operations-token"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/staff-users/probe")
                        .header("Authorization", "Bearer operations-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void technicianCanWriteMaintenanceButNotInventory() throws Exception {
        registerStaffToken(
                "technician-token", "technician", "tenant-3",
                StaffRole.TECHNICIAN);

        mockMvc.perform(post("/api/staff/maintenance/probe")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/inventory/probe")
                        .header("Authorization", "Bearer technician-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void inventoryStaffCanWriteInventoryButNotMaintenance() throws Exception {
        registerStaffToken(
                "inventory-token", "inventory", "tenant-4",
                StaffRole.INVENTORY_STAFF);

        mockMvc.perform(post("/api/staff/inventory/probe")
                        .header("Authorization", "Bearer inventory-token"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/staff/maintenance/probe")
                        .header("Authorization", "Bearer inventory-token"))
                .andExpect(status().isForbidden());
    }

    private void registerStaffToken(
            String token, String username, String tenantId, StaffRole role) {
        StaffUser staffUser = new StaffUser(username, "test-password", tenantId);
        staffUser.addRole(role);
        when(jwtService.extractPrincipalType(token)).thenReturn("STAFF");
        when(jwtService.extractPrincipalId(token)).thenReturn(staffUser.getId());
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(staffUserRepository.findWithRolesById(staffUser.getId()))
                .thenReturn(Optional.of(staffUser));
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(new com.scentstation.identity.tenant.Tenant("DEMO")));
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