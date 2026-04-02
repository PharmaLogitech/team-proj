/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Read-only DTO for the low-stock report (CAT-US10) and the           ║
 * ║        low-stock warning banner (CAT-US9).                                 ║
 * ║                                                                              ║
 * ║  WHY:  US9 acceptance criterion 2 requires "Product ID, Description, and    ║
 * ║        current stock level" in the warning.  This DTO carries exactly that  ║
 * ║        plus the threshold for context, without exposing the full Product    ║
 * ║        entity graph.                                                        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Add fields as needed (e.g. supplierInfo) without changing the        ║
 * ║        entity layer.                                                        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import com.ipos.entity.Product;

public class LowStockProductDto {

    private Long id;
    private String productCode;
    private String description;
    private Integer availabilityCount;
    private Integer minStockThreshold;

    public LowStockProductDto() {
    }

    /**
     * Maps a Product entity to a low-stock report row.
     * Treats null availabilityCount as 0 for display consistency.
     */
    public static LowStockProductDto fromProduct(Product product) {
        LowStockProductDto dto = new LowStockProductDto();
        dto.id = product.getId();
        dto.productCode = product.getProductCode();
        dto.description = product.getDescription();
        Integer rawCount = product.getAvailabilityCount();
        dto.availabilityCount = rawCount != null ? rawCount : 0;
        dto.minStockThreshold = product.getMinStockThreshold();
        return dto;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getDescription() {
        return description;
    }

    public Integer getAvailabilityCount() {
        return availabilityCount;
    }

    public Integer getMinStockThreshold() {
        return minStockThreshold;
    }
}
