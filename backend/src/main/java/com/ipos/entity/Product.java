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

    /**
     * Business Product ID (SKU). Stored in UPPERCASE for case-insensitive uniqueness (CAT-US1/US2).
     * May be null only for legacy rows until {@link com.ipos.config.CatalogueLifecycleRunner} backfills.
     */
    @Column(name = "product_code", length = 64, unique = true)
    private String productCode;

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
     * This value is REDUCED inside OrderService when a merchant places an order
     * and INCREASED by stock deliveries (CAT-US7).
     * The @Column annotation lets us customise the database column name.
     */
    @Column(name = "availability_count")
    private Integer availabilityCount;

    /*
     * ── MINIMUM STOCK THRESHOLD (CAT-US8) ────────────────────────────────────
     *
     * The minimum number of units the administrator considers safe to hold.
     * When availabilityCount falls AT OR BELOW this value, the product is
     * considered "low stock" (used by future CAT-US9 warnings).
     *
     * Semantics:
     *   null  = no threshold configured for this product
     *   0     = threshold at zero (flags only when stock hits 0)
     *   N > 0 = flag when availabilityCount <= N
     *
     * Hibernate ddl-auto=update adds this nullable column without touching
     * existing rows; legacy rows will have null (= no threshold set).
     */
    @Column(name = "min_stock_threshold")
    private Integer minStockThreshold;

    public Product() {
    }

    /**
     * @param productCode business Product ID; null allowed only for transient / legacy rows
     */
    public Product(String productCode, String description, BigDecimal price, Integer availabilityCount) {
        this.productCode = productCode;
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

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
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

    public Integer getMinStockThreshold() {
        return minStockThreshold;
    }

    public void setMinStockThreshold(Integer minStockThreshold) {
        this.minStockThreshold = minStockThreshold;
    }
}
