/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "monthly_rebate_settlements" table.      ║
 * ║                                                                              ║
 * ║  WHY (brief §i — flexible discount plan):                                    ║
 * ║        For merchants on the FLEXIBLE discount plan, the rebate is           ║
 * ║        calculated at the end of each calendar month based on the total      ║
 * ║        gross value of all orders placed during that month.                  ║
 * ║                                                                              ║
 * ║        This entity records the result of each month-close operation:        ║
 * ║          - Which merchant was settled.                                      ║
 * ║          - Which month was closed (YYYY-MM format).                         ║
 * ║          - The computed rebate amount.                                      ║
 * ║          - The settlement mode (credit toward next orders, or cheque).      ║
 * ║                                                                              ║
 * ║        A UNIQUE CONSTRAINT on (merchant_id, settlement_year_month) prevents ║
 * ║        month from being closed twice for the same merchant (idempotency).   ║
 * ║                                                                              ║
 * ║  SETTLEMENT MODES (brief §i):                                                ║
 * ║        APPLY_CREDIT — The rebate is added to the merchant's                ║
 * ║                       flexibleDiscountCredit balance and deducted from      ║
 * ║                       future order(s).                                      ║
 * ║        CHEQUE       — The rebate is added to chequeRebatePending, to be    ║
 * ║                       paid by cheque at the end of the month.  No actual   ║
 * ║                       payment integration exists yet — this is a ledger    ║
 * ║                       record for reporting.                                 ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add audit fields (createdBy, notes) for compliance.               ║
 * ║        - Add a "reversed" flag for settlement corrections.                 ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

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
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "monthly_rebate_settlements",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"merchant_id", "settlement_year_month"},
                name = "uk_settlement_merchant_month"
        ))
public class MonthlyRebateSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * The merchant this settlement belongs to.
     * @ManyToOne because one merchant can have many monthly settlements
     * (one per month they trade in).
     */
    @ManyToOne
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;

    /*
     * The calendar month this settlement covers, stored as "YYYY-MM".
     * Combined with merchant_id, this forms the unique constraint that
     * prevents double-closing the same month.
     *
     * NOTE: The physical column is named settlement_year_month (not year_month)
     * because year_month is a reserved keyword in MySQL 8+ and breaks DDL.
     */
    @Column(name = "settlement_year_month", nullable = false, length = 7)
    private String yearMonth;

    /*
     * The rebate amount computed for this month.
     * Calculation: look up the merchant's flexible tiers, find the tier
     * matching the month's total gross spend, apply the tier's percentage.
     */
    @Column(name = "computed_discount", precision = 12, scale = 2, nullable = false)
    private BigDecimal computedDiscount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementMode mode;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    /*
     * How the rebate is disbursed (brief §i):
     *   APPLY_CREDIT — Added to MerchantProfile.flexibleDiscountCredit.
     *   CHEQUE       — Added to MerchantProfile.chequeRebatePending.
     */
    public enum SettlementMode {
        APPLY_CREDIT,
        CHEQUE
    }

    public MonthlyRebateSettlement() {
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getMerchant() {
        return merchant;
    }

    public void setMerchant(User merchant) {
        this.merchant = merchant;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public void setYearMonth(String yearMonth) {
        this.yearMonth = yearMonth;
    }

    public BigDecimal getComputedDiscount() {
        return computedDiscount;
    }

    public void setComputedDiscount(BigDecimal computedDiscount) {
        this.computedDiscount = computedDiscount;
    }

    public SettlementMode getMode() {
        return mode;
    }

    public void setMode(SettlementMode mode) {
        this.mode = mode;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(Instant settledAt) {
        this.settledAt = settledAt;
    }
}
