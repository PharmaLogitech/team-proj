package com.ipos.dto;

import java.math.BigDecimal;

/**
 * RPT-US1 — Sales turnover for a calendar date range (inclusive of start/end days,
 * interpreted in {@link com.ipos.service.ReportingService#REPORT_ZONE}).
 */
public class SalesTurnoverResponse {

    private long totalQuantitySold;
    private BigDecimal totalRevenue;
    private String currency;

    public SalesTurnoverResponse() {
    }

    public SalesTurnoverResponse(long totalQuantitySold, BigDecimal totalRevenue, String currency) {
        this.totalQuantitySold = totalQuantitySold;
        this.totalRevenue = totalRevenue;
        this.currency = currency;
    }

    public long getTotalQuantitySold() {
        return totalQuantitySold;
    }

    public void setTotalQuantitySold(long totalQuantitySold) {
        this.totalQuantitySold = totalQuantitySold;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public void setTotalRevenue(BigDecimal totalRevenue) {
        this.totalRevenue = totalRevenue;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
