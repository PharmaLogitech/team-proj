package com.ipos.dto;

import java.time.Instant;

public record CommercialApplicationDetailDto(
        long id,
        String externalReferenceId,
        String status,
        String payloadJson,
        String generatedEmailBody,
        String rejectionReason,
        Instant createdAt,
        Instant decidedAt,
        String decidedByUsername,
        String callbackUrl
) {
}
