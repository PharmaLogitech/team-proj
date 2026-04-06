/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "orders" table.                          ║
 * ║                                                                              ║
 * ║  WHY:  An Order records that a specific MERCHANT requested specific          ║
 * ║        products.  It has a lifecycle status matching the brief examples      ║
 * ║        (Accepted, Processing, Dispatched, Cancelled) and links to the       ║
 * ║        merchant (User) who placed it and the items inside it.               ║
 * ║                                                                              ║
 * ║  ORDER STATUS LIFECYCLE (ORD-US1/US2):                                      ║
 * ║        ACCEPTED → PROCESSING → DISPATCHED   (forward-only progression)      ║
 * ║        Any non-DISPATCHED status → CANCELLED (cancellation branch)          ║
 * ║        New orders are created with status ACCEPTED.                         ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a "createdAt" timestamp field with @CreationTimestamp.          ║
 * ║        - Add more statuses (DELIVERED, RETURNED, etc.) to OrderStatus.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * ── FOREIGN KEY RELATIONSHIP ─────────────────────────────────────────────
     *
     * @ManyToOne — Defines the "many orders belong to one user" relationship.
     *   In database terms, this creates a FOREIGN KEY column in the "orders"
     *   table that references the PRIMARY KEY of the "users" table.
     *
     * @JoinColumn(name = "merchant_id") — Names the FK column "merchant_id"
     *   in the "orders" table.  Without this, Hibernate would auto-generate
     *   a less readable name like "merchant_id" or "user_id".
     *
     * How FKs work:
     *   A foreign key CONSTRAINT ensures that merchant_id always points to
     *   an existing user.  You cannot insert an order with merchant_id = 999
     *   if no user with id = 999 exists — the database will reject it.
     */
    @ManyToOne
    @JoinColumn(name = "merchant_id")
    private User merchant;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    /*
     * ── PRICING & DISCOUNT FIELDS (ACC-US1 / brief §i) ──────────────────────
     *
     * These fields record the financial breakdown of every order so that
     * totals are auditable and reproducible without recalculation.
     *
     * placedAt              — Timestamp of order creation.  Used by the
     *                         month-close settlement to determine which
     *                         orders fall within a calendar month.
     *
     * grossTotal            — Sum of (unitPriceAtOrder * quantity) for all
     *                         items, BEFORE any discounts.
     *
     * fixedDiscountAmount   — For FIXED-plan merchants: gross * (percent/100).
     *                         For FLEXIBLE-plan merchants: 0.
     *
     * flexibleCreditApplied — For FLEXIBLE-plan merchants: the portion of
     *                         accumulated flexibleDiscountCredit consumed by
     *                         this order (min of credit balance and gross).
     *                         For FIXED-plan merchants: 0.
     *
     * totalDue              — The final amount the merchant owes:
     *                         grossTotal - fixedDiscountAmount - flexibleCreditApplied.
     *
     * NOTE: Existing orders created before these columns were added will
     *       have NULL for these fields.  That is acceptable during development
     *       (ddl-auto=update adds nullable columns).
     */

    @Column(name = "placed_at")
    private Instant placedAt;

    @Column(name = "gross_total", precision = 12, scale = 2)
    private BigDecimal grossTotal;

    @Column(name = "fixed_discount_amount", precision = 12, scale = 2)
    private BigDecimal fixedDiscountAmount;

    @Column(name = "flexible_credit_applied", precision = 12, scale = 2)
    private BigDecimal flexibleCreditApplied;

    @Column(name = "total_due", precision = 12, scale = 2)
    private BigDecimal totalDue;

    /*
     * ── ONE-TO-MANY RELATIONSHIP ─────────────────────────────────────────────
     *
     * @OneToMany — The inverse side of the OrderItem.order relationship.
     *   One order contains MANY order items.
     *
     * mappedBy = "order" — Tells JPA: "don't create a join table; the
     *   foreign key column already lives on the OrderItem side, in the
     *   field named 'order'."
     *
     * cascade = CascadeType.ALL — When we save/delete an Order, JPA
     *   automatically saves/deletes all its OrderItems too.  This means
     *   we only need to call orderRepository.save(order) and the items
     *   are persisted along with it.
     *
     * orphanRemoval = true — If we remove an OrderItem from this list,
     *   JPA will DELETE that row from the database (not just set FK to null).
     *
     * @JsonManagedReference — Prevents infinite JSON recursion.
     *   Order → items → each item.order → items → …  would loop forever.
     *   @JsonManagedReference is the "forward" side (serialized normally).
     *   @JsonBackReference on OrderItem.order is the "back" side (skipped).
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<OrderItem> items = new ArrayList<>();

    /*
     * ── ORDER STATUS ENUM (ORD-US1/US2) ──────────────────────────────────────
     *
     * Lifecycle: ACCEPTED → PROCESSING → DISPATCHED (forward-only).
     * Cancellation: ACCEPTED or PROCESSING → CANCELLED.
     * DISPATCHED orders cannot be cancelled.
     *
     * MIGRATION NOTE: Legacy orders may have PENDING or CONFIRMED from before
     * this enum was updated. Run this SQL once on existing databases:
     *   UPDATE orders SET status = 'ACCEPTED' WHERE status IN ('PENDING','CONFIRMED');
     */
    public enum OrderStatus {
        /** @deprecated Legacy — use ACCEPTED for new orders. Kept for DB compatibility. */
        @Deprecated
        PENDING,
        /** @deprecated Legacy — use ACCEPTED for new orders. Kept for DB compatibility. */
        @Deprecated
        CONFIRMED,
        ACCEPTED,
        PROCESSING,
        DISPATCHED,
        CANCELLED
    }

    public Order() {
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

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    public Instant getPlacedAt() {
        return placedAt;
    }

    public void setPlacedAt(Instant placedAt) {
        this.placedAt = placedAt;
    }

    public BigDecimal getGrossTotal() {
        return grossTotal;
    }

    public void setGrossTotal(BigDecimal grossTotal) {
        this.grossTotal = grossTotal;
    }

    public BigDecimal getFixedDiscountAmount() {
        return fixedDiscountAmount;
    }

    public void setFixedDiscountAmount(BigDecimal fixedDiscountAmount) {
        this.fixedDiscountAmount = fixedDiscountAmount;
    }

    public BigDecimal getFlexibleCreditApplied() {
        return flexibleCreditApplied;
    }

    public void setFlexibleCreditApplied(BigDecimal flexibleCreditApplied) {
        this.flexibleCreditApplied = flexibleCreditApplied;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }

    public void setTotalDue(BigDecimal totalDue) {
        this.totalDue = totalDue;
    }
}
