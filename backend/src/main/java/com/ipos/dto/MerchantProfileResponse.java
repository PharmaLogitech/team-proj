/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Response DTO for MerchantProfile data.                               ║
 * ║                                                                              ║
 * ║  WHY:  We never return raw entities in API responses:                        ║
 * ║        - Prevents accidental exposure of internal fields.                   ║
 * ║        - Decouples the API shape from the database schema.                  ║
 * ║        - Flattens the User + MerchantProfile relationship for the frontend. ║
 * ║                                                                              ║
 * ║  USED BY:                                                                    ║
 * ║        - MerchantAccountController (POST /api/merchant-accounts response).  ║
 * ║        - MerchantProfileController (GET/PUT /api/merchant-profiles).        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import com.ipos.entity.MerchantProfile;
import java.math.BigDecimal;

public record MerchantProfileResponse(
        Long userId,
        String name,
        String username,
        String contactEmail,
        String contactPhone,
        String addressLine,
        BigDecimal creditLimit,
        String discountPlanType,
        BigDecimal fixedDiscountPercent,
        String flexibleTiersJson,
        String standing,
        BigDecimal flexibleDiscountCredit,
        BigDecimal chequeRebatePending
) {

    /*
     * Factory method: converts MerchantProfile entity → safe API response.
     * Flattens user fields (name, username) alongside profile fields.
     */
    public static MerchantProfileResponse fromEntity(MerchantProfile profile) {
        return new MerchantProfileResponse(
                profile.getUser().getId(),
                profile.getUser().getName(),
                profile.getUser().getUsername(),
                profile.getContactEmail(),
                profile.getContactPhone(),
                profile.getAddressLine(),
                profile.getCreditLimit(),
                profile.getDiscountPlanType().name(),
                profile.getFixedDiscountPercent(),
                profile.getFlexibleTiersJson(),
                profile.getStanding().name(),
                profile.getFlexibleDiscountCredit(),
                profile.getChequeRebatePending()
        );
    }
}
