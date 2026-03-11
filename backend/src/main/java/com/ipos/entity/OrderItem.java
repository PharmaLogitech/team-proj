/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "order_items" table — a line item        ║
 * ║        inside an order.                                                      ║
 * ║                                                                              ║
 * ║  WHY:  Orders can contain MULTIPLE products.  Rather than cramming all       ║
 * ║        product IDs into one column, we use a separate table.  Each row       ║
 * ║        says "order #X includes Y units of product #Z."  This is the          ║
 * ║        standard way to model a many-to-many relationship (Order ↔ Product)   ║
 * ║        with extra data (quantity) on the join.                                ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a "priceAtOrder" field to snapshot the price at order time.     ║
 * ║        - Add a "discount" field for promotional pricing.                     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * @ManyToOne — Many order-items belong to one order.
     *
     * @JoinColumn(name = "order_id") — Creates a FOREIGN KEY column "order_id"
     *   in the "order_items" table pointing to the "orders" table's primary key.
     *
     * @JsonBackReference — The "back" side of the JSON serialization pair.
     *   When Jackson serializes an OrderItem, it will SKIP this field to
     *   avoid the infinite loop:  order → items → item.order → items → …
     *   The Order's @JsonManagedReference already includes the items list.
     */
    @ManyToOne
    @JoinColumn(name = "order_id")
    @JsonBackReference
    private Order order;

    /*
     * @ManyToOne — Many order-items can reference the same product.
     *   This creates a FK column "product_id" in "order_items" → "products".
     *
     * This is NOT bidirectional (Product does not have a List<OrderItem>).
     * Bidirectional is only needed when you want to navigate from the
     * "one" side.  For Phase 1 we only navigate from OrderItem → Product.
     */
    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;

    public OrderItem() {
    }

    public OrderItem(Product product, Integer quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
