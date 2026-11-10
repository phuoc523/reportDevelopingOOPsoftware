package com.scentstation.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.dto.request.CreateProductRequest;
import com.scentstation.catalog.entity.Campaign;
import com.scentstation.catalog.entity.Product;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.integration.machine.client.MachineAvailabilityClient;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import com.scentstation.catalog.security.HeaderCurrentActorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CatalogMySqlIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.41")
            .withDatabaseName("scentstation_catalog")
            .withUsername("catalog")
            .withPassword("catalog");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CampaignRepository campaignRepository;

    @MockBean
    private MachineAvailabilityClient machineAvailabilityClient;

    @BeforeEach
    void cleanDatabase() {
        campaignRepository.deleteAll();
        productRepository.deleteAll();
    }

    @Test
    void flywayBootstrapsAllMappedTablesAndCampaignPersists() {
        Integer mappedTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('products', 'campaigns')
                """, Integer.class);
        assertEquals(2, mappedTableCount);

        Product product = productRepository.saveAndFlush(Product.builder()
                .tenantId(1L)
                .name("Campaign product")
                .defaultPrice(new BigDecimal("100.00"))
                .available(true)
                .build());

        Campaign campaign = campaignRepository.saveAndFlush(Campaign.builder()
                .tenantId(1L)
                .productId(product.getId())
                .name("MySQL campaign")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(1))
                .active(true)
                .build());

        assertEquals(1L, campaignRepository.findByIdAndTenantId(campaign.getId(), 1L)
                .orElseThrow()
                .getTenantId());
    }

    @Test
    void productApiEnforcesTrustedTenantOnRealMySql() throws Exception {
        CreateProductRequest request = CreateProductRequest.builder()
                .tenantId(1L)
                .name("MySQL product")
                .defaultPrice(new BigDecimal("99.99"))
                .available(true)
                .build();

        String response = mockMvc.perform(post("/api/products")
                        .header(HeaderCurrentActorContext.TENANT_HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long productId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(get("/api/products/{id}", productId)
                        .header(HeaderCurrentActorContext.TENANT_HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(productId));

        mockMvc.perform(get("/api/products/{id}", productId)
                        .header(HeaderCurrentActorContext.TENANT_HEADER, "2"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/products")
                        .header(HeaderCurrentActorContext.TENANT_HEADER, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(patch("/api/products/{id}/price", productId)
                        .header(HeaderCurrentActorContext.TENANT_HEADER, "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultPrice\": 1.00}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void databaseRejectsNegativePrice() {
        Product product = Product.builder()
                .tenantId(1L)
                .name("Invalid price")
                .defaultPrice(new BigDecimal("-0.01"))
                .available(true)
                .build();

        assertThrows(JpaSystemException.class,
                () -> productRepository.saveAndFlush(product));
    }
}
