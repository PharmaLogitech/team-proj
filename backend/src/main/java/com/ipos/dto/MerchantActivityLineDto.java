package com.ipos.dto;

import java.math.BigDecimal;

/** One catalogue line on an order in the RPT-US3 activity report. */
public class MerchantActivityLineDto {

    private String description;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    public MerchantActivityLineDto() {
    }

    public MerchantActivityLineDto(String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = lineTotal;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public BigDecimal getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(BigDecimal lineTotal) {
        this.lineTotal = lineTotal;
    }
}
