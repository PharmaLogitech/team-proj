package com.ipos.dto;

import java.util.ArrayList;
import java.util.List;

/** RPT-US5 — Per-product quantities sold (ORD) vs received (stock deliveries) in a period. */
public class StockTurnoverReportResponse {

    private List<StockTurnoverRowDto> rows = new ArrayList<>();

    public StockTurnoverReportResponse() {
    }

    public StockTurnoverReportResponse(List<StockTurnoverRowDto> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public List<StockTurnoverRowDto> getRows() {
        return rows;
    }

    public void setRows(List<StockTurnoverRowDto> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }
}
