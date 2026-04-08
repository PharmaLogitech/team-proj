package com.ipos.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * RPT-US2 — Order history for one merchant over a date range, including a totals line.
 */
public class MerchantOrderHistoryResponse {

    private List<MerchantOrderHistoryRowDto> rows = new ArrayList<>();
    private BigDecimal periodTotalValue;

    public MerchantOrderHistoryResponse() {
    }

    public MerchantOrderHistoryResponse(List<MerchantOrderHistoryRowDto> rows, BigDecimal periodTotalValue) {
        this.rows = rows != null ? rows : new ArrayList<>();
        this.periodTotalValue = periodTotalValue;
    }

    public List<MerchantOrderHistoryRowDto> getRows() {
        return rows;
    }

    public void setRows(List<MerchantOrderHistoryRowDto> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public BigDecimal getPeriodTotalValue() {
        return periodTotalValue;
    }

    public void setPeriodTotalValue(BigDecimal periodTotalValue) {
        this.periodTotalValue = periodTotalValue;
    }
}
