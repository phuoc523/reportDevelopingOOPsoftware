package com.scentstation.catalog.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.integration.machine.client.MachineAvailabilityClient;
import com.scentstation.catalog.repository.CampaignRepository;
import com.scentstation.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

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

    // Dọn dữ liệu trước mỗi test để các test không phụ thuộc lẫn nhau.
    @BeforeEach
    void cleanDatabase() {
        campaignRepository.deleteAll();
        productRepository.deleteAll();
    }
}