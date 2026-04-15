package com.ipos.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Payload from IPOS-PU when submitting a commercial application to IPOS-SA.
 */
public record InboundCommercialApplicationRequest(
        @NotBlank(message = "externalReferenceId is required") String externalReferenceId,
        @NotNull(message = "payload is required") JsonNode payload,
        String callbackUrl
) {
}
