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
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add more masked/visible fields as new stories require.             ║
 * ║        - Use fromProduct() factory for consistent mapping from entities.    ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ipos.entity.Product;

import java.math.BigDecimal;

public class CatalogueProductDto {

    private Long id;
    private String productCode;
    private String description;
    private BigDecimal price;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer availabilityCount;

    private String availabilityStatus;

    public CatalogueProductDto() {
    }

    /**
     * Maps a Product entity to the catalogue DTO.
     *
     * @param product   the source entity
     * @param maskStock true for MERCHANT (hide numeric count); false for ADMIN/MANAGER
     */
    public static CatalogueProductDto fromProduct(Product product, boolean maskStock) {
        CatalogueProductDto dto = new CatalogueProductDto();
        dto.id = product.getId();
        dto.productCode = product.getProductCode();
        dto.description = product.getDescription();
        dto.price = product.getPrice();

        if (maskStock) {
            dto.availabilityCount = null;
            dto.availabilityStatus =
                    product.getAvailabilityCount() != null && product.getAvailabilityCount() > 0
                            ? "AVAILABLE" : "OUT_OF_STOCK";
        } else {
            dto.availabilityCount = product.getAvailabilityCount();
            dto.availabilityStatus =
                    product.getAvailabilityCount() != null && product.getAvailabilityCount() > 0
                            ? "AVAILABLE" : "OUT_OF_STOCK";
        }

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

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getAvailabilityCount() {
        return availabilityCount;
    }

    public String getAvailabilityStatus() {
        return availabilityStatus;
    }
}
