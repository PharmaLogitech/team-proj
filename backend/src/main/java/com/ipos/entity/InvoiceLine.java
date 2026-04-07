/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "invoice_lines" table (ORD-US5).        ║
 * ║                                                                              ║
 * ║  WHY:  Each Invoice has one or more line items that snapshot the order      ║
 * ║        items at the time the invoice was generated.  Using a separate       ║
 * ║        table (rather than joining live to order_items) ensures invoice      ║
 * ║        data remains immutable for audit purposes.                           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonBackReference("invoice-lines")
    private Invoice invoice;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "product_id")
    private Long productId;

    /** Snapshot of product description at invoice time. */
    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    /** quantity * unitPrice (pre-discount; discount is header-level). */
    @Column(name = "line_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal lineTotal;

    public InvoiceLine() {
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }
}
