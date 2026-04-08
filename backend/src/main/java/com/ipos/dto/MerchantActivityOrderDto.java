package com.ipos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** One order with line detail for RPT-US3. */
public class MerchantActivityOrderDto {

    private Long orderId;
    private Instant placedAt;
    private String orderStatus;
    private List<MerchantActivityLineDto> lines = new ArrayList<>();
    private BigDecimal grossTotal;
    private BigDecimal fixedDiscountAmount;
    private BigDecimal flexibleCreditApplied;
    private BigDecimal totalDue;

    public MerchantActivityOrderDto() {
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public List<MerchantActivityLineDto> getLines() {
        return lines;
    }

    public void setLines(List<MerchantActivityLineDto> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    public BigDecimal getGrossTotal() {
        return grossTotal;
    }

    public void setGrossTotal(BigDecimal grossTotal) {
        this.grossTotal = grossTotal;
    }

    public BigDecimal getFixedDiscountAmount() {
        return fixedDiscountAmount;
    }

    public void setFixedDiscountAmount(BigDecimal fixedDiscountAmount) {
        this.fixedDiscountAmount = fixedDiscountAmount;
    }

    public BigDecimal getFlexibleCreditApplied() {
        return flexibleCreditApplied;
    }

    public void setFlexibleCreditApplied(BigDecimal flexibleCreditApplied) {
        this.flexibleCreditApplied = flexibleCreditApplied;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public void setTotalDue(BigDecimal totalDue) {
        this.totalDue = totalDue;
    }
}
