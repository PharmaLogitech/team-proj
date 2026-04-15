package com.ipos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ipos.config.IntegrationPuProperties;
import com.ipos.entity.CommercialApplication;
import com.ipos.entity.CommercialApplicationStatus;
import com.ipos.entity.User;
import com.ipos.entity.MerchantProfile;
import com.ipos.repository.CommercialApplicationRepository;
import com.ipos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialApplicationService")
class CommercialApplicationServiceTest {

    @Mock
    private CommercialApplicationRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MerchantAccountService merchantAccountService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final IntegrationPuProperties properties = new IntegrationPuProperties();
    private final RestClient restClient = RestClient.create();

    private CommercialApplicationService service;

    @BeforeEach
    void setUp() {
        properties.setAutoCreateMerchantOnApprove(true);
        properties.setWebhookUrl("");
        service = new CommercialApplicationService(
                repository, userRepository, objectMapper, properties, restClient, merchantAccountService);
    }

    @Test
    @DisplayName("submitFromPu — duplicate external reference throws")
    void submit_duplicate_throws() {
        when(repository.findByExternalReferenceId("DUP")).thenReturn(Optional.of(new CommercialApplication()));

        assertThrows(CommercialApplicationService.DuplicateExternalReferenceException.class,
                () -> service.submitFromPu("DUP", objectMapper.createObjectNode().put("a", 1), null));
    }

    @Test
    @DisplayName("buildApprovalEmailBody — includes reference and company from payload")
    void buildApprovalEmailBody_includesFields() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("companyName", "Demo Pharma Ltd");
        payload.put("contactName", "Jane Doe");
        payload.put("contactEmail", "jane@example.com");
        String json = objectMapper.writeValueAsString(payload);

        String body = service.buildApprovalEmailBody("PU-REF-99", json);

        assertTrue(body.contains("PU-REF-99"));
        assertTrue(body.contains("Demo Pharma Ltd"));
        assertTrue(body.contains("jane@example.com"));
        assertTrue(body.contains("approved"));
    }

    @Test
    @DisplayName("approve — persists APPROVED and generated email body")
    void approve_success() {
        User admin = new User();
        admin.setId(10L);
        admin.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        CommercialApplication app = new CommercialApplication();
        app.setId(7L);
        app.setExternalReferenceId("EXT-1");
        app.setStatus(CommercialApplicationStatus.PENDING);
        app.setPayloadJson("{\"companyName\":\"Co\",\"contactName\":\"Bob\"}");
        when(repository.findById(7L)).thenReturn(Optional.of(app));
        when(repository.save(any(CommercialApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        properties.setWebhookUrl("");
        properties.setAutoCreateMerchantOnApprove(false);

        var result = service.approve(7L, Optional.empty(), "admin");

        assertEquals(CommercialApplicationStatus.APPROVED, result.getApplication().getStatus());
        assertTrue(result.getApplication().getGeneratedEmailBody().contains("Co"));
        verify(repository).save(any(CommercialApplication.class));
        verify(merchantAccountService, never()).createMerchantAccount(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("approve — auto-creates merchant and appends credentials when enabled")
    void approve_autoMerchant_appendsCredentials() throws Exception {
        User admin = new User();
        admin.setId(10L);
        admin.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.empty());

        CommercialApplication app = new CommercialApplication();
        app.setId(7L);
        app.setExternalReferenceId("EXT-1");
        app.setStatus(CommercialApplicationStatus.PENDING);
        app.setPayloadJson("{\"companyName\":\"Co\",\"contactName\":\"Bob\",\"contactEmail\":\"bob@corp.com\"}");
        when(repository.findById(7L)).thenReturn(Optional.of(app));
        when(repository.save(any(CommercialApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        properties.setWebhookUrl("");
        properties.setAutoCreateMerchantOnApprove(true);

        MerchantProfile created = new MerchantProfile();
        when(merchantAccountService.createMerchantAccount(
                eq("Bob"),
                eq("bob"),
                anyString(),
                eq("bob@corp.com"),
                eq("0000000000"),
                anyString(),
                any(),
                eq(MerchantProfile.DiscountPlanType.FIXED),
                any(),
                isNull(),
                isNull(),
                isNull())).thenReturn(created);

        var result = service.approve(7L, Optional.empty(), "admin");

        assertEquals(CommercialApplicationStatus.APPROVED, result.getApplication().getStatus());
        String body = result.getApplication().getGeneratedEmailBody();
        assertTrue(body.contains("Username: bob"));
        assertTrue(body.contains("Password:"));
        verify(merchantAccountService).createMerchantAccount(
                eq("Bob"),
                eq("bob"),
                anyString(),
                eq("bob@corp.com"),
                eq("0000000000"),
                anyString(),
                any(),
                eq(MerchantProfile.DiscountPlanType.FIXED),
                any(),
                isNull(),
                isNull(),
                isNull());
    }

    @Test
    @DisplayName("reject — requires non-blank reason")
    void reject_blankReason_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.reject(1L, "  ", "admin"));
        verify(repository, never()).save(any());
    }
}
