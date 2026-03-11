/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "products" table — the drug catalogue.   ║
 * ║                                                                              ║
 * ║  WHY:  Products are the core of IPOS-SA.  Merchants browse products and     ║
 * ║        place orders against them.  The availabilityCount field tracks how    ║
 * ║        many units are in stock; it decreases when orders are placed.         ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add fields like "manufacturer", "category", or "imageUrl".         ║
 * ║        - Add validation annotations like @NotBlank or @Min(0).              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String description;

    /*
     * We use BigDecimal for monetary values instead of double.
     * Doubles suffer from floating-point rounding errors (e.g., 0.1 + 0.2 != 0.3).
     * BigDecimal stores exact decimal values — critical for prices.
     *
     * precision = total number of digits, scale = digits after the decimal point.
     */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /*
     * How many units are currently available in stock.
     * This value is REDUCED inside OrderService when a merchant places an order.
     * The @Column annotation lets us customise the database column name.
     */
    @Column(name = "availability_count")
    private Integer availabilityCount;

    public Product() {
    }

    public Product(String description, BigDecimal price, Integer availabilityCount) {
        this.description = description;
        this.price = price;
        this.availabilityCount = availabilityCount;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getAvailabilityCount() {
        return availabilityCount;
    }

    public void setAvailabilityCount(Integer availabilityCount) {
        this.availabilityCount = availabilityCount;
    }
}
