/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "orders" table.                          ║
 * ║                                                                              ║
 * ║  WHY:  An Order records that a specific MERCHANT requested specific          ║
 * ║        products.  It has a status (PENDING, CONFIRMED, etc.) and links       ║
 * ║        to the merchant (User) who placed it and the items inside it.         ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a "createdAt" timestamp field with @CreationTimestamp.          ║
 * ║        - Add more statuses to the OrderStatus enum (SHIPPED, DELIVERED…).   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.CascadeType;
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

    public enum OrderStatus {
        PENDING,
        CONFIRMED,
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
}
