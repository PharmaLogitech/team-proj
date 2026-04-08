package com.ipos.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Spring Data closed projection for RPT-US4 global invoice query (one row per invoice).
 */
public interface InvoiceGlobalReportProjection {

    Long getInvoiceId();

    String getInvoiceNumber();

    Instant getIssuedAt();

    BigDecimal getTotalDue();

    Long getMerchantId();

    String getMerchantUsername();

    String getMerchantName();

    BigDecimal getPaidSum();
}
