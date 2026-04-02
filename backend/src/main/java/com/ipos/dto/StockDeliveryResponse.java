package com.ipos.dto;

import com.ipos.entity.StockDelivery;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Read-only response DTO returned after a stock delivery is recorded (CAT-US7).
 * Returned by POST /api/products/{productId}/deliveries with HTTP 201 Created.
 */
public class StockDeliveryResponse {

    private Long id;
    private Long productId;
    private String productCode;
    private LocalDate deliveryDate;
    private Integer quantityReceived;
    private String supplierReference;
    private Long recordedByUserId;
    private String recordedByUsername;
    private Instant recordedAt;
    private Integer newAvailabilityCount;

    public StockDeliveryResponse() {
    }

    /**
     * Maps a persisted StockDelivery and the updated product availability to a response DTO.
     *
     * @param delivery             the saved delivery entity
     * @param newAvailabilityCount the product's availability after this delivery was applied
     */
    public static StockDeliveryResponse from(StockDelivery delivery, int newAvailabilityCount) {
        StockDeliveryResponse r = new StockDeliveryResponse();
        r.id = delivery.getId();
        r.productId = delivery.getProduct().getId();
        r.productCode = delivery.getProduct().getProductCode();
        r.deliveryDate = delivery.getDeliveryDate();
        r.quantityReceived = delivery.getQuantityReceived();
        r.supplierReference = delivery.getSupplierReference();
        r.recordedByUserId = delivery.getRecordedBy().getId();
        r.recordedByUsername = delivery.getRecordedBy().getUsername();
        r.recordedAt = delivery.getRecordedAt();
        r.newAvailabilityCount = newAvailabilityCount;
        return r;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public String getProductCode() { return productCode; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public Integer getQuantityReceived() { return quantityReceived; }
    public String getSupplierReference() { return supplierReference; }
    public Long getRecordedByUserId() { return recordedByUserId; }
    public String getRecordedByUsername() { return recordedByUsername; }
    public Instant getRecordedAt() { return recordedAt; }
    public Integer getNewAvailabilityCount() { return newAvailabilityCount; }
}
