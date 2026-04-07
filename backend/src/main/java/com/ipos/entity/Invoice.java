/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "invoices" table (ORD-US5).             ║
 * ║                                                                              ║
 * ║  WHY:  An Invoice is generated automatically when an order reaches          ║
 * ║        ACCEPTED status.  It snapshots merchant contact details, VAT         ║
 * ║        number, and financial totals so historical invoices remain accurate  ║
 * ║        even if the merchant profile is later updated.                       ║
 * ║                                                                              ║
 * ║  RELATIONSHIPS:                                                              ║
 * ║        1:1 with Order (unique order_id FK).                                 ║
 * ║        1:N with InvoiceLine (cascade ALL, orphanRemoval).                   ║
 * ║        1:N with Payment (mapped from Payment side).                         ║
 * ║                                                                              ║
 * ║  ORD-US3 (balance): dueDate = issuedAt + paymentTermsDays.  Outstanding    ║
 * ║        amount = totalDue - sum(payments).                                   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable invoice reference, e.g. INV-2026-00001. */
    @Column(name = "invoice_number", unique = true, nullable = false, length = 30)
    private String invoiceNumber;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Payment due date derived from issuedAt + merchant paymentTermsDays. */
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    // ── Order relationship (1:1) ──────────────────────────────────────────

    @OneToOne
    @JoinColumn(name = "order_id", unique = true, nullable = false)
    private Order order;

    // ── Merchant snapshot (immutable after invoice creation) ───────────────

    @ManyToOne
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;

    @Column(name = "merchant_name", nullable = false)
    private String merchantName;

    @Column(name = "merchant_address", nullable = false)
    private String merchantAddress;

    @Column(name = "merchant_email", nullable = false)
    private String merchantEmail;

    @Column(name = "merchant_phone", nullable = false)
    private String merchantPhone;

    @Column(name = "merchant_vat")
    private String merchantVat;

    // ── Financial totals (snapshotted from Order at invoice creation) ──────

    @Column(name = "gross_total", precision = 12, scale = 2, nullable = false)
    private BigDecimal grossTotal;

    @Column(name = "fixed_discount_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal fixedDiscountAmount;

    @Column(name = "flexible_credit_applied", precision = 12, scale = 2, nullable = false)
    private BigDecimal flexibleCreditApplied;

    @Column(name = "total_due", precision = 12, scale = 2, nullable = false)
    private BigDecimal totalDue;

    // ── Line items ────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("invoice-lines")
    private List<InvoiceLine> lines = new ArrayList<>();

    // ── Payments ──────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL)
    @JsonManagedReference("invoice-payments")
    private List<Payment> payments = new ArrayList<>();

    public Invoice() {
    }

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public User getMerchant() { return merchant; }
    public void setMerchant(User merchant) { this.merchant = merchant; }

    public String getMerchantName() { return merchantName; }
    public void setMerchantName(String merchantName) { this.merchantName = merchantName; }

    public String getMerchantAddress() { return merchantAddress; }
    public void setMerchantAddress(String merchantAddress) { this.merchantAddress = merchantAddress; }

    public String getMerchantEmail() { return merchantEmail; }
    public void setMerchantEmail(String merchantEmail) { this.merchantEmail = merchantEmail; }

    public String getMerchantPhone() { return merchantPhone; }
    public void setMerchantPhone(String merchantPhone) { this.merchantPhone = merchantPhone; }

    public String getMerchantVat() { return merchantVat; }
    public void setMerchantVat(String merchantVat) { this.merchantVat = merchantVat; }

    public BigDecimal getGrossTotal() { return grossTotal; }
    public void setGrossTotal(BigDecimal grossTotal) { this.grossTotal = grossTotal; }

    public BigDecimal getFixedDiscountAmount() { return fixedDiscountAmount; }
    public void setFixedDiscountAmount(BigDecimal fixedDiscountAmount) { this.fixedDiscountAmount = fixedDiscountAmount; }

    public BigDecimal getFlexibleCreditApplied() { return flexibleCreditApplied; }
    public void setFlexibleCreditApplied(BigDecimal flexibleCreditApplied) { this.flexibleCreditApplied = flexibleCreditApplied; }

    public BigDecimal getTotalDue() { return totalDue; }
    public void setTotalDue(BigDecimal totalDue) { this.totalDue = totalDue; }

    public List<InvoiceLine> getLines() { return lines; }
    public void setLines(List<InvoiceLine> lines) { this.lines = lines; }

    public List<Payment> getPayments() { return payments; }
    public void setPayments(List<Payment> payments) { this.payments = payments; }
}
