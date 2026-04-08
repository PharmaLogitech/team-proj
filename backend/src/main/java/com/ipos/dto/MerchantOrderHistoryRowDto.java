package com.ipos.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row of the merchant order history report (RPT-US2).
 */
public class MerchantOrderHistoryRowDto {

    private Long orderId;
    private Instant orderDate;
    private Instant dispatchDate;
    private BigDecimal totalValue;
    /** e.g. PENDING, PARTIAL, PAID */
    private String paymentStatus;

    public MerchantOrderHistoryRowDto() {
    }

    public MerchantOrderHistoryRowDto(Long orderId, Instant orderDate, Instant dispatchDate,
                                      BigDecimal totalValue, String paymentStatus) {
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.dispatchDate = dispatchDate;
        this.totalValue = totalValue;
        this.paymentStatus = paymentStatus;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public Instant getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Instant orderDate) {
        this.orderDate = orderDate;
    }

    public Instant getDispatchDate() {
        return dispatchDate;
    }

    public void setDispatchDate(Instant dispatchDate) {
        this.dispatchDate = dispatchDate;
    }

    public BigDecimal getTotalValue() {
        return totalValue;
    }

    public void setTotalValue(BigDecimal totalValue) {
        this.totalValue = totalValue;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }
}
