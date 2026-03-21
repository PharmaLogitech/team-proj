/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Merchant Account operations.                       ║
 * ║                                                                              ║
 * ║  WHY (ACC-US1 — Merchant Account Creation):                                  ║
 * ║        The brief says: "Once a new account is set up as a merchant account  ║
 * ║        the system will ask for contact details to be provided, credit limit ║
 * ║        and discount plan to be set up for the new merchant before the       ║
 * ║        account is activated.  If the required details are not provided the  ║
 * ║        account will not be created."                                        ║
 * ║                                                                              ║
 * ║        This service enforces that contract:                                  ║
 * ║          1. ALL mandatory fields must be present.                           ║
 * ║          2. Plan-specific fields are validated (fixed % for FIXED,          ║
 * ║             valid tier JSON for FLEXIBLE).                                  ║
 * ║          3. User + MerchantProfile are created ATOMICALLY inside a single   ║
 * ║             @Transactional method — if anything fails, neither row exists.  ║
 * ║          4. The account starts with standing = NORMAL (active and able to   ║
 * ║             trade immediately once created).                                ║
 * ║                                                                              ║
 * ║  MONTH-CLOSE SETTLEMENT (brief §i — flexible discount):                      ║
 * ║        closeMonth() computes each FLEXIBLE merchant's rebate for a given    ║
 * ║        calendar month and disburses it as credit or cheque.                 ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL:                                                             ║
 * ║        createMerchantAccount → ADMIN only (enforced by controller/security).║
 * ║        closeMonth            → MANAGER or ADMIN (enforced by controller).   ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - ACC-US5: Add restoreFromDefault() with audit logging.             ║
 * ║        - ACC-US6: Add updateCreditLimit(), updateDiscountPlan().           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.entity.MonthlyRebateSettlement;
import com.ipos.entity.MonthlyRebateSettlement.SettlementMode;
import com.ipos.entity.Order;
import com.ipos.entity.User;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.MonthlyRebateSettlementRepository;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class MerchantAccountService {

    private final UserRepository userRepository;
    private final MerchantProfileRepository profileRepository;
    private final MonthlyRebateSettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public MerchantAccountService(UserRepository userRepository,
                                  MerchantProfileRepository profileRepository,
                                  MonthlyRebateSettlementRepository settlementRepository,
                                  OrderRepository orderRepository,
                                  PasswordEncoder passwordEncoder,
                                  ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  CREATE MERCHANT ACCOUNT (ACC-US1)
     * ══════════════════════════════════════════════════════════════════════════
     *
     * Creates a User (role = MERCHANT) and its MerchantProfile ATOMICALLY.
     *
     * @Transactional ensures that if profile creation fails (e.g. bad tier
     * JSON), the User row is rolled back too.  The brief requires "the
     * account will not be created" if details are missing — this method
     * guarantees that no partial account can exist.
     *
     * @param name               Display name for the user.
     * @param username           Unique login identifier.
     * @param rawPassword        Plaintext password (BCrypt-hashed before storage).
     * @param contactEmail       Mandatory contact email.
     * @param contactPhone       Mandatory contact phone.
     * @param addressLine        Mandatory address.
     * @param creditLimit        Mandatory credit limit (must be > 0).
     * @param planType           FIXED or FLEXIBLE.
     * @param fixedDiscountPercent  Required when planType is FIXED (0–100).
     * @param flexibleTiersJson  Required when planType is FLEXIBLE (valid JSON array).
     * @return The created MerchantProfile (with the linked User).
     */
    @Transactional
    public MerchantProfile createMerchantAccount(String name,
                                                  String username,
                                                  String rawPassword,
                                                  String contactEmail,
                                                  String contactPhone,
                                                  String addressLine,
                                                  BigDecimal creditLimit,
                                                  DiscountPlanType planType,
                                                  BigDecimal fixedDiscountPercent,
                                                  String flexibleTiersJson) {

        /* ── Validate credentials ─────────────────────────────────────────── */

        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username is required.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new RuntimeException("Password is required.");
        }
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username '" + username + "' is already taken.");
        }

        /* ── Validate contact details (ACC-US1: mandatory) ───────────────── */

        if (name == null || name.isBlank()) {
            throw new RuntimeException("Name is required.");
        }
        if (contactEmail == null || contactEmail.isBlank()) {
            throw new RuntimeException("Contact email is required.");
        }
        if (contactPhone == null || contactPhone.isBlank()) {
            throw new RuntimeException("Contact phone is required.");
        }
        if (addressLine == null || addressLine.isBlank()) {
            throw new RuntimeException("Address is required.");
        }

        /* ── Validate credit limit ────────────────────────────────────────── */

        if (creditLimit == null || creditLimit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("Credit limit must be greater than zero.");
        }

        /* ── Validate discount plan ───────────────────────────────────────── */

        if (planType == null) {
            throw new RuntimeException("Discount plan type is required (FIXED or FLEXIBLE).");
        }

        if (planType == DiscountPlanType.FIXED) {
            if (fixedDiscountPercent == null
                    || fixedDiscountPercent.compareTo(BigDecimal.ZERO) < 0
                    || fixedDiscountPercent.compareTo(new BigDecimal("100")) > 0) {
                throw new RuntimeException(
                        "Fixed discount percent must be between 0 and 100.");
            }
        }

        if (planType == DiscountPlanType.FLEXIBLE) {
            validateFlexibleTiers(flexibleTiersJson);
        }

        /* ── Create User ──────────────────────────────────────────────────── */

        User user = new User(
                name,
                username,
                passwordEncoder.encode(rawPassword),
                User.Role.MERCHANT
        );
        user = userRepository.save(user);

        /* ── Create MerchantProfile ───────────────────────────────────────── */

        MerchantProfile profile = new MerchantProfile();
        profile.setUser(user);
        profile.setContactEmail(contactEmail);
        profile.setContactPhone(contactPhone);
        profile.setAddressLine(addressLine);
        profile.setCreditLimit(creditLimit);
        profile.setDiscountPlanType(planType);
        profile.setStanding(MerchantStanding.NORMAL);
        profile.setFlexibleDiscountCredit(BigDecimal.ZERO);
        profile.setChequeRebatePending(BigDecimal.ZERO);

        if (planType == DiscountPlanType.FIXED) {
            profile.setFixedDiscountPercent(fixedDiscountPercent);
            profile.setFlexibleTiersJson(null);
        } else {
            profile.setFixedDiscountPercent(null);
            profile.setFlexibleTiersJson(flexibleTiersJson);
        }

        return profileRepository.save(profile);
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  MONTH-CLOSE FLEXIBLE SETTLEMENT (brief §i)
     * ══════════════════════════════════════════════════════════════════════════
     *
     * For each FLEXIBLE-plan merchant, compute the rebate for the given
     * calendar month and disburse it according to the settlement mode.
     *
     * ALGORITHM:
     *   1. Find all merchants with FLEXIBLE plan.
     *   2. For each, sum grossTotal of non-cancelled orders in [monthStart, nextMonthStart).
     *   3. Determine the applicable tier percentage from the merchant's flexibleTiersJson.
     *   4. computedDiscount = monthlyGross * (tierPercent / 100).
     *   5. If mode is APPLY_CREDIT, add to flexibleDiscountCredit.
     *      If mode is CHEQUE, add to chequeRebatePending.
     *   6. Record a MonthlyRebateSettlement row (idempotent: skip if already settled).
     *
     * @param yearMonth  The calendar month to close (e.g. "2026-03").
     * @param mode       How to disburse the rebate.
     * @return Summary list of settlements created (empty entries for skipped merchants).
     */
    @Transactional
    public List<MonthlyRebateSettlement> closeMonth(YearMonth yearMonth, SettlementMode mode) {

        String ymString = yearMonth.toString(); // "2026-03"
        Instant monthStart = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant nextMonthStart = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<MerchantProfile> flexibleMerchants =
                profileRepository.findByDiscountPlanType(DiscountPlanType.FLEXIBLE);

        List<MonthlyRebateSettlement> results = new ArrayList<>();

        for (MerchantProfile profile : flexibleMerchants) {
            Long merchantId = profile.getUser().getId();

            /* Idempotency: skip if this merchant+month was already settled. */
            if (settlementRepository.findByMerchantIdAndYearMonth(merchantId, ymString).isPresent()) {
                continue;
            }

            /* Sum gross for this merchant during the month. */
            BigDecimal monthlyGross = orderRepository.sumGrossByMerchantAndPeriod(
                    merchantId, monthStart, nextMonthStart, Order.OrderStatus.CANCELLED);

            if (monthlyGross.compareTo(BigDecimal.ZERO) == 0) {
                continue; // no orders this month — nothing to settle
            }

            /* Determine the tier percentage. */
            BigDecimal tierPercent = resolveTierPercent(profile.getFlexibleTiersJson(), monthlyGross);
            BigDecimal discount = monthlyGross.multiply(tierPercent)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            if (discount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            /* Disburse according to the chosen mode. */
            if (mode == SettlementMode.APPLY_CREDIT) {
                profile.setFlexibleDiscountCredit(
                        profile.getFlexibleDiscountCredit().add(discount));
            } else {
                profile.setChequeRebatePending(
                        profile.getChequeRebatePending().add(discount));
            }
            profileRepository.save(profile);

            /* Record the settlement for idempotency and audit. */
            MonthlyRebateSettlement settlement = new MonthlyRebateSettlement();
            settlement.setMerchant(profile.getUser());
            settlement.setYearMonth(ymString);
            settlement.setComputedDiscount(discount);
            settlement.setMode(mode);
            settlement.setSettledAt(Instant.now());
            results.add(settlementRepository.save(settlement));
        }

        return results;
    }

    // ── Internal Helpers ─────────────────────────────────────────────────────

    /*
     * Validates the flexible tiers JSON string.
     *
     * Expected format: array of objects, each with a "percent" (required)
     * and an optional "maxExclusive".  The last tier MUST omit maxExclusive
     * (the catch-all tier for all spend above the previous threshold).
     *
     * Example:
     *   [{"maxExclusive":1000,"percent":1},
     *    {"maxExclusive":2000,"percent":2},
     *    {"percent":3}]
     *
     * Tiers must be ordered by ascending maxExclusive.
     */
    public void validateFlexibleTiers(String json) {
        if (json == null || json.isBlank()) {
            throw new RuntimeException("Flexible tiers JSON is required for FLEXIBLE plan.");
        }

        List<Map<String, Object>> tiers;
        try {
            tiers = objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Invalid flexible tiers JSON: " + e.getMessage());
        }

        if (tiers.isEmpty()) {
            throw new RuntimeException("Flexible tiers must contain at least one tier.");
        }

        BigDecimal prevMax = BigDecimal.ZERO;
        for (int i = 0; i < tiers.size(); i++) {
            Map<String, Object> tier = tiers.get(i);
            if (!tier.containsKey("percent")) {
                throw new RuntimeException("Tier " + (i + 1) + " is missing 'percent'.");
            }

            BigDecimal percent = new BigDecimal(tier.get("percent").toString());
            if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(new BigDecimal("100")) > 0) {
                throw new RuntimeException("Tier " + (i + 1) + " percent must be between 0 and 100.");
            }

            boolean isLast = (i == tiers.size() - 1);
            if (!isLast) {
                if (!tier.containsKey("maxExclusive")) {
                    throw new RuntimeException(
                            "Tier " + (i + 1) + " must have 'maxExclusive' (only the last tier omits it).");
                }
                BigDecimal max = new BigDecimal(tier.get("maxExclusive").toString());
                if (max.compareTo(prevMax) <= 0) {
                    throw new RuntimeException(
                            "Tier " + (i + 1) + " maxExclusive must be greater than previous tier's.");
                }
                prevMax = max;
            } else {
                if (tier.containsKey("maxExclusive")) {
                    throw new RuntimeException(
                            "The last tier must NOT have 'maxExclusive' (it is the catch-all).");
                }
            }
        }
    }

    /*
     * Given a flexible tiers JSON and a gross spend amount, returns the
     * applicable discount percentage.
     *
     * The tiers are checked in order.  The first tier whose maxExclusive
     * is greater than the spend (or the catch-all last tier) wins.
     *
     * Example with tiers [{max:1000, %:1}, {max:2000, %:2}, {%:3}]:
     *   spend=500   → 1%
     *   spend=1500  → 2%
     *   spend=2500  → 3%
     */
    BigDecimal resolveTierPercent(String tiersJson, BigDecimal grossSpend) {
        try {
            List<Map<String, Object>> tiers =
                    objectMapper.readValue(tiersJson, new TypeReference<>() {});

            for (Map<String, Object> tier : tiers) {
                BigDecimal percent = new BigDecimal(tier.get("percent").toString());

                if (!tier.containsKey("maxExclusive")) {
                    return percent; // catch-all (last tier)
                }

                BigDecimal max = new BigDecimal(tier.get("maxExclusive").toString());
                if (grossSpend.compareTo(max) < 0) {
                    return percent;
                }
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse flexible tiers JSON: " + e.getMessage());
        }

        return BigDecimal.ZERO; // safety net — should not happen with valid tiers
    }
}
