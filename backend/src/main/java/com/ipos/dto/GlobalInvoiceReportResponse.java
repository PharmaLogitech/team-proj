package com.ipos.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * RPT-US4 — All invoices issued in a date range across merchants.
 */
public class GlobalInvoiceReportResponse {

    private List<GlobalInvoiceRowDto> rows = new ArrayList<>();

    public GlobalInvoiceReportResponse() {
    }

    public GlobalInvoiceReportResponse(List<GlobalInvoiceRowDto> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }

    public List<GlobalInvoiceRowDto> getRows() {
        return rows;
    }

    public void setRows(List<GlobalInvoiceRowDto> rows) {
        this.rows = rows != null ? rows : new ArrayList<>();
    }
}
