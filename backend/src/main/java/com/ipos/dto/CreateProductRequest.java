package com.ipos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * Request body for creating a catalogue product (CAT-US2, CAT-US8).
 * Product codes are normalized to uppercase in the service layer for case-insensitive uniqueness.
 * minStockThreshold is optional (null = no threshold configured for this product).
 */
public class CreateProductRequest {

    @NotBlank(message = "Product ID is required")
    private String productCode;

    @NotBlank(message = "Description is required")
    private String description;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Unit price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Availability is required")
    @Min(value = 0, message = "Availability must not be negative")
    private Integer availabilityCount;

    /** Optional minimum stock threshold (CAT-US8). null = no threshold. Must be ≥ 0 when provided. */
    @Min(value = 0, message = "Minimum stock threshold must not be negative")
    private Integer minStockThreshold;

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
