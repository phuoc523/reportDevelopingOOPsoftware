package com.scentstation.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.identity.audit.AuditEventRepository;
import com.scentstation.identity.model.StaffUser;
import com.scentstation.identity.model.Tenant;
import com.scentstation.identity.notifications.NotificationEventRepository;
import com.scentstation.identity.notifications.NotificationDeliveryRepository;
import com.scentstation.identity.platform.PlatformSettingRepository;
import com.scentstation.identity.rbac.RoleAssignmentAuditRepository;
import com.scentstation.identity.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.Set;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "AUDIT_SERVICE_KEY=demo-audit-key-32-characters-long-for-test",
    "NOTIFICATION_SERVICE_KEY=demo-notification-key-32-characters-long-for-test"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformModulesIntegrationTest {
    private static final String PASSWORD="correct-horse-123";
    private static final String AUDIT_KEY="demo-audit-key-32-characters-long-for-test";
    private static final String NOTIF_KEY="demo-notification-key-32-characters-long-for-test";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired StaffUserRepository users;
    @Autowired TenantRepository tenants;
    @Autowired AuthSessionRepository sessions;
    @Autowired LoginAttemptRepository attempts;
    @Autowired RoleAssignmentAuditRepository roleAudits;
    @Autowired AuditEventRepository audits;
    @Autowired NotificationDeliveryRepository deliveries;
    @Autowired NotificationEventRepository events;
    @Autowired PlatformSettingRepository settings;
    @Autowired PasswordEncoder encoder;
    private Tenant a,b;

    @BeforeEach void setup(){
        deliveries.deleteAll();events.deleteAll();settings.deleteAll();audits.deleteAll();
        sessions.deleteAll();roleAudits.deleteAll();attempts.deleteAll();users.deleteAll();tenants.deleteAll();
        a=tenants.save(new Tenant("PLATFORM_A"));b=tenants.save(new Tenant("PLATFORM_B"));
        user("padmin",null,"PLATFORM_SUPER_ADMIN");
        user("aadmin",a,"BRAND_ADMIN");user("badmin",b,"BRAND_ADMIN");
        user("aviewer",a,"VIEWER");user("bviewer",b,"VIEWER");
    }
    private StaffUser user(String name,Tenant tenant,String role){
        return users.save(new StaffUser(name,encoder.encode(PASSWORD),tenant,Set.of(role)));
    }
    private String bearer(String name)throws Exception{
        String response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("username",name,"password",PASSWORD))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer "+mapper.readTree(response).get("accessToken").asText();
    }
    @Test void tenantConfigCannotCrossOrOverrideGlobal()throws Exception{
        String admin=bearer("aadmin");
        String url="/api/v1/platform-config/tenants/"+a.getId()+"/DEVICE_IDLE_TIMEOUT_SECONDS";
        mvc.perform(get(url).header("Authorization",admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("300"));
        mvc.perform(put(url).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"value\":\"120\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("120"));
        mvc.perform(get(url).header("Authorization",admin))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("TENANT_OVERRIDE"));
        mvc.perform(get("/api/v1/platform-config/tenants/"+b.getId()+"/DEVICE_IDLE_TIMEOUT_SECONDS")
            .header("Authorization",admin)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/platform-config/global/DEVICE_IDLE_TIMEOUT_SECONDS")
            .header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"value\":\"90\"}")).andExpect(status().isForbidden());
        mvc.perform(put(url).header("Authorization",admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"value\":\"99999\"}")).andExpect(status().isBadRequest());
        mvc.perform(get(url).header("Authorization",bearer("aviewer")))
            .andExpect(status().isForbidden());
    }
    @Test void platformConfigOverridesDefaultForOtherTenant()throws Exception{
        String platform=bearer("padmin");
        mvc.perform(put("/api/v1/platform-config/global/NOTIFICATION_ENABLED")
            .header("Authorization",platform).contentType(MediaType.APPLICATION_JSON)
            .content("{\"value\":\"false\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/platform-config/tenants/"+b.getId()+"/NOTIFICATION_ENABLED")
            .header("Authorization",bearer("badmin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.value").value("false"))
            .andExpect(jsonPath("$.source").value("GLOBAL_OVERRIDE"));
    }
    @Test void internalAuditKeyAndTenantReadAreEnforced()throws Exception{
        String body=mapper.writeValueAsString(Map.of("tenantId",a.getId(),"actorId","svc-order",
            "action","ORDER_REFUND","resourceType","ORDER","resourceId","sample-order-id",
            "outcome","SUCCESS","requestId","req-test-1"));
        mvc.perform(post("/api/v1/internal/audit/events").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/audit/events").header("X-Internal-Service-Key",AUDIT_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit/events").header("Authorization",bearer("aadmin")))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].tenantId").value(a.getId()));
        mvc.perform(get("/api/v1/audit/events").param("tenantId",a.getId())
            .header("Authorization",bearer("badmin"))).andExpect(status().isForbidden());
    }
    @Test void notificationIsInAppDedupeAndTenantScoped()throws Exception{
        String body=mapper.writeValueAsString(Map.of("tenantId",a.getId(),"eventId","event-001",
            "eventType","MACHINE_ALERT","title","Demo notice","message","Example message",
            "recipientRoles",Set.of("VIEWER")));
        mvc.perform(post("/api/v1/internal/notifications/events").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/internal/notifications/events").header("X-Internal-Service-Key",NOTIF_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.delivered").value(1));
        mvc.perform(post("/api/v1/internal/notifications/events").header("X-Internal-Service-Key",NOTIF_KEY)
            .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
        mvc.perform(get("/api/v1/notifications/me").header("Authorization",bearer("aviewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/notifications/me").header("Authorization",bearer("bviewer")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/notifications/me")).andExpect(status().isUnauthorized());
    }
}
