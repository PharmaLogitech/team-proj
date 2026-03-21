/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the MerchantProfile entity.           ║
 * ║                                                                              ║
 * ║  WHY:  Provides database access for merchant profile operations:            ║
 * ║        - ACC-US1: Lookup after merchant account creation.                   ║
 * ║        - ACC-US6: Manager edits credit limit, discount plan, standing.     ║
 * ║        - OrderService: Resolve profile to apply discount rules.            ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - findByStanding(MerchantStanding) to list defaulted merchants.     ║
 * ║        - findByDiscountPlanType(DiscountPlanType) for settlement queries.  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.MerchantProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, Long> {

    /*
     * Lookup a merchant's profile by their User ID.
     * Spring Data JPA traverses the "user" property and matches on user.id.
     *
     * USED BY:
     *   - MerchantAccountService — to verify profile exists after creation.
     *   - OrderService — to load discount plan and standing before placing orders.
     *   - MerchantProfileController — to fetch/update a specific merchant.
     */
    Optional<MerchantProfile> findByUserId(Long userId);

    /*
     * Find all merchants with a specific discount plan type.
     * Used by the month-close settlement to process only FLEXIBLE merchants.
     */
    List<MerchantProfile> findByDiscountPlanType(MerchantProfile.DiscountPlanType planType);
}
