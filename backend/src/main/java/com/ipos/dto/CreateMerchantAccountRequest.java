/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Request DTO for creating a new Merchant Account (ACC-US1).           ║
 * ║                                                                              ║
 * ║  WHY:  The brief requires ALL mandatory fields to be present before the     ║
 * ║        account is created.  Using a dedicated DTO with Jakarta Bean          ║
 * ║        Validation annotations (@NotBlank, @NotNull, etc.) lets Spring       ║
 * ║        reject invalid requests with 400 Bad Request automatically.          ║
 * ║                                                                              ║
 * ║        This DTO is only used for the POST /api/merchant-accounts endpoint.  ║
 * ║                                                                              ║
 * ║  FIELDS:                                                                     ║
 * ║        Credentials  — name, username, password (for User entity).           ║
 * ║        Contact      — contactEmail, contactPhone, addressLine (mandatory).  ║
 * ║        Financial    — creditLimit (must be > 0).                            ║
 * ║        Discount     — planType (FIXED or FLEXIBLE), fixedDiscountPercent    ║
 * ║                       (FIXED only), flexibleTiersJson (FLEXIBLE only).     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateMerchantAccountRequest(

        @NotBlank(message = "Name is required.")
        String name,

        @NotBlank(message = "Username is required.")
        String username,

        @NotBlank(message = "Password is required.")
        String password,

        @NotBlank(message = "Contact email is required.")
        String contactEmail,

        @NotBlank(message = "Contact phone is required.")
        String contactPhone,

        @NotBlank(message = "Address is required.")
        String addressLine,

        @NotNull(message = "Credit limit is required.")
        @DecimalMin(value = "0.01", message = "Credit limit must be greater than zero.")
        BigDecimal creditLimit,

        @NotBlank(message = "Discount plan type is required (FIXED or FLEXIBLE).")
        String planType,

        /*
         * Required for FIXED plan: the discount percentage (0–100).
         * Nullable for FLEXIBLE plan.
         */
        BigDecimal fixedDiscountPercent,

        /*
         * Required for FLEXIBLE plan: JSON array of tier definitions.
         * Nullable for FIXED plan.
         * Example: [{"maxExclusive":1000,"percent":1},{"maxExclusive":2000,"percent":2},{"percent":3}]
         */
        String flexibleTiersJson

) {}
