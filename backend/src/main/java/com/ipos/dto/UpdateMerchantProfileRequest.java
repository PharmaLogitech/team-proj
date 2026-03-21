/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Request DTO for updating a merchant's profile (ACC-US6, brief §iii). ║
 * ║                                                                              ║
 * ║  WHY:  Managers and Admins can alter a merchant's credit limit, discount    ║
 * ║        plan, and standing.  This DTO carries only the mutable fields.       ║
 * ║        All fields are optional — only non-null fields are applied.          ║
 * ║                                                                              ║
 * ║  STANDING TRANSITIONS (brief §iii):                                          ║
 * ║        Managers may change: IN_DEFAULT → NORMAL, IN_DEFAULT → SUSPENDED.   ║
 * ║        The controller enforces which transitions are valid.                 ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import java.math.BigDecimal;

public record UpdateMerchantProfileRequest(
        BigDecimal creditLimit,
        String discountPlanType,
        BigDecimal fixedDiscountPercent,
        String flexibleTiersJson,
        String standing
) {}
