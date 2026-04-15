package com.ipos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipos.config.IntegrationPuProperties;
import com.ipos.security.IntegrationPuInboundApiKeyFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * §3b: SA generates email content; IPOS-PU-COMMS sends via SMTP.
 */
@Service
public class PuCommsRelayService {

    private static final Logger log = LoggerFactory.getLogger(PuCommsRelayService.class);

    private static final String RELAY_PATH = "/api/integration-sa/relay-email";

    private final IntegrationPuProperties properties;
    private final RestClient integrationPuRestClient;
    private final ObjectMapper objectMapper;

    public PuCommsRelayService(
            IntegrationPuProperties properties,
            RestClient integrationPuRestClient,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.integrationPuRestClient = integrationPuRestClient;
        this.objectMapper = objectMapper;
    }

    /**
     * After successful {@code POST /api/merchant-accounts} — does not run for commercial-application auto-create.
     */
    public void relayNewMerchantWelcomeEmail(String contactEmail, String username, String plainPassword) {
        if (!properties.isRelayEmailEnabled()) {
            return;
        }
        String base = properties.getPuBaseUrl();
        if (base == null || base.isBlank()) {
            log.warn("PU email relay enabled but ipos.integration-pu.pu-base-url is blank; skipping relay.");
            return;
        }
        String key = properties.getInboundApiKey();
        if (key == null || key.isBlank()) {
            log.warn("PU email relay enabled but ipos.integration-pu.inbound-api-key is blank; skipping relay.");
            return;
        }
        String subject = MerchantCredentialsEmailFormatter.newMerchantWelcomeEmailSubject();
        String body = MerchantCredentialsEmailFormatter.newMerchantWelcomeEmailBody(username, plainPassword);
        relayEmail(contactEmail, subject, body, base.trim(), key);
    }

    void relayEmail(String to, String subject, String body, String puBaseUrl, String apiKey) {
        String url = puBaseUrl.replaceAll("/+$", "") + RELAY_PATH;
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("to", to);
        payload.put("subject", subject);
        payload.put("body", body);
        try {
            String json = objectMapper.writeValueAsString(payload);
            integrationPuRestClient.post()
                    .uri(url)
                    .header(IntegrationPuInboundApiKeyFilter.INTEGRATION_KEY_HEADER, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("IPOS-PU email relay failed for {}: {}", to, e.getMessage());
        }
    }
}
