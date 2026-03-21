/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for managing existing Merchant Profiles.             ║
 * ║                                                                              ║
 * ║  WHY (ACC-US6, brief §iii):                                                  ║
 * ║        "A manager's account … can also access the merchant accounts and     ║
 * ║        alter their credit limits, discount plans and change the state of    ║
 * ║        an 'in default' account to either 'normal' or 'suspended'."         ║
 * ║                                                                              ║
 * ║        This controller provides:                                            ║
 * ║          GET  /api/merchant-profiles        — List all merchant profiles.   ║
 * ║          GET  /api/merchant-profiles/{uid}  — Get one profile by user ID.   ║
 * ║          PUT  /api/merchant-profiles/{uid}  — Update profile fields.        ║
 * ║          POST /api/merchant-profiles/close-month — Month-close settlement. ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL:                                                             ║
 * ║        All endpoints → MANAGER or ADMIN.                                   ║
 * ║        Enforced in SecurityConfig.java.                                     ║
 * ║                                                                              ║
 * ║  STANDING TRANSITION RULES (enforced here):                                  ║
 * ║        Managers may only transition: IN_DEFAULT → NORMAL or SUSPENDED.     ║
 * ║        Any other standing change returns 400 Bad Request.                  ║
 * ║        (Admins follow the same rules for consistency; extend later if       ║
 * ║        admins need broader transition powers.)                              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.dto.CloseMonthRequest;
import com.ipos.dto.MerchantProfileResponse;
import com.ipos.dto.UpdateMerchantProfileRequest;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.entity.MonthlyRebateSettlement;
import com.ipos.entity.MonthlyRebateSettlement.SettlementMode;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.service.MerchantAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant-profiles")
public class MerchantProfileController {

    private final MerchantProfileRepository profileRepository;
    private final MerchantAccountService merchantAccountService;

    public MerchantProfileController(MerchantProfileRepository profileRepository,
                                     MerchantAccountService merchantAccountService) {
        this.profileRepository = profileRepository;
        this.merchantAccountService = merchantAccountService;
    }

    /* GET /api/merchant-profiles → List all merchant profiles as DTOs. */
    @GetMapping
    public List<MerchantProfileResponse> findAll() {
        return profileRepository.findAll().stream()
                .map(MerchantProfileResponse::fromEntity)
                .toList();
    }

    /* GET /api/merchant-profiles/{userId} → Get one profile by user ID. */
    @GetMapping("/{userId}")
    public ResponseEntity<?> findByUserId(@PathVariable Long userId) {
        return profileRepository.findByUserId(userId)
                .map(profile -> ResponseEntity.ok(MerchantProfileResponse.fromEntity(profile)))
                .orElse(ResponseEntity.notFound().build());
    }

    /*
     * PUT /api/merchant-profiles/{userId}
     *
     * Updates mutable fields on a merchant's profile.  Only non-null fields
     * in the request body are applied (partial update semantics).
     *
     * STANDING TRANSITION RULES (brief §iii):
     *   Allowed: IN_DEFAULT → NORMAL, IN_DEFAULT → SUSPENDED.
     *   Anything else returns 400.
     */
    @PutMapping("/{userId}")
    public ResponseEntity<?> update(@PathVariable Long userId,
                                    @RequestBody UpdateMerchantProfileRequest request) {
        try {
            MerchantProfile profile = profileRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException(
                            "Merchant profile not found for user " + userId));

            /* ── Credit Limit ─────────────────────────────────────────────── */
            if (request.creditLimit() != null) {
                if (request.creditLimit().compareTo(BigDecimal.ZERO) <= 0) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Credit limit must be greater than zero."));
                }
                profile.setCreditLimit(request.creditLimit());
            }

            /* ── Discount Plan Type + Parameters ──────────────────────────── */
            if (request.discountPlanType() != null) {
                DiscountPlanType newType;
                try {
                    newType = DiscountPlanType.valueOf(request.discountPlanType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error",
                                    "Invalid plan type: '" + request.discountPlanType()
                                    + "'. Must be FIXED or FLEXIBLE."));
                }

                profile.setDiscountPlanType(newType);

                if (newType == DiscountPlanType.FIXED) {
                    if (request.fixedDiscountPercent() == null
                            || request.fixedDiscountPercent().compareTo(BigDecimal.ZERO) < 0
                            || request.fixedDiscountPercent().compareTo(new BigDecimal("100")) > 0) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error",
                                        "Fixed discount percent must be between 0 and 100."));
                    }
                    profile.setFixedDiscountPercent(request.fixedDiscountPercent());
                    profile.setFlexibleTiersJson(null);
                } else {
                    try {
                        merchantAccountService.validateFlexibleTiers(request.flexibleTiersJson());
                    } catch (RuntimeException e) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error", e.getMessage()));
                    }
                    profile.setFlexibleTiersJson(request.flexibleTiersJson());
                    profile.setFixedDiscountPercent(null);
                }
            }

            /* ── Standing Transition (brief §iii) ─────────────────────────── */
            if (request.standing() != null) {
                MerchantStanding newStanding;
                try {
                    newStanding = MerchantStanding.valueOf(request.standing().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error",
                                    "Invalid standing: '" + request.standing()
                                    + "'. Must be NORMAL, IN_DEFAULT, or SUSPENDED."));
                }

                MerchantStanding current = profile.getStanding();

                /*
                 * TRANSITION RULES:
                 *   IN_DEFAULT → NORMAL    ✓  (Manager restores the account)
                 *   IN_DEFAULT → SUSPENDED ✓  (Manager suspends instead of restoring)
                 *   Anything else          ✗  (400 Bad Request)
                 *
                 * This matches the brief §iii: "change the state of an 'in default'
                 * account to either 'normal' or 'suspended'."
                 */
                if (current != newStanding) {
                    if (current != MerchantStanding.IN_DEFAULT) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error",
                                        "Standing can only be changed when the current standing is IN_DEFAULT. "
                                        + "Current standing: " + current + "."));
                    }
                    if (newStanding != MerchantStanding.NORMAL
                            && newStanding != MerchantStanding.SUSPENDED) {
                        return ResponseEntity.badRequest()
                                .body(Map.of("error",
                                        "From IN_DEFAULT, standing can only be changed to NORMAL or SUSPENDED."));
                    }
                    profile.setStanding(newStanding);
                }
            }

            MerchantProfile saved = profileRepository.save(profile);
            return ResponseEntity.ok(MerchantProfileResponse.fromEntity(saved));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /*
     * POST /api/merchant-profiles/close-month
     *
     * Triggers the month-close flexible discount settlement.
     * See MerchantAccountService.closeMonth() for the algorithm.
     */
    @PostMapping("/close-month")
    public ResponseEntity<?> closeMonth(@Valid @RequestBody CloseMonthRequest request) {
        try {
            YearMonth ym;
            try {
                ym = YearMonth.parse(request.yearMonth());
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Invalid year-month format: '" + request.yearMonth()
                                + "'. Expected YYYY-MM."));
            }

            SettlementMode mode;
            try {
                mode = SettlementMode.valueOf(request.settlementMode().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Invalid settlement mode: '" + request.settlementMode()
                                + "'. Must be APPLY_CREDIT or CHEQUE."));
            }

            List<MonthlyRebateSettlement> results = merchantAccountService.closeMonth(ym, mode);

            return ResponseEntity.ok(Map.of(
                    "message", "Month " + request.yearMonth() + " closed successfully.",
                    "settlementsCreated", results.size()
            ));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
