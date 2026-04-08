package com.ipos.repository;

/**
 * Closed projection: aggregate quantity received per product (RPT-US5).
 */
public interface StockDeliveryQtyReceivedProjection {

    Long getProductId();

    String getProductCode();

    Long getQtyReceived();
}
