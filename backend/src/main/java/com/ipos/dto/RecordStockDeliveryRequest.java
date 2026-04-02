package com.ipos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for recording a stock delivery (CAT-US7).
 * Accepted by POST /api/products/{productId}/deliveries (ADMIN only).
 */
public class RecordStockDeliveryRequest {

    /** Business date the stock physically arrived. ISO-8601 format: yyyy-MM-dd. */
    @NotNull(message = "Delivery date is required")
    private LocalDate deliveryDate;

    /**
     * Number of units received.
     * Must be strictly positive — the acceptance criterion states "reject any delivery
     * quantity equal to or less than zero" (CAT-US7 AC3).
     */
    @NotNull(message = "Quantity received is required")
    @Min(value = 1, message = "Quantity received must be at least 1")
    private Integer quantityReceived;

    /** Optional purchase-order or supplier reference for cross-referencing paperwork. */
    @Size(max = 255, message = "Supplier reference must not exceed 255 characters")
    private String supplierReference;

    public LocalDate getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDate deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public Integer getQuantityReceived() {
        return quantityReceived;
    }

    public void setQuantityReceived(Integer quantityReceived) {
        this.quantityReceived = quantityReceived;
    }

    public String getSupplierReference() {
        return supplierReference;
    }

    public void setSupplierReference(String supplierReference) {
        this.supplierReference = supplierReference;
    }
}
