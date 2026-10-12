package com.coldchainsentinel.controller;

import com.coldchainsentinel.ColdChainSentinelApplication;
import com.coldchainsentinel.dto.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {SpaForwardController.class, HealthController.class})
@ContextConfiguration(classes = {ColdChainSentinelApplication.class, SpaForwardController.class, HealthController.class})
class SpaForwardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Root path GET / forwards to index.html for React SPA rendering")
    void rootPath_ForwardsToIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Direct client-side routes forward to /index.html without 404")
    void spaRoutes_ForwardToIndexHtml() throws Exception {
        mockMvc.perform(get("/facilities"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/sensors"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/alerts"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/risk-assessment"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/facilities/1/details"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    @DisplayName("Existing /api/** endpoints are not intercepted by SPA forwarder")
    void apiEndpoints_NotInterceptedBySpaForwarder() throws Exception {
        mockMvc.perform(get("/api/health")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.application").value("ColdChain Sentinel"));
    }
}
