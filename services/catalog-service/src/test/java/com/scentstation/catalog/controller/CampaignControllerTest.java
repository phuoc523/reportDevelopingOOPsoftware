package com.scentstation.catalog.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scentstation.catalog.dto.request.CreateCampaignRequest;
import com.scentstation.catalog.dto.response.CampaignResponse;
import com.scentstation.catalog.enums.DiscountType;
import com.scentstation.catalog.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignController.class)
class CampaignControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CampaignService campaignService;

    @Test
    void createCampaign_shouldReturnCreated() throws Exception {
        CreateCampaignRequest request =
                new CreateCampaignRequest();

        request.setTenantId(1L);
        request.setProductId(10L);
        request.setName("Launch Campaign");
        request.setDiscountType(
                DiscountType.PERCENTAGE
        );
        request.setDiscountValue(
                new BigDecimal("20.00")
        );
        request.setStartAt(
                LocalDateTime.now().minusDays(1)
        );
        request.setEndAt(
                LocalDateTime.now().plusDays(1)
        );
        request.setActive(true);

        CampaignResponse response =
                CampaignResponse.builder()
                        .id(100L)
                        .tenantId(1L)
                        .productId(10L)
                        .name("Launch Campaign")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("20.00")
                        )
                        .active(true)
                        .build();

        when(campaignService.createCampaign(any()))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/campaigns")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(
                        jsonPath("$.tenantId")
                                .value(1)
                )
                .andExpect(
                        jsonPath("$.productId")
                                .value(10)
                );
    }

    @Test
    void getCampaigns_shouldReturnTenantCampaigns()
            throws Exception {

        CampaignResponse response =
                CampaignResponse.builder()
                        .id(100L)
                        .tenantId(1L)
                        .productId(10L)
                        .name("Campaign")
                        .discountType(
                                DiscountType.PERCENTAGE
                        )
                        .discountValue(
                                new BigDecimal("10.00")
                        )
                        .active(true)
                        .build();

        when(campaignService.getCampaigns(1L))
                .thenReturn(List.of(response));

        mockMvc.perform(
                        get("/api/campaigns")
                                .param("tenantId", "1")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100))
                .andExpect(
                        jsonPath("$[0].tenantId")
                                .value(1)
                );
    }

    @Test
    void createCampaign_shouldRejectInvalidRequest()
            throws Exception {

        CreateCampaignRequest request =
                new CreateCampaignRequest();

        request.setTenantId(1L);

        mockMvc.perform(
                        post("/api/campaigns")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper
                                                .writeValueAsString(
                                                        request
                                                )
                                )
                )
                .andExpect(status().isBadRequest());
    }
}