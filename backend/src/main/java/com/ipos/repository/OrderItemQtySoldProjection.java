package com.ipos.repository;

/**
 * Closed projection: aggregate quantity sold per product (RPT-US5).
 */
public interface OrderItemQtySoldProjection {

    Long getProductId();

    String getProductCode();

    Long getQtySold();
}
