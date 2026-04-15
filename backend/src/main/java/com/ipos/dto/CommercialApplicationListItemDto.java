package com.ipos.dto;

import java.time.Instant;

public record CommercialApplicationListItemDto(
        long id,
        String externalReferenceId,
        String status,
        Instant createdAt
) {
}
