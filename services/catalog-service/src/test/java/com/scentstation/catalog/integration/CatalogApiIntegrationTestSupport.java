package com.scentstation.catalog.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.integration.machine.client.MachineAvailabilityClient;
import com.scentstation.catalog.integration.identity.client.IdentityIntrospectionClient;
import com.scentstation.catalog.integration.identity.dto.IdentityPrincipal;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class CatalogApiIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CampaignRepository campaignRepository;

    @MockBean
    protected MachineAvailabilityClient machineAvailabilityClient;

    @MockBean
    protected IdentityIntrospectionClient identityIntrospectionClient;

    // Dọn dữ liệu trước mỗi test để các test không phụ thuộc lẫn nhau.
    @BeforeEach
    void cleanDatabase() {
        when(identityIntrospectionClient.introspect(anyString())).thenAnswer(invocation -> {
            String token = invocation.getArgument(0);
            long tenantId = "tenant-2".equals(token) ? 2L : 1L;
            return new IdentityPrincipal(
                    tenantId,
                    "tenant-user-" + tenantId,
                    tenantId,
                    Set.of("BRAND_ADMIN"),
                    Set.of("CATALOG_READ", "CATALOG_MANAGE", "CAMPAIGN_MANAGE"),
                    Set.of(),
                    Set.of()
            );
        });
        campaignRepository.deleteAll();
        productRepository.deleteAll();
    }
}
