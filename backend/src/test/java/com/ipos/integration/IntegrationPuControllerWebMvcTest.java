package com.ipos.integration;

import com.ipos.config.IntegrationPuConfig;
import com.ipos.controller.IntegrationPuController;
import com.ipos.entity.CommercialApplication;
import com.ipos.entity.CommercialApplicationStatus;
import com.ipos.security.SecurityConfig;
import com.ipos.service.CommercialApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IntegrationPuController.class)
@Import({SecurityConfig.class, IntegrationPuConfig.class})
@ActiveProfiles("test")
@DisplayName("IntegrationPuController — WebMvc slice")
@SuppressWarnings("null")
class IntegrationPuControllerWebMvcTest {

    private static final String INTEGRATION_KEY_HEADER = "X-IPOS-Integration-Key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommercialApplicationService commercialApplicationService;

    @Test
    @DisplayName("POST /api/integration-pu/inbound/applications without API key → 401")
    void inbound_missingKey_returns401() throws Exception {
        mockMvc.perform(post("/api/integration-pu/inbound/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalReferenceId\":\"X\",\"payload\":{\"a\":1}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/integration-pu/inbound/applications with wrong API key → 401")
    void inbound_wrongKey_returns401() throws Exception {
        mockMvc.perform(post("/api/integration-pu/inbound/applications")
                        .header(INTEGRATION_KEY_HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalReferenceId\":\"X\",\"payload\":{\"a\":1}}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/integration-pu/inbound/applications with valid key → 201")
    void inbound_validKey_returns201() throws Exception {
        CommercialApplication saved = new CommercialApplication();
        saved.setId(42L);
        saved.setExternalReferenceId("PU-2026-001");
        when(commercialApplicationService.submitFromPu(eq("PU-2026-001"), any(), any()))
                .thenReturn(saved);

        mockMvc.perform(post("/api/integration-pu/inbound/applications")
                        .header(INTEGRATION_KEY_HEADER, "test-inbound-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"externalReferenceId":"PU-2026-001","payload":{"companyName":"Acme Ltd"}}\
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.externalReferenceId").value("PU-2026-001"));
    }

    @Test
    @DisplayName("GET /api/integration-pu/applications as MANAGER → 403")
    void list_asManager_returns403() throws Exception {
        mockMvc.perform(get("/api/integration-pu/applications")
                        .with(user("manager").roles("MANAGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/integration-pu/applications as ADMIN → 200")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void list_asAdmin_returns200() throws Exception {
        CommercialApplication a = new CommercialApplication();
        a.setId(1L);
        a.setExternalReferenceId("R1");
        a.setStatus(CommercialApplicationStatus.PENDING);
        a.setCreatedAt(Instant.parse("2026-01-01T12:00:00Z"));
        when(commercialApplicationService.findAll(Optional.empty())).thenReturn(List.of(a));

        mockMvc.perform(get("/api/integration-pu/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].externalReferenceId").value("R1"));
    }

    @Test
    @DisplayName("POST /api/integration-pu/applications/1/approve as ADMIN → 200")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void approve_asAdmin_returns200() throws Exception {
        CommercialApplication app = new CommercialApplication();
        app.setId(1L);
        app.setStatus(CommercialApplicationStatus.APPROVED);
        app.setGeneratedEmailBody("Dear Applicant...");
        app.setExternalReferenceId("X");

        var wh = CommercialApplicationService.WebhookOutcome.skipped();
        var result = new CommercialApplicationService.DecisionResult(app, wh);
        when(commercialApplicationService.approve(eq(1L), any(), eq("admin"))).thenReturn(result);

        mockMvc.perform(post("/api/integration-pu/applications/1/approve")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.puWebhookStatus").value("SKIPPED"));
    }
}
