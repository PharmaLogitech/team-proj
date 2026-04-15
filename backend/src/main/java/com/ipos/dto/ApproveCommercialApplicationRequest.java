package com.ipos.dto;

/**
 * Optional override for the generated approval email body. When omitted or blank,
 * the server builds the canonical text from the stored application payload.
 */
public record ApproveCommercialApplicationRequest(String emailBody) {
}
