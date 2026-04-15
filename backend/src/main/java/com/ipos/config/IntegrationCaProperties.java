package com.ipos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IPOS-CA integration settings for the SA → CA order-status webhook.
 * <ul>
 *   <li>{@code webhook-url} — CA's inbound endpoint (default {@code http://localhost:8081/order-update}).</li>
 *   <li>{@code webhook-enabled} — master switch; when false, no outbound calls are made to CA.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "ipos.integration-ca")
public class IntegrationCaProperties {

    private String webhookUrl = "http://localhost:8081/order-update";

    private boolean webhookEnabled = false;

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl != null ? webhookUrl : "";
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public void setWebhookEnabled(boolean webhookEnabled) {
        this.webhookEnabled = webhookEnabled;
    }
}
