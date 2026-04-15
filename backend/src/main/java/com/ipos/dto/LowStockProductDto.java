/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Read-only DTO for the low-stock report (CAT-US10) and the           ║
 * ║        low-stock warning banner (CAT-US9).                                 ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import com.ipos.entity.Product;

public class LowStockProductDto {

    private Long id;
    private String productCode;
    private String itemIdRange;
    private String itemIdSuffix;
    private String description;
    private Integer availabilityCount;
    private Integer minStockThreshold;

    public LowStockProductDto() {
    }

    public static LowStockProductDto fromProduct(Product product) {
        LowStockProductDto dto = new LowStockProductDto();
        dto.id = product.getId();
        dto.productCode = product.getProductCode();
        dto.itemIdRange = product.getItemIdRange();
        dto.itemIdSuffix = product.getItemIdSuffix();
        dto.description = product.getDescription();
        Integer rawCount = product.getAvailabilityCount();
        dto.availabilityCount = rawCount != null ? rawCount : 0;
        dto.minStockThreshold = product.getMinStockThreshold();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getItemIdRange() {
        return itemIdRange;
    }

    public String getItemIdSuffix() {
        return itemIdSuffix;
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
