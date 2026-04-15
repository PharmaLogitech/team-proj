package com.ipos.dto;

public record CommercialApplicationDecisionResponse(
        long id,
        String status,
        String generatedEmailBody,
        String rejectionReason,
        String puWebhookStatus,
        String puWebhookError
) {
}
