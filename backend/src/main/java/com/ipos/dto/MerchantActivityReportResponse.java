package com.ipos.dto;

import java.util.ArrayList;
import java.util.List;

/** RPT-US3 — full merchant activity payload. */
public class MerchantActivityReportResponse {

    private MerchantActivityHeaderDto header;
    private List<MerchantActivityOrderDto> orders = new ArrayList<>();

    public MerchantActivityReportResponse() {
    }

    public MerchantActivityHeaderDto getHeader() {
        return header;
    }

    public void setHeader(MerchantActivityHeaderDto header) {
        this.header = header;
    }

    public List<MerchantActivityOrderDto> getOrders() {
        return orders;
    }

    public void setOrders(List<MerchantActivityOrderDto> orders) {
        this.orders = orders != null ? orders : new ArrayList<>();
    }
}
