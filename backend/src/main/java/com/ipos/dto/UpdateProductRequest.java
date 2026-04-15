package com.ipos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Request body for updating a catalogue product (CAT-US4, CAT-US8).
 * Item ID parts are immutable (same as product code).
 * minStockThreshold: null clears any existing threshold.
 */
public class UpdateProductRequest {

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Package type is required")
    @Size(max = 64)
    private String packageType;

    @Size(max = 32)
    private String unit;

    @NotNull(message = "Units in a pack is required")
    @Min(value = 1, message = "Units in a pack must be at least 1")
    private Integer unitsPerPack;

    @NotNull(message = "Unit price is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "Unit price must be greater than zero")
    private BigDecimal price;

    @NotNull(message = "Availability is required")
    @Min(value = 0, message = "Availability must not be negative")
    private Integer availabilityCount;

    @Min(value = 0, message = "Minimum stock threshold must not be negative")
    private Integer minStockThreshold;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPackageType() {
        return packageType;
    }

    public void setPackageType(String packageType) {
        this.packageType = packageType;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getUnitsPerPack() {
        return unitsPerPack;
    }

    public void setUnitsPerPack(Integer unitsPerPack) {
        this.unitsPerPack = unitsPerPack;
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
