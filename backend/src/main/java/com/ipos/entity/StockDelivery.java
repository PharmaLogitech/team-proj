/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "stock_deliveries" table — a record of   ║
 * ║        each stock replenishment received for a product (CAT-US7).           ║
 * ║                                                                              ║
 * ║  WHY:  CAT-US7 requires administrators to record stock deliveries so that    ║
 * ║        inventory levels can be kept accurate over time.  Each delivery:      ║
 * ║          - Captures the business delivery date (LocalDate, not a timestamp), ║
 * ║            the quantity received, and an optional supplier reference.        ║
 * ║          - Atomically increments the product's availabilityCount in the      ║
 * ║            same @Transactional call (ProductService.recordStockDelivery).    ║
 * ║          - Records which User (ADMIN) made the entry and when.              ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a supplier FK if a Supplier entity is introduced.              ║
 * ║        - Add a "batch number" or "expiry date" field for pharmaceutical     ║
 * ║          traceability requirements.                                          ║
 * ║        - Expose a GET endpoint for delivery history per product.            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "stock_deliveries")
public class StockDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* The product whose stock was replenished. */
    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /**
     * Business delivery date — the date the stock physically arrived.
     * Uses LocalDate (not Instant) to avoid timezone ambiguity for a "date-only" concept.
     * Spring Boot auto-parses ISO-8601 strings (yyyy-MM-dd) from JSON.
     */
    @Column(name = "delivery_date", nullable = false)
    private LocalDate deliveryDate;

    /**
     * Number of units received in this delivery.
     * Must be strictly positive (≥ 1); validated in RecordStockDeliveryRequest.
     */
    @Column(name = "quantity_received", nullable = false)
    private Integer quantityReceived;

    /**
     * Optional free-text supplier or purchase-order reference.
     * Allows operators to cross-reference physical delivery notes.
     */
    @Column(name = "supplier_reference", length = 255)
    private String supplierReference;

    /** The ADMIN user who recorded this delivery entry. */
    @ManyToOne
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedBy;

    /** Server-side timestamp of when the delivery was entered into the system. */
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public StockDelivery() {
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public LocalDate getDeliveryDate() { return deliveryDate; }
    public void setDeliveryDate(LocalDate deliveryDate) { this.deliveryDate = deliveryDate; }

    public Integer getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(Integer quantityReceived) { this.quantityReceived = quantityReceived; }

    public String getSupplierReference() { return supplierReference; }
    public void setSupplierReference(String supplierReference) { this.supplierReference = supplierReference; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }
}
