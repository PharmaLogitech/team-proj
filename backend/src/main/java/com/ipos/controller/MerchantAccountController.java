/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for creating Merchant Accounts (ACC-US1).            ║
 * ║                                                                              ║
 * ║  WHY:  The brief requires that merchant accounts are created with all       ║
 * ║        mandatory fields (contact details, credit limit, discount plan)      ║
 * ║        in a single operation.  "If the required details are not provided    ║
 * ║        the account will not be created."                                    ║
 * ║                                                                              ║
 * ║        This controller is separate from UserController (which handles       ║
 * ║        staff user CRUD) because the merchant creation contract is           ║
 * ║        fundamentally different: it creates both a User and a                ║
 * ║        MerchantProfile atomically.                                          ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL:                                                             ║
 * ║        POST /api/merchant-accounts → ADMIN only.                           ║
 * ║        Enforced in SecurityConfig.java.                                     ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add GET /api/merchant-accounts to list all merchants (if needed   ║
 * ║          separately from /api/merchant-profiles).                           ║
 * ║        - Add DELETE /api/merchant-accounts/{id} for deactivation.          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.dto.CreateMerchantAccountRequest;
import com.ipos.dto.MerchantProfileResponse;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.service.MerchantAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/merchant-accounts")
public class MerchantAccountController {

    private final MerchantAccountService merchantAccountService;

    public MerchantAccountController(MerchantAccountService merchantAccountService) {
        this.merchantAccountService = merchantAccountService;
    }

    /*
     * POST /api/merchant-accounts
     *
     * Creates a new Merchant user + MerchantProfile in one atomic operation.
     *
     * @Valid triggers Jakarta Bean Validation on the request DTO.
     * If any @NotBlank / @NotNull / @DecimalMin constraint fails, Spring
     * returns 400 before this method body runs.
     *
     * The service layer performs additional business validation (duplicate
     * username, plan-specific rules, tier JSON structure).
     */
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreateMerchantAccountRequest request) {
        try {
            DiscountPlanType planType;
            try {
                planType = DiscountPlanType.valueOf(request.planType().toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Invalid plan type: '" + request.planType()
                                + "'. Must be FIXED or FLEXIBLE."));
            }

            MerchantProfile profile = merchantAccountService.createMerchantAccount(
                    request.name(),
                    request.username(),
                    request.password(),
                    request.contactEmail(),
                    request.contactPhone(),
                    request.addressLine(),
                    request.creditLimit(),
                    planType,
                    request.fixedDiscountPercent(),
                    request.flexibleTiersJson()
            );

            return ResponseEntity.ok(MerchantProfileResponse.fromEntity(profile));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
