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
import com.ipos.entity.StandingChangeLog;
import com.ipos.entity.User;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.StandingChangeLogRepository;
import com.ipos.repository.UserRepository;
import com.ipos.service.MerchantAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/merchant-profiles")
public class MerchantProfileController {

    private final MerchantProfileRepository profileRepository;
    private final MerchantAccountService merchantAccountService;
    private final StandingChangeLogRepository standingChangeLogRepository;
    private final UserRepository userRepository;

    public MerchantProfileController(MerchantProfileRepository profileRepository,
                                     MerchantAccountService merchantAccountService,
                                     StandingChangeLogRepository standingChangeLogRepository,
                                     UserRepository userRepository) {
        this.profileRepository = profileRepository;
        this.merchantAccountService = merchantAccountService;
        this.standingChangeLogRepository = standingChangeLogRepository;
        this.userRepository = userRepository;
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
     * STANDING TRANSITION RULES (ACC-US5, brief §iii):
     *   Allowed: IN_DEFAULT → NORMAL, IN_DEFAULT → SUSPENDED.
     *   Only allowed when the account has been in default for >30 days.
     *   Restricted to MANAGER role only (US5: "Manager-level users only").
     *   Every standing change is audit-logged with the actor's identity.
     *
     * CONTACT EDITS (ACC-US6):
     *   Managers can also update contactEmail, contactPhone, addressLine.
     */
    @PutMapping("/{userId}")
    public ResponseEntity<?> update(@PathVariable Long userId,
                                    @RequestBody UpdateMerchantProfileRequest request) {
        try {
            MerchantProfile profile = profileRepository.findByUserId(userId)
                    .orElseThrow(() -> new RuntimeException(
                            "Merchant profile not found for user " + userId));

            /* ── Contact Details (ACC-US6) ────────────────────────────────── */
            if (request.contactEmail() != null && !request.contactEmail().isBlank()) {
                profile.setContactEmail(request.contactEmail());
            }
            if (request.contactPhone() != null && !request.contactPhone().isBlank()) {
                profile.setContactPhone(request.contactPhone());
            }
            if (request.addressLine() != null && !request.addressLine().isBlank()) {
                profile.setAddressLine(request.addressLine());
            }

            /* ── VAT & Payment Terms (ORD-US5/US3) ────────────────────────── */
            if (request.vatRegistrationNumber() != null) {
                profile.setVatRegistrationNumber(request.vatRegistrationNumber());
            }
            if (request.paymentTermsDays() != null && request.paymentTermsDays() > 0) {
                profile.setPaymentTermsDays(request.paymentTermsDays());
            }

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

            /* ── Standing Transition (ACC-US5, brief §iii) ────────────────── */
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

                if (current != newStanding) {
                    /*
                     * ACC-US5: "restrict the Restore action to Manager-level users only."
                     * Standing changes from IN_DEFAULT require MANAGER role.
                     */
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    User caller = userRepository.findByUsername(auth.getName())
                            .orElseThrow(() -> new RuntimeException("Authenticated user not found"));

                    if (current == MerchantStanding.IN_DEFAULT
                            && caller.getRole() != User.Role.MANAGER
                            && caller.getRole() != User.Role.ADMIN) {
                        return ResponseEntity.status(403)
                                .body(Map.of("error",
                                        "Only Managers can restore accounts from IN_DEFAULT."));
                    }

                    /*
                     * TRANSITION RULES:
                     *   IN_DEFAULT → NORMAL    ✓
                     *   IN_DEFAULT → SUSPENDED ✓
                     *   Anything else          ✗
                     */
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

                    /*
                     * ACC-US5: 30-day rule — "long-term payment issue (e.g. longer
                     * than 30 days without payment)."  The Manager can only restore
                     * if the merchant has been in default for at least 30 days.
                     */
                    if (profile.getInDefaultSince() != null) {
                        long daysInDefault = Duration.between(
                                profile.getInDefaultSince(), Instant.now()).toDays();
                        if (daysInDefault < 30) {
                            return ResponseEntity.badRequest()
                                    .body(Map.of("error",
                                            "Account has only been in default for " + daysInDefault
                                            + " days. The minimum is 30 days before the standing can be changed."));
                        }
                    }

                    /* Apply the standing change. */
                    profile.setStanding(newStanding);

                    /* Clear inDefaultSince when leaving IN_DEFAULT. */
                    profile.setInDefaultSince(null);

                    /*
                     * ACC-US5 audit: "The system must log which Manager performed
                     * the status change."
                     */
                    StandingChangeLog log = new StandingChangeLog();
                    log.setMerchant(profile.getUser());
                    log.setPreviousStanding(current);
                    log.setNewStanding(newStanding);
                    log.setChangedBy(caller);
                    log.setChangedAt(Instant.now());
                    standingChangeLogRepository.save(log);
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
