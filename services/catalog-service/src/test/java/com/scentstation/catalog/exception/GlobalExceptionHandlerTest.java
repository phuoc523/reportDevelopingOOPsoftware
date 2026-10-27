package com.scentstation.catalog.exception;

import com.scentstation.catalog.controller.CampaignController;
import com.scentstation.catalog.service.CampaignService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CampaignController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CampaignService campaignService;

    @Test
    void campaignNotFound_shouldReturn404()
            throws Exception {

        when(campaignService
                .getCampaignById(1L, 999L))
                .thenThrow(
                        new CampaignNotFoundException(
                                999L,
                                1L
                        )
                );

        mockMvc.perform(
                        get("/api/campaigns/{campaignId}", 999L)
                                .param("tenantId", "1")
                )
                .andExpect(status().isNotFound())
                .andExpect(
                        jsonPath("$.status")
                                .value(404)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Not Found")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/campaigns/999")
                );
    }
}