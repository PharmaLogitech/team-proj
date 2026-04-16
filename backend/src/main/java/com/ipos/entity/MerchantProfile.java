/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "merchant_profiles" table — the          ║
 * ║        business profile attached to every MERCHANT user account.            ║
 * ║                                                                              ║
 * ║  WHY (ACC-US1 — Merchant Account Creation):                                  ║
 * ║        The brief requires that when a new merchant account is set up the    ║
 * ║        system asks for contact details, credit limit, and discount plan     ║
 * ║        BEFORE the account is activated.  If the required details are NOT    ║
 * ║        provided, the account will NOT be created.                           ║
 * ║                                                                              ║
 * ║        This entity stores all of that merchant-specific data in a separate  ║
 * ║        table (1:1 with User).  Keeping it separate from User means:         ║
 * ║          - Admin and Manager rows don't carry unused merchant columns.      ║
 * ║          - The merchant profile can evolve independently (ACC-US2/US3/US6). ║
 * ║          - The User entity stays the authentication identity; this entity   ║
 * ║            is the business profile.                                         ║
 * ║                                                                              ║
 * ║  DISCOUNT PLANS (brief §i):                                                  ║
 * ║        Two plan types are supported:                                        ║
 * ║                                                                              ║
 * ║        FIXED  — A single percentage applied to every order at placement.    ║
 * ║                 Stored in fixedDiscountPercent (e.g. 5.00 = 5%).            ║
 * ║                 The discount reduces the order total immediately.           ║
 * ║                                                                              ║
 * ║        FLEXIBLE — Tiered percentages based on total monthly gross spend.    ║
 * ║                   Tiers are stored as JSON in flexibleTiersJson, e.g.:      ║
 * ║                   [{"maxExclusive":1000,"percent":1},                       ║
 * ║                    {"maxExclusive":2000,"percent":2},                        ║
 * ║                    {"percent":3}]                                            ║
 * ║                   The last tier (no maxExclusive) catches everything above. ║
 * ║                   The rebate is calculated at month-close and either:       ║
 * ║                     a) credited to flexibleDiscountCredit (applied to next  ║
 * ║                        order(s)), or                                        ║
 * ║                     b) recorded in chequeRebatePending (for cheque payout). ║
 * ║                                                                              ║
 * ║  STANDING (brief §iii — Manager capabilities):                               ║
 * ║        NORMAL       — Default state; merchant can place orders.             ║
 * ║        IN_DEFAULT   — Merchant has exceeded credit terms or similar;        ║
 * ║                       orders are blocked until a Manager resolves it.       ║
 * ║        SUSPENDED    — Manager-imposed suspension; orders are blocked.       ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - ACC-US2: Add a DiscountPlan entity and FK if plans become shared. ║
 * ║        - ACC-US3: Add flexible-plan config screens.                        ║
 * ║        - ACC-US5: Add audit log for standing transitions.                  ║
 * ║        - ACC-US6: Manager edits credit limits and discount plans.          ║
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "merchant_profiles")
public class MerchantProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * ── USER RELATIONSHIP ────────────────────────────────────────────────────
     *
     * @OneToOne — Each MerchantProfile belongs to exactly one User, and each
     *   User (with role MERCHANT) has at most one MerchantProfile.
     *
     * unique = true — Database-level constraint: no two profiles can point
     *   to the same user.
     *
     * nullable = false — A profile MUST be linked to a user.  The service
     *   layer creates both atomically (ACC-US1: "account will not be created"
     *   if incomplete).
     */
    @OneToOne
    @JoinColumn(name = "user_id", unique = true, nullable = false)
    private User user;

    // ── Contact Details (ACC-US1: mandatory for account creation) ────────────

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false)
    private String contactPhone;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    // ── VAT Registration (ORD-US5 — invoice generation) ─────────────────────

    /** Merchant's VAT registration number, snapshotted onto invoices at issue time. */
    @Column(name = "vat_registration_number")
    private String vatRegistrationNumber;

    // ── Payment Terms (ORD-US3/US5 — invoice due date calculation) ─────────

    /** Days after invoice issue before payment is due. Default 30. */
    @Column(name = "payment_terms_days", nullable = false)
    private int paymentTermsDays = 30;

    // ── Credit Limit (ACC-US1) ───────────────────────────────────────────────

    /*
     * The maximum net outstanding exposure allowed for this merchant.
     * OrderService.placeOrder compares: (sum of non-cancelled order totalDue
     * minus sum of payments on that merchant's invoices), plus the new order's
     * totalDue, against creditLimit. Net exposure is floored at zero.
     *
     * PLACEHOLDER NOTE:
     *   The full credit policy (when to flag IN_DEFAULT, grace periods, etc.)
     *   is not yet defined.  Replace this comment with real policy rules when
     *   ACC-US5 (defaulted accounts) is fully specified.
     */
    @Column(name = "credit_limit", precision = 12, scale = 2, nullable = false)
    private BigDecimal creditLimit;

    // ── Discount Plan (ACC-US1, brief §i) ────────────────────────────────────

    /*
     * Which type of discount plan this merchant uses.
     * Determines how discounts are calculated — see the enum below.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_plan_type", nullable = false)
    private DiscountPlanType discountPlanType;

    /*
     * FIXED plan only: the percentage discount applied to every order.
     * Stored as a percentage value (e.g. 5.00 means 5%).
     * NULL when discountPlanType is FLEXIBLE.
     */
    @Column(name = "fixed_discount_percent", precision = 5, scale = 2)
    private BigDecimal fixedDiscountPercent;

    /*
     * FLEXIBLE plan only: JSON array of tier definitions.
     * Format: [{"maxExclusive":1000,"percent":1}, {"maxExclusive":2000,"percent":2}, {"percent":3}]
     * The last tier has no maxExclusive and acts as a catch-all.
     * Parsed and validated in MerchantAccountService.
     * NULL when discountPlanType is FIXED.
     *
     * PLACEHOLDER NOTE:
     *   A dedicated DiscountTier entity with a proper @OneToMany relationship
     *   would be more robust.  JSON-in-a-column is used here for simplicity
     *   while the discount plan stories (ACC-US2, ACC-US3) are prototyped.
     *   Replace with a normalised structure when those stories are finalised.
     */
    @Column(name = "flexible_tiers_json", columnDefinition = "TEXT")
    private String flexibleTiersJson;

    // ── Account Status (ACC-US1: Inactive → Active lifecycle) ────────────────

    /*
     * The activation status of this merchant account (ACC-US1).
     *
     * INACTIVE — Profile exists but mandatory details are incomplete or
     *            the account has not yet been fully activated.  In the
     *            current implementation, the transactional create path
     *            always sets ACTIVE on success, so INACTIVE only appears
     *            if a future draft-save flow is introduced.
     * ACTIVE   — All mandatory fields are present; account is fully
     *            operational and the merchant can trade (subject to
     *            standing checks).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    // ── Account Standing (brief §iii — manager can alter) ────────────────────

    /*
     * The operational state of this merchant account.
     * NORMAL = can trade.  IN_DEFAULT / SUSPENDED = orders blocked.
     * Only Managers can transition IN_DEFAULT → NORMAL | SUSPENDED (ACC-US5).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStanding standing;

    // ── Default Tracking (ACC-US5 — 30-day rule) ──────────────────────────────

    /*
     * Timestamp of when this merchant was placed into IN_DEFAULT standing.
     * Used to enforce the ACC-US5 rule: a Manager can only restore an
     * account from IN_DEFAULT if the default period exceeds 30 days
     * ("long-term payment issue, e.g. longer than 30 days").
     *
     * Set when standing transitions TO IN_DEFAULT; cleared when standing
     * transitions AWAY from IN_DEFAULT.
     */
    @Column(name = "in_default_since")
    private Instant inDefaultSince;

    // ── Flexible Discount Balances ───────────────────────────────────────────

    /*
     * Accumulated discount credit from monthly flexible rebate settlements
     * (settlement mode = APPLY_CREDIT).  Applied against the gross total of
     * the merchant's next order(s), reducing the totalDue.
     *
     * Starts at 0.00.  Increases at month-close; decreases when orders consume it.
     */
    @Column(name = "flexible_discount_credit", precision = 12, scale = 2, nullable = false)
    private BigDecimal flexibleDiscountCredit = BigDecimal.ZERO;

    /*
     * Accumulated rebate amount pending cheque settlement
     * (settlement mode = CHEQUE).  This is a ledger figure for reporting;
     * no actual payment integration exists yet.
     *
     * PLACEHOLDER NOTE:
     *   In a production system, this would trigger an accounts-payable
     *   workflow or integrate with an external payment provider.  For now
     *   it's a simple running total.  Replace with real settlement logic
     *   when financial integration is scoped.
     */
    @Column(name = "cheque_rebate_pending", precision = 12, scale = 2, nullable = false)
    private BigDecimal chequeRebatePending = BigDecimal.ZERO;

    // ── Debtor Reminder (RPT — Generate Debtor Reminders) ─────────────────

    /**
     * Outstanding balance snapshot written by "Generate Debtor Reminders".
     * null = no active reminder; > 0 = merchant sees a warning banner on login.
     */
    @Column(name = "debt_reminder_outstanding", precision = 12, scale = 2)
    private BigDecimal debtReminderOutstanding;

    // ── Enums ────────────────────────────────────────────────────────────────

    /*
     * Account activation status (ACC-US1):
     *   INACTIVE — Account created but not yet fully configured/activated.
     *   ACTIVE   — All mandatory details provided; account is operational.
     */
    public enum AccountStatus {
        INACTIVE,
        ACTIVE
    }

    /*
     * The two discount plan types required by the brief (§i):
     *
     *   FIXED    — Same discount rate applied to every order at placement.
     *   FLEXIBLE — Tiered rates based on calendar-month gross spend;
     *              rebate computed at month-close, not per-order.
     */
    public enum DiscountPlanType {
        FIXED,
        FLEXIBLE
    }

    /*
     * Merchant account standing (brief §iii):
     *
     *   NORMAL      — Active, can place orders.
     *   IN_DEFAULT  — Flagged (e.g. exceeded credit terms); orders blocked.
     *   SUSPENDED   — Manager-imposed suspension; orders blocked.
     *
     * Transition rules (enforced in MerchantProfileController):
     *   Manager can change: IN_DEFAULT → NORMAL, IN_DEFAULT → SUSPENDED.
     */
    public enum MerchantStanding {
        NORMAL,
        IN_DEFAULT,
        SUSPENDED
    }

    // ── Constructors ─────────────────────────────────────────────────────────

    public MerchantProfile() {
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getVatRegistrationNumber() {
        return vatRegistrationNumber;
    }

    public void setVatRegistrationNumber(String vatRegistrationNumber) {
        this.vatRegistrationNumber = vatRegistrationNumber;
    }

    public int getPaymentTermsDays() {
        return paymentTermsDays;
    }

    public void setPaymentTermsDays(int paymentTermsDays) {
        this.paymentTermsDays = paymentTermsDays;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public DiscountPlanType getDiscountPlanType() {
        return discountPlanType;
    }

    public void setDiscountPlanType(DiscountPlanType discountPlanType) {
        this.discountPlanType = discountPlanType;
    }

    public BigDecimal getFixedDiscountPercent() {
        return fixedDiscountPercent;
    }

    public void setFixedDiscountPercent(BigDecimal fixedDiscountPercent) {
        this.fixedDiscountPercent = fixedDiscountPercent;
    }

    public String getFlexibleTiersJson() {
        return flexibleTiersJson;
    }

    public void setFlexibleTiersJson(String flexibleTiersJson) {
        this.flexibleTiersJson = flexibleTiersJson;
    }

    public MerchantStanding getStanding() {
        return standing;
    }

    public void setStanding(MerchantStanding standing) {
        this.standing = standing;
    }

    public BigDecimal getFlexibleDiscountCredit() {
        return flexibleDiscountCredit;
    }

    public void setFlexibleDiscountCredit(BigDecimal flexibleDiscountCredit) {
        this.flexibleDiscountCredit = flexibleDiscountCredit;
    }

    public BigDecimal getChequeRebatePending() {
        return chequeRebatePending;
    }

    public void setChequeRebatePending(BigDecimal chequeRebatePending) {
        this.chequeRebatePending = chequeRebatePending;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public Instant getInDefaultSince() {
        return inDefaultSince;
    }

    public void setInDefaultSince(Instant inDefaultSince) {
        this.inDefaultSince = inDefaultSince;
    }

    public BigDecimal getDebtReminderOutstanding() {
        return debtReminderOutstanding;
    }

    public void setDebtReminderOutstanding(BigDecimal debtReminderOutstanding) {
        this.debtReminderOutstanding = debtReminderOutstanding;
    }
}
