/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "payments" table (ORD-US6).             ║
 * ║                                                                              ║
 * ║  WHY:  An Administrator records payments made by merchants against          ║
 * ║        specific invoices.  Each payment reduces the outstanding balance     ║
 * ║        (ORD-US3) for that merchant.  The method enum matches the brief:    ║
 * ║        Bank Transfer, Card, or Cheque.                                     ║
 * ║                                                                              ║
 * ║  CONSTRAINT: Payment amount must not exceed the invoice's remaining         ║
 * ║        outstanding balance (enforced by PaymentService).                    ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "invoice_id", nullable = false)
    @JsonBackReference("invoice-payments")
    private Invoice invoice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    /** The ADMIN user who recorded this payment. */
    @ManyToOne
    @JoinColumn(name = "recorded_by", nullable = false)
    private User recordedBy;

    /**
     * Payment methods as specified in ORD-US6:
     * "record payments made via Bank Transfer, Card or Check."
     */
    public enum PaymentMethod {
        BANK_TRANSFER,
        CARD,
        CHEQUE
    }

    public Payment() {
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Invoice getInvoice() { return invoice; }
    public void setInvoice(Invoice invoice) { this.invoice = invoice; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public User getRecordedBy() { return recordedBy; }
    public void setRecordedBy(User recordedBy) { this.recordedBy = recordedBy; }
}
