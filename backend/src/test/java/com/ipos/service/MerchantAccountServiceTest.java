/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit 5 unit tests for the IPOS-SA-ACC (Account Management)         ║
 * ║        functionality.                                                       ║
 * ║                                                                              ║
 * ║  WHY:  These tests verify the core business rules introduced by ACC-US1,    ║
 * ║        ACC-US4, the brief's discount plan logic, and the order pipeline     ║
 * ║        enhancements (standing guard, credit limit, ORD-US1 isolation).     ║
 * ║                                                                              ║
 * ║  HOW:  Pure Mockito unit tests — no Spring context, no database.  Each      ║
 * ║        repository is mocked so we control exactly what data the service     ║
 * ║        sees.  This keeps tests fast and deterministic.                      ║
 * ║                                                                              ║
 * ║  COVERAGE:                                                                   ║
 * ║    Tests 1-5:   MerchantAccountService.createMerchantAccount (ACC-US1)     ║
 * ║    Test  6:     UserService rejects MERCHANT role (ACC-US1 guard)          ║
 * ║    Tests 7-10:  Flexible tier JSON validation (brief §i)                   ║
 * ║    Test  11:    Flexible tier resolution (brief §i)                        ║
 * ║    Test  12:    Order placement — FIXED discount calculation               ║
 * ║    Test  13:    Order placement — FLEXIBLE credit consumption              ║
 * ║    Test  14:    Order placement — standing guard (ACC-US5 / brief §iii)    ║
 * ║    Test  15:    Order placement — credit limit enforcement + ORD-US1       ║
 * ║                                                                              ║
 * ║  FUTURE TESTS (commented at bottom of file):                                ║
 * ║    - Controller-layer tests with @WebMvcTest and MockMvc                   ║
 * ║    - Repository integration tests with @DataJpaTest and H2                 ║
 * ║    - Month-close settlement end-to-end tests                               ║
 * ║    - Standing transition tests (Manager PUT endpoint)                      ║
 * ║    - Frontend component tests (React Testing Library)                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.AccountStatus;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.entity.StandingChangeLog;
import com.ipos.entity.User;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.MonthlyRebateSettlementRepository;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/*
 * @ExtendWith(MockitoExtension.class) — Initialises @Mock fields and injects
 * them before each test.  No Spring ApplicationContext is loaded, so these
 * tests start in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class MerchantAccountServiceTest {

    /* ── Mocked dependencies ──────────────────────────────────────────────── */

    @Mock private UserRepository userRepository;
    @Mock private MerchantProfileRepository profileRepository;
    @Mock private MonthlyRebateSettlementRepository settlementRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;

    /*
     * Real instances — these are stateless/pure so mocking adds no value.
     * Using a real PasswordEncoder means we can verify the hash is BCrypt.
     * Using a real ObjectMapper means tier JSON validation is fully exercised.
     */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* ── Services under test ──────────────────────────────────────────────── */

    private MerchantAccountService merchantAccountService;
    private OrderService orderService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        merchantAccountService = new MerchantAccountService(
                userRepository, profileRepository, settlementRepository,
                orderRepository, passwordEncoder, objectMapper);

        orderService = new OrderService(
                orderRepository, productRepository, userRepository, profileRepository);

        userService = new UserService(userRepository, passwordEncoder);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 1-5: MerchantAccountService.createMerchantAccount (ACC-US1)
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 1: Happy path — create a FIXED-plan merchant with all mandatory fields.
     *
     * Verifies:
     *   - A User with role MERCHANT is saved.
     *   - A MerchantProfile is saved with correct contact details, credit limit,
     *     FIXED plan type, and discount percentage.
     *   - Standing starts as NORMAL (account is active immediately).
     *   - The password is BCrypt-hashed (not stored as plaintext).
     */
    @Test
    @DisplayName("ACC-US1: Create merchant with FIXED plan — happy path")
    void createMerchantAccount_fixedPlan_happyPath() {
        when(userRepository.findByUsername("newmerchant")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(10L);
            return u;
        });
        when(profileRepository.save(any(MerchantProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantProfile result = merchantAccountService.createMerchantAccount(
                "Test Merchant", "newmerchant", "pass123",
                "test@example.com", "07700000000", "1 Test Road",
                new BigDecimal("5000.00"),
                DiscountPlanType.FIXED,
                new BigDecimal("5.00"),
                null);

        assertNotNull(result);
        assertEquals("test@example.com", result.getContactEmail());
        assertEquals("07700000000", result.getContactPhone());
        assertEquals("1 Test Road", result.getAddressLine());
        assertEquals(new BigDecimal("5000.00"), result.getCreditLimit());
        assertEquals(DiscountPlanType.FIXED, result.getDiscountPlanType());
        assertEquals(new BigDecimal("5.00"), result.getFixedDiscountPercent());
        assertNull(result.getFlexibleTiersJson());
        assertEquals(MerchantStanding.NORMAL, result.getStanding());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals(User.Role.MERCHANT, savedUser.getRole());
        assertTrue(passwordEncoder.matches("pass123", savedUser.getPasswordHash()),
                "Password should be BCrypt-hashed, not stored as plaintext");
    }

    /*
     * TEST 2: Happy path — create a FLEXIBLE-plan merchant with valid tier JSON.
     *
     * Verifies:
     *   - Tier JSON is stored on the profile.
     *   - fixedDiscountPercent is null for FLEXIBLE plans.
     *   - All other fields are correctly persisted.
     */
    @Test
    @DisplayName("ACC-US1: Create merchant with FLEXIBLE plan — happy path")
    void createMerchantAccount_flexiblePlan_happyPath() {
        String tiersJson = "[{\"maxExclusive\":1000,\"percent\":1},"
                + "{\"maxExclusive\":2000,\"percent\":2},"
                + "{\"percent\":3}]";

        when(userRepository.findByUsername("flexmerchant")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(11L);
            return u;
        });
        when(profileRepository.save(any(MerchantProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantProfile result = merchantAccountService.createMerchantAccount(
                "Flex Merchant", "flexmerchant", "pass456",
                "flex@example.com", "07700000001", "2 Flex Avenue",
                new BigDecimal("10000.00"),
                DiscountPlanType.FLEXIBLE,
                null,
                tiersJson);

        assertEquals(DiscountPlanType.FLEXIBLE, result.getDiscountPlanType());
        assertNull(result.getFixedDiscountPercent());
        assertEquals(tiersJson, result.getFlexibleTiersJson());
        assertEquals(BigDecimal.ZERO, result.getFlexibleDiscountCredit());
        assertEquals(BigDecimal.ZERO, result.getChequeRebatePending());
    }

    /*
     * TEST 3: Blank username is rejected.
     *
     * Verifies the ACC-US1 rule: "account will not be created if any mandatory
     * field is blank."  Tests the service-layer validation (DTO validation
     * catches this first in production, but the service has a second check).
     */
    @Test
    @DisplayName("ACC-US1: Blank username is rejected")
    void createMerchantAccount_blankUsername_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                merchantAccountService.createMerchantAccount(
                        "Name", "", "pass",
                        "e@e.com", "07700", "Addr",
                        new BigDecimal("1000"),
                        DiscountPlanType.FIXED, new BigDecimal("5"), null));

        assertTrue(ex.getMessage().contains("Username is required"));
        verify(userRepository, never()).save(any());
        verify(profileRepository, never()).save(any());
    }

    /*
     * TEST 4: Duplicate username is rejected.
     *
     * Verifies that the system does not create a second user with an
     * existing username, which would violate the unique constraint.
     */
    @Test
    @DisplayName("ACC-US1: Duplicate username is rejected")
    void createMerchantAccount_duplicateUsername_throws() {
        when(userRepository.findByUsername("existing")).thenReturn(Optional.of(new User()));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                merchantAccountService.createMerchantAccount(
                        "Name", "existing", "pass",
                        "e@e.com", "07700", "Addr",
                        new BigDecimal("1000"),
                        DiscountPlanType.FIXED, new BigDecimal("5"), null));

        assertTrue(ex.getMessage().contains("already taken"));
        verify(userRepository, never()).save(any());
    }

    /*
     * TEST 5: Zero credit limit is rejected.
     *
     * Verifies: credit limit must be > 0.  A merchant with a zero or negative
     * credit limit cannot place any orders, so it makes no business sense.
     */
    @Test
    @DisplayName("ACC-US1: Zero credit limit is rejected")
    void createMerchantAccount_zeroCreditLimit_throws() {
        when(userRepository.findByUsername("zeromerchant")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                merchantAccountService.createMerchantAccount(
                        "Name", "zeromerchant", "pass",
                        "e@e.com", "07700", "Addr",
                        BigDecimal.ZERO,
                        DiscountPlanType.FIXED, new BigDecimal("5"), null));

        assertTrue(ex.getMessage().contains("Credit limit must be greater than zero"));
        verify(userRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 6: UserService rejects MERCHANT role (ACC-US1 guard)
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 6: Generic user creation endpoint rejects MERCHANT role.
     *
     * Verifies: UserService.createUser() throws if role=MERCHANT, because
     * merchant accounts MUST be created via MerchantAccountService to ensure
     * the mandatory MerchantProfile is created atomically.
     */
    @Test
    @DisplayName("ACC-US1: UserService.createUser rejects MERCHANT role")
    void userService_createUser_rejectsMerchantRole() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                userService.createUser("Test", "testuser", "pass", User.Role.MERCHANT));

        assertTrue(ex.getMessage().contains("merchant account endpoint"));
        verify(userRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 7-10: Flexible tier JSON validation (brief §i)
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 7: Valid three-tier flexible JSON passes validation.
     *
     * Verifies: standard brief example tiers are accepted without error.
     */
    @Test
    @DisplayName("Brief §i: Valid flexible tiers JSON passes validation")
    void validateFlexibleTiers_validJson_noException() {
        String validJson = "[{\"maxExclusive\":1000,\"percent\":1},"
                + "{\"maxExclusive\":2000,\"percent\":2},"
                + "{\"percent\":3}]";

        assertDoesNotThrow(() -> merchantAccountService.validateFlexibleTiers(validJson));
    }

    /*
     * TEST 8: Null/empty JSON is rejected.
     *
     * Verifies: FLEXIBLE plan requires tier definitions; omitting them is
     * equivalent to missing a mandatory field (ACC-US1: "account will not
     * be created").
     */
    @Test
    @DisplayName("Brief §i: Null flexible tiers JSON is rejected")
    void validateFlexibleTiers_nullJson_throws() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                merchantAccountService.validateFlexibleTiers(null));
        assertTrue(ex.getMessage().contains("Flexible tiers JSON is required"));
    }

    /*
     * TEST 9: Tier missing "percent" field is rejected.
     *
     * Verifies: each tier object must contain a "percent" key.  A tier
     * without a discount percentage is meaningless.
     */
    @Test
    @DisplayName("Brief §i: Tier missing 'percent' is rejected")
    void validateFlexibleTiers_missingPercent_throws() {
        String badJson = "[{\"maxExclusive\":1000}]";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                merchantAccountService.validateFlexibleTiers(badJson));
        assertTrue(ex.getMessage().contains("missing 'percent'"));
    }

    /*
     * TEST 10: Last tier must NOT have maxExclusive (it is the catch-all).
     *
     * Verifies: the final tier in the array acts as the catch-all bracket
     * for all spend amounts above the previous threshold.  If it has a
     * maxExclusive, there's no bracket for spend above that value.
     */
    @Test
    @DisplayName("Brief §i: Last tier with maxExclusive is rejected")
    void validateFlexibleTiers_lastTierHasMax_throws() {
        String badJson = "[{\"maxExclusive\":1000,\"percent\":1},"
                + "{\"maxExclusive\":2000,\"percent\":2}]";

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                merchantAccountService.validateFlexibleTiers(badJson));
        assertTrue(ex.getMessage().contains("last tier must NOT have 'maxExclusive'"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 11: Flexible tier resolution (brief §i)
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 11: resolveTierPercent returns the correct bracket for each spend level.
     *
     * Verifies the tier-matching algorithm against the brief's example:
     *   - <£1000    → 1%
     *   - £1000-£2000 → 2%
     *   - >£2000    → 3% (catch-all)
     *
     * NOTE: resolveTierPercent is package-private, so we can call it directly
     * because this test class is in the same package (com.ipos.service).
     */
    @Test
    @DisplayName("Brief §i: Tier resolution returns correct % for each bracket")
    void resolveTierPercent_returnsCorrectBracket() {
        String tiersJson = "[{\"maxExclusive\":1000,\"percent\":1},"
                + "{\"maxExclusive\":2000,\"percent\":2},"
                + "{\"percent\":3}]";

        assertEquals(new BigDecimal("1"),
                merchantAccountService.resolveTierPercent(tiersJson, new BigDecimal("500")),
                "Spend £500 should fall in the <£1000 bracket (1%)");

        assertEquals(new BigDecimal("2"),
                merchantAccountService.resolveTierPercent(tiersJson, new BigDecimal("1500")),
                "Spend £1500 should fall in the £1000-£2000 bracket (2%)");

        assertEquals(new BigDecimal("3"),
                merchantAccountService.resolveTierPercent(tiersJson, new BigDecimal("5000")),
                "Spend £5000 should fall in the catch-all bracket (3%)");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 12-15: OrderService.placeOrder — discount, standing, credit limit
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * Helper: builds a standard merchant User + MerchantProfile for order tests.
     * Returns the profile so individual tests can customise it (plan type,
     * standing, credit, etc.) before exercising placeOrder().
     */
    private MerchantProfile buildMerchantWithProfile(Long userId, DiscountPlanType planType,
                                                      BigDecimal creditLimit) {
        User merchant = new User("Test Merchant", "merchant1", "hashedpw", User.Role.MERCHANT);
        merchant.setId(userId);

        MerchantProfile profile = new MerchantProfile();
        profile.setId(1L);
        profile.setUser(merchant);
        profile.setContactEmail("m@m.com");
        profile.setContactPhone("07700");
        profile.setAddressLine("1 Test St");
        profile.setCreditLimit(creditLimit);
        profile.setDiscountPlanType(planType);
        profile.setStanding(MerchantStanding.NORMAL);
        profile.setFlexibleDiscountCredit(BigDecimal.ZERO);
        profile.setChequeRebatePending(BigDecimal.ZERO);

        if (planType == DiscountPlanType.FIXED) {
            profile.setFixedDiscountPercent(new BigDecimal("10.00"));
        } else {
            profile.setFlexibleTiersJson(
                    "[{\"maxExclusive\":1000,\"percent\":1},{\"percent\":2}]");
        }

        when(userRepository.findById(userId)).thenReturn(Optional.of(merchant));
        when(profileRepository.findByUserId(userId)).thenReturn(Optional.of(profile));

        return profile;
    }

    /*
     * Helper: builds a product with a given price and stock.
     */
    private Product buildProduct(Long id, BigDecimal price, int stock) {
        Product product = new Product("P-" + id, "Product " + id, price, stock);
        product.setId(id);
        when(productRepository.findById(id)).thenReturn(Optional.of(product));
        return product;
    }

    /*
     * TEST 12: FIXED discount is correctly calculated at order placement.
     *
     * Scenario:
     *   - Merchant has FIXED 10% discount plan.
     *   - Orders 2 x Product A at £50 each → gross = £100.
     *   - Expected: fixedDiscountAmount = £10, totalDue = £90.
     *
     * Verifies: the order's financial fields are set correctly and the
     * discount formula (gross * percent / 100) produces the right result.
     */
    @Test
    @DisplayName("Brief §i FIXED: Discount correctly reduces totalDue")
    void placeOrder_fixedDiscount_correctCalculation() {
        Long merchantId = 20L;
        buildMerchantWithProfile(merchantId, DiscountPlanType.FIXED, new BigDecimal("10000"));
        buildProduct(100L, new BigDecimal("50.00"), 10);

        when(orderRepository.sumTotalDueByMerchantExcludingStatus(
                eq(merchantId), any(Order.OrderStatus.class)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.getProduct().setId(100L);
        item.setQuantity(2);

        Order order = orderService.placeOrder(merchantId, List.of(item), merchantId, User.Role.MERCHANT);

        assertEquals(0, new BigDecimal("100.00").compareTo(order.getGrossTotal()),
                "Gross should be 2 x £50 = £100");
        assertEquals(0, new BigDecimal("10.00").compareTo(order.getFixedDiscountAmount()),
                "10% of £100 = £10 fixed discount");
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getFlexibleCreditApplied()),
                "No flexible credit for FIXED plan");
        assertEquals(0, new BigDecimal("90.00").compareTo(order.getTotalDue()),
                "Total due should be £100 - £10 = £90");
    }

    /*
     * TEST 13: FLEXIBLE plan — accumulated credit is consumed at order placement.
     *
     * Scenario:
     *   - Merchant has FLEXIBLE plan with £30 accumulated credit (from prior
     *     month-close settlement).
     *   - Orders 1 x Product B at £100 → gross = £100.
     *   - Expected: flexibleCreditApplied = £30, totalDue = £70.
     *   - The profile's flexibleDiscountCredit is reduced from £30 to £0.
     *
     * Verifies: the credit consumption formula min(credit, gross) works and
     * the profile balance is correctly decremented.
     */
    @Test
    @DisplayName("Brief §i FLEXIBLE: Accumulated credit consumed at order placement")
    void placeOrder_flexibleCredit_consumed() {
        Long merchantId = 21L;
        MerchantProfile profile = buildMerchantWithProfile(
                merchantId, DiscountPlanType.FLEXIBLE, new BigDecimal("10000"));
        profile.setFlexibleDiscountCredit(new BigDecimal("30.00"));

        buildProduct(101L, new BigDecimal("100.00"), 5);

        when(orderRepository.sumTotalDueByMerchantExcludingStatus(
                eq(merchantId), any(Order.OrderStatus.class)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(profileRepository.save(any(MerchantProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.getProduct().setId(101L);
        item.setQuantity(1);

        Order order = orderService.placeOrder(merchantId, List.of(item), merchantId, User.Role.MERCHANT);

        assertEquals(0, new BigDecimal("100.00").compareTo(order.getGrossTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(order.getFixedDiscountAmount()),
                "FLEXIBLE plan should not apply fixed discount");
        assertEquals(0, new BigDecimal("30.00").compareTo(order.getFlexibleCreditApplied()),
                "Should consume the full £30 credit balance");
        assertEquals(0, new BigDecimal("70.00").compareTo(order.getTotalDue()),
                "Total due = £100 gross - £30 credit = £70");

        assertEquals(0, BigDecimal.ZERO.compareTo(profile.getFlexibleDiscountCredit()),
                "Profile credit balance should be drained to zero");
    }

    /*
     * TEST 14: Orders are blocked when merchant standing is IN_DEFAULT.
     *
     * Verifies the brief §iii requirement: merchants who are in default
     * cannot place orders.  The same applies to SUSPENDED standing.
     */
    @Test
    @DisplayName("Brief §iii: IN_DEFAULT merchant is blocked from placing orders")
    void placeOrder_inDefaultStanding_blocked() {
        Long merchantId = 22L;
        MerchantProfile profile = buildMerchantWithProfile(
                merchantId, DiscountPlanType.FIXED, new BigDecimal("10000"));
        profile.setStanding(MerchantStanding.IN_DEFAULT);

        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.getProduct().setId(200L);
        item.setQuantity(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.placeOrder(merchantId, List.of(item), merchantId, User.Role.MERCHANT));

        assertTrue(ex.getMessage().contains("IN_DEFAULT"),
                "Error message should mention the standing state");
        verify(orderRepository, never()).save(any());
    }

    /*
     * TEST 15: Credit limit enforcement AND ORD-US1 merchant isolation.
     *
     * This test verifies TWO things at once:
     *
     *   a) ORD-US1: When a MERCHANT places an order, the merchantId is forced
     *      to their own callerUserId.  Even if a different merchantId is passed
     *      in the request, the service overrides it.
     *
     *   b) Credit limit: If the new order's totalDue would push the merchant's
     *      total outstanding exposure above their creditLimit, the order is
     *      rejected.
     *
     * Scenario:
     *   - Merchant (userId=23) has credit limit of £500.
     *   - Existing outstanding: £450.
     *   - New order gross: £200, after 10% discount → totalDue = £180.
     *   - New exposure: £450 + £180 = £630 > £500 → REJECTED.
     *   - The merchant attempts to pass merchantId=999 (another merchant),
     *     but ORD-US1 forces it to 23.
     */
    @Test
    @DisplayName("ORD-US1 + Credit Limit: Merchant isolated + over-limit rejected")
    void placeOrder_merchantIsolation_andCreditLimitExceeded() {
        Long realMerchantId = 23L;
        buildMerchantWithProfile(realMerchantId, DiscountPlanType.FIXED, new BigDecimal("500.00"));
        buildProduct(102L, new BigDecimal("100.00"), 10);

        when(orderRepository.sumTotalDueByMerchantExcludingStatus(
                eq(realMerchantId), any(Order.OrderStatus.class)))
                .thenReturn(new BigDecimal("450.00"));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.getProduct().setId(102L);
        item.setQuantity(2);

        /*
         * Pass merchantId=999 in the request, but callerUserId=23 with
         * role=MERCHANT.  ORD-US1 forces resolution to 23.
         */
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.placeOrder(999L, List.of(item), realMerchantId, User.Role.MERCHANT));

        assertTrue(ex.getMessage().contains("credit limit"),
                "Should fail due to credit limit, not 'merchant not found' — proving isolation worked");
        verify(orderRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 16-20: US1 accountStatus, US5 inDefaultSince, StandingChangeLog
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 16: Created merchant accounts have accountStatus = ACTIVE.
     *
     * Verifies: ACC-US1 acceptance criterion — "Once all details are saved,
     * account status should update to Active."
     */
    @Test
    @DisplayName("ACC-US1: Newly created merchant has accountStatus = ACTIVE")
    void createMerchantAccount_setsAccountStatusActive() {
        when(userRepository.findByUsername("statustest")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(30L);
            return u;
        });
        when(profileRepository.save(any(MerchantProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MerchantProfile result = merchantAccountService.createMerchantAccount(
                "Status Test", "statustest", "pass123",
                "s@e.com", "07700", "1 Street",
                new BigDecimal("5000.00"),
                DiscountPlanType.FIXED, new BigDecimal("5.00"), null);

        assertEquals(AccountStatus.ACTIVE, result.getAccountStatus(),
                "Newly created account must have ACTIVE status per ACC-US1");
    }

    /*
     * TEST 17: MerchantProfile.inDefaultSince tracks when standing became IN_DEFAULT.
     *
     * Verifies: the field can be set and read back, and is used by the controller
     * to enforce the 30-day rule (ACC-US5).
     */
    @Test
    @DisplayName("ACC-US5: inDefaultSince records when merchant entered IN_DEFAULT")
    void merchantProfile_inDefaultSince_tracksDefaultStart() {
        MerchantProfile profile = new MerchantProfile();
        Instant defaultStart = Instant.now().minus(45, ChronoUnit.DAYS);
        profile.setInDefaultSince(defaultStart);
        profile.setStanding(MerchantStanding.IN_DEFAULT);

        assertEquals(defaultStart, profile.getInDefaultSince());
        assertEquals(MerchantStanding.IN_DEFAULT, profile.getStanding());

        profile.setStanding(MerchantStanding.NORMAL);
        profile.setInDefaultSince(null);
        assertNull(profile.getInDefaultSince(),
                "inDefaultSince should be cleared when leaving IN_DEFAULT");
    }

    /*
     * TEST 18: StandingChangeLog entity correctly records audit fields.
     *
     * Verifies: the audit entity stores merchant, previous/new standing,
     * the actor who made the change, and the timestamp (ACC-US5: "log which
     * Manager performed the status change").
     */
    @Test
    @DisplayName("ACC-US5: StandingChangeLog records all audit fields")
    void standingChangeLog_recordsAllFields() {
        User merchant = new User("Merchant", "m1", "hash", User.Role.MERCHANT);
        merchant.setId(40L);
        User manager = new User("Manager", "mgr", "hash", User.Role.MANAGER);
        manager.setId(41L);

        StandingChangeLog log = new StandingChangeLog();
        log.setMerchant(merchant);
        log.setPreviousStanding(MerchantStanding.IN_DEFAULT);
        log.setNewStanding(MerchantStanding.NORMAL);
        log.setChangedBy(manager);
        Instant now = Instant.now();
        log.setChangedAt(now);

        assertEquals(merchant, log.getMerchant());
        assertEquals(MerchantStanding.IN_DEFAULT, log.getPreviousStanding());
        assertEquals(MerchantStanding.NORMAL, log.getNewStanding());
        assertEquals(manager, log.getChangedBy());
        assertEquals(now, log.getChangedAt());
        assertEquals(User.Role.MANAGER, log.getChangedBy().getRole(),
                "Audit must record the MANAGER who performed the change");
    }

    /*
     * TEST 19: SUSPENDED merchant is blocked from placing orders (like IN_DEFAULT).
     *
     * Verifies: both non-NORMAL standings are blocked by OrderService, not just
     * IN_DEFAULT (complements test 14 which tested IN_DEFAULT only).
     */
    @Test
    @DisplayName("ACC-US5: SUSPENDED merchant is also blocked from placing orders")
    void placeOrder_suspendedStanding_blocked() {
        Long merchantId = 50L;
        MerchantProfile profile = buildMerchantWithProfile(
                merchantId, DiscountPlanType.FIXED, new BigDecimal("10000"));
        profile.setStanding(MerchantStanding.SUSPENDED);

        OrderItem item = new OrderItem();
        item.setProduct(new Product());
        item.getProduct().setId(300L);
        item.setQuantity(1);

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                orderService.placeOrder(merchantId, List.of(item), merchantId, User.Role.MERCHANT));

        assertTrue(ex.getMessage().contains("SUSPENDED"),
                "Error message should mention SUSPENDED standing");
        verify(orderRepository, never()).save(any());
    }

    /*
     * TEST 20: AccountStatus enum has exactly INACTIVE and ACTIVE values.
     *
     * Verifies: the US1 lifecycle states exist in the enum and cover both
     * acceptance criteria states.
     */
    @Test
    @DisplayName("ACC-US1: AccountStatus enum contains INACTIVE and ACTIVE")
    void accountStatus_enumValues() {
        AccountStatus[] values = AccountStatus.values();
        assertEquals(2, values.length);
        assertEquals(AccountStatus.INACTIVE, AccountStatus.valueOf("INACTIVE"));
        assertEquals(AccountStatus.ACTIVE, AccountStatus.valueOf("ACTIVE"));
    }
}

/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  FUTURE TESTS — to be implemented as later user stories are completed      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 *
 * The following tests will need to be added when the corresponding features
 * are built or when deeper coverage is required:
 *
 * ── Controller-layer tests (@WebMvcTest + MockMvc) ───────────────────────────
 *
 *   1. POST /api/merchant-accounts — returns 400 when @Valid DTO fails
 *      (e.g. blank name, null creditLimit).  Confirms that Jakarta Bean
 *      Validation is wired correctly at the controller level.
 *
 *   2. POST /api/merchant-accounts — returns 403 for MANAGER or MERCHANT
 *      callers.  Confirms SecurityConfig URL rule: ADMIN-only.
 *
 *   3. GET /api/merchant-profiles — returns 403 for MERCHANT callers.
 *      Confirms MANAGER/ADMIN-only access to the merchant management page.
 *
 *   4. PUT /api/merchant-profiles/{userId} — standing transition rules:
 *        a) IN_DEFAULT → NORMAL  (allowed, returns 200)
 *        b) IN_DEFAULT → SUSPENDED (allowed, returns 200)
 *        c) NORMAL → SUSPENDED   (rejected, returns 400)
 *        d) SUSPENDED → NORMAL   (rejected, returns 400)
 *      Confirms MerchantProfileController enforces the brief's constraints.
 *
 *   5. POST /api/merchant-profiles/close-month — returns 200 with settlement
 *      summaries for FLEXIBLE merchants, and is idempotent (second call for
 *      same month returns empty).
 *
 * ── Repository integration tests (@DataJpaTest + H2) ─────────────────────────
 *
 *   6. OrderRepository.sumTotalDueByMerchantExcludingStatus — verify the JPQL
 *      correctly sums non-cancelled orders and excludes cancelled ones.
 *
 *   7. OrderRepository.sumGrossByMerchantAndPeriod — verify the date-range
 *      filtering works correctly for month-close settlement.
 *
 *   8. MerchantProfileRepository.findByDiscountPlanType — verify it returns
 *      only FLEXIBLE (or FIXED) merchants as expected.
 *
 *   9. MonthlyRebateSettlementRepository unique constraint — verify that
 *      inserting a duplicate (merchantId, yearMonth) throws a constraint
 *      violation, enforcing idempotency at the database level.
 *
 * ── End-to-end / integration tests ───────────────────────────────────────────
 *
 *  10. Full month-close settlement flow: create a FLEXIBLE merchant, place
 *      several orders across a month, run closeMonth(), verify the computed
 *      discount matches the tier bracket, and verify that the next order
 *      consumes the credit.
 *
 *  11. Atomic rollback on account creation failure: provide valid user data
 *      but invalid tier JSON, and verify that NO User row was persisted
 *      (confirming @Transactional rollback).
 *
 * ── Frontend tests (React Testing Library / Vitest) ──────────────────────────
 *
 *  12. MerchantCreate.jsx: form submission with all fields populated sends
 *      correct payload to POST /api/merchant-accounts.
 *
 *  13. MerchantManagement.jsx: inline edit + save sends correct payload to
 *      PUT /api/merchant-profiles/{userId}.
 *
 *  14. rbac.js: MERCHANT role cannot access "accounts" or "merchants" routes;
 *      MANAGER can access "merchants" but not "accounts."
 *
 *  15. OrderForm.jsx: after successful order, the financial breakdown
 *      (grossTotal, discount, totalDue) is displayed correctly.
 */
