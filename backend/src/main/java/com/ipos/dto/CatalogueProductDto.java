/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Read-only DTO returned by GET /api/products and GET /api/products/   ║
 * ║        search — the catalogue view seen by all roles.                       ║
 * ║                                                                              ║
 * ║  WHY:  CAT-US6 requires that merchants never see numeric stock counts.      ║
 * ║        Returning the raw Product entity would expose availabilityCount in    ║
 * ║        the JSON response.  This DTO conditionally includes the count for    ║
 * ║        ADMIN/MANAGER and omits it (null → absent in JSON) for MERCHANT,     ║
 * ║        replacing it with a human-readable availabilityStatus string.        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ipos.entity.Product;

import java.math.BigDecimal;

public class CatalogueProductDto {

    private Long id;
    private String productCode;
    private String itemIdRange;
    private String itemIdSuffix;
    private String description;
    private String packageType;
    private String unit;
    private Integer unitsPerPack;
    private BigDecimal price;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer availabilityCount;

    private String availabilityStatus;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer minStockThreshold;

    public CatalogueProductDto() {
    }

    public static CatalogueProductDto fromProduct(Product product, boolean maskStock) {
        CatalogueProductDto dto = new CatalogueProductDto();
        dto.id = product.getId();
        dto.productCode = product.getProductCode();
        dto.itemIdRange = product.getItemIdRange();
        dto.itemIdSuffix = product.getItemIdSuffix();
        dto.description = product.getDescription();
        dto.packageType = product.getPackageType();
        dto.unit = product.getUnit();
        dto.unitsPerPack = product.getUnitsPerPack();
        dto.price = product.getPrice();

        if (maskStock) {
            dto.availabilityCount = null;
            dto.availabilityStatus =
                    product.getAvailabilityCount() != null && product.getAvailabilityCount() > 0
                            ? "AVAILABLE" : "OUT_OF_STOCK";
            dto.minStockThreshold = null;
        } else {
            dto.availabilityCount = product.getAvailabilityCount();
            dto.availabilityStatus =
                    product.getAvailabilityCount() != null && product.getAvailabilityCount() > 0
                            ? "AVAILABLE" : "OUT_OF_STOCK";
            dto.minStockThreshold = product.getMinStockThreshold();
        }

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

    public String getPackageType() {
        return packageType;
    }

    public String getUnit() {
        return unit;
    }

    public Integer getUnitsPerPack() {
        return unitsPerPack;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getAvailabilityCount() {
        return availabilityCount;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }

    public Integer getMinStockThreshold() {
        return minStockThreshold;
    }
}
