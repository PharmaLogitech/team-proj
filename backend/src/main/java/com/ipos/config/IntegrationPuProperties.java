package com.ipos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * IPOS-PU ↔ IPOS-SA integration settings.
 * <ul>
 *   <li>{@code inbound-api-key} — required for IPOS-PU to POST applications (header {@code X-IPOS-Integration-Key}).</li>
 *   <li>{@code webhook-url} — optional default URL to notify IPOS-PU when an admin approves or rejects.</li>
 *   <li>{@code webhook-api-key} — optional bearer/API key sent to IPOS-PU on outbound webhook POSTs.</li>
 *   <li>{@code auto-create-merchant-on-approve} — when true, approving an application creates an IPOS-SA merchant account and appends credentials to the email sent to PU.</li>
 *   <li>{@code pu-base-url} — IPOS-PU base URL for §3b SA → PU email relay (no trailing slash).</li>
 *   <li>{@code relay-email-enabled} — when true, POST /api/merchant-accounts triggers relay to PU after successful create.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "ipos.integration-pu")
public class IntegrationPuProperties {

    /**
     * Shared secret for inbound calls. If blank, inbound submission is disabled.
     * Also sent as {@code X-IPOS-Integration-Key} on SA → PU relay (§3b), matching PU {@code ipos.pu.integration.sa-api-key}.
     */
    private String inboundApiKey = "";

    /**
     * IPOS-PU origin for §3b relay, e.g. {@code http://localhost:8082}.
     */
    private String puBaseUrl = "";

    /**
     * When true, successful direct merchant creation emails credentials via PU SMTP (§3b). Does not affect commercial approval (§3a webhook).
     */
    private boolean relayEmailEnabled = false;

    private String webhookUrl = "";

    private String webhookApiKey = "";

    /**
     * If true (default), approving a commercial application creates a MERCHANT user on IPOS-SA
     * and appends login credentials to the generated email body for IPOS-PU to relay.
     */
    private boolean autoCreateMerchantOnApprove = true;

    /**
     * Default credit limit for auto-created merchants (commercial applications from PU).
     */
    private BigDecimal autoMerchantCreditLimit = new BigDecimal("10000.00");

    /**
     * Default fixed discount percent when auto-creating with FIXED plan.
     */
    private BigDecimal autoMerchantFixedDiscountPercent = new BigDecimal("5.00");

    /**
     * Placeholder phone when PU payload has no phone (ACC-US1 requires non-blank contact phone).
     */
    private String autoMerchantPlaceholderPhone = "0000000000";

    public String getInboundApiKey() {
        return inboundApiKey;
    }

    public void setInboundApiKey(String inboundApiKey) {
        this.inboundApiKey = inboundApiKey != null ? inboundApiKey : "";
    }

    public String getPuBaseUrl() {
        return puBaseUrl;
    }

    public void setPuBaseUrl(String puBaseUrl) {
        this.puBaseUrl = puBaseUrl != null ? puBaseUrl : "";
    }

    public boolean isRelayEmailEnabled() {
        return relayEmailEnabled;
    }

    public void setRelayEmailEnabled(boolean relayEmailEnabled) {
        this.relayEmailEnabled = relayEmailEnabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl != null ? webhookUrl : "";
    }

    public String getWebhookApiKey() {
        return webhookApiKey;
    }

    public void setWebhookApiKey(String webhookApiKey) {
        this.webhookApiKey = webhookApiKey != null ? webhookApiKey : "";
    }

    public boolean isAutoCreateMerchantOnApprove() {
        return autoCreateMerchantOnApprove;
    }

    public void setAutoCreateMerchantOnApprove(boolean autoCreateMerchantOnApprove) {
        this.autoCreateMerchantOnApprove = autoCreateMerchantOnApprove;
    }

    public BigDecimal getAutoMerchantCreditLimit() {
        return autoMerchantCreditLimit;
    }

    public void setAutoMerchantCreditLimit(BigDecimal autoMerchantCreditLimit) {
        this.autoMerchantCreditLimit = autoMerchantCreditLimit;
    }

    public BigDecimal getAutoMerchantFixedDiscountPercent() {
        return autoMerchantFixedDiscountPercent;
    }

    public void setAutoMerchantFixedDiscountPercent(BigDecimal autoMerchantFixedDiscountPercent) {
        this.autoMerchantFixedDiscountPercent = autoMerchantFixedDiscountPercent;
    }

    public String getAutoMerchantPlaceholderPhone() {
        return autoMerchantPlaceholderPhone;
    }

    public void setAutoMerchantPlaceholderPhone(String autoMerchantPlaceholderPhone) {
        this.autoMerchantPlaceholderPhone = autoMerchantPlaceholderPhone != null ? autoMerchantPlaceholderPhone : "";
    }
}
