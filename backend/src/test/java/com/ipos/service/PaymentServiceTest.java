/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit 5 sub-system tests for PaymentService (IPOS-SA-ORD / ORD-US6).║
 * ║                                                                              ║
 * ║  ROLE:  Sub-System testing — tests the PROVIDED interface of IPOS-SA-ORD   ║
 * ║         for the payment recording functionality.                            ║
 * ║         Per the brief: "Develop jUnit tests for 2 of the methods defined   ║
 * ║         for an interface provided by your own subsystem."                  ║
 * ║                                                                              ║
 * ║  COVERAGE (maps to High-Level Design test cases):                          ║
 * ║    Test  1: merchantID=0001, amount=250      → Success                     ║
 * ║    Test  2: merchantID=5002, amount=120.75   → Success (decimal precision) ║
 * ║    Test  3: merchantID=-999                  → Failure (negative ID)       ║
 * ║    (T3b): amount=500.0, no merchant ID       → Failure (invalid ID)        ║
 * ║    Test  4: merchantID=9a9a (non-numeric)    → Failure (merchant not found)║
 * ║    Test  5: merchantID=0001, amount=-50      → Failure (negative amount)   ║
 * ║    Test  6: merchantID=0001, amount=0        → Failure (zero amount)       ║
 * ║                                                                              ║
 * ║  HOW:  Pure Mockito unit tests — no Spring context, no database.           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private UserRepository userRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(userRepository);
    }

    /* ── Helper: build a MERCHANT user ──────────────────────────────────── */

    private User buildMerchant(Long id) {
        User u = new User("Test Merchant", "merchant" + id, "hash", User.Role.MERCHANT);
        u.setId(id);
        return u;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 1: Valid merchant + integer amount → Success
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 1: Merchant 0001 exists, amount=250 — payment successfully recorded.
     *
     * Design-doc: recordPayment(merchantID=0001, amount=250)
     *   Comment: Merchant 101 exists in the system
     *   Expected: Success. Payment successfully recorded with no exception thrown.
     */
    @Test
    @DisplayName("T1 recordPayment: Valid merchant + amount=250 — no exception thrown")
    void recordPayment_validMerchantAndAmount_noExceptionThrown() {
        Long merchantId = 1L;
        when(userRepository.findById(merchantId)).thenReturn(Optional.of(buildMerchant(merchantId)));

        assertDoesNotThrow(() ->
                paymentService.recordPayment(merchantId, new BigDecimal("250")),
                "Payment should be recorded with no exception");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 2: Valid merchant + decimal amount → Success
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 2: Merchant 5002, amount=120.75 (decimal precision).
     *
     * Design-doc: recordPayment(merchantID=5002, amount=120.75)
     *   Comment: Valid merchant with a decimal payment
     *   Expected: Success. Payment recorded, balance updated with decimal precision.
     */
    @Test
    @DisplayName("T2 recordPayment: Valid merchant + decimal amount=120.75 — recorded with decimal precision")
    void recordPayment_validMerchantAndDecimalAmount_noExceptionThrown() {
        Long merchantId = 5002L;
        when(userRepository.findById(merchantId)).thenReturn(Optional.of(buildMerchant(merchantId)));

        assertDoesNotThrow(() ->
                paymentService.recordPayment(merchantId, new BigDecimal("120.75")),
                "Decimal amount should be accepted and payment recorded");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 3: Negative merchant ID → Failure
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 3: merchantID=-999 — negative ID is invalid.
     *
     * Design-doc: recordPayment(merchantID=-999)
     *   Comment: Negative merchant ID
     *   Expected: Failure. Exception raised ('Invalid merchant ID'). No payment recorded.
     */
    @Test
    @DisplayName("T3 recordPayment: Negative merchantID=-999 — 'Invalid merchant ID' exception")
    void recordPayment_negativeMerchantId_throwsInvalidMerchantId() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                paymentService.recordPayment(-999L, new BigDecimal("500")));

        assertTrue(ex.getMessage().contains("Invalid merchant ID"),
                "Exception must say 'Invalid merchant ID'");
        verify(userRepository, never()).findById(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 3b: No valid merchant ID provided (null) → Failure
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * Design-doc (T3 continuation): amount=500.0, no merchant ID provided
     *   Expected: Failure. Exception raised ('Invalid merchant ID'). No payment recorded.
     */
    @Test
    @DisplayName("T3b recordPayment: Null merchantID — 'Invalid merchant ID' exception")
    void recordPayment_nullMerchantId_throwsInvalidMerchantId() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                paymentService.recordPayment(null, new BigDecimal("500.0")));

        assertTrue(ex.getMessage().contains("Invalid merchant ID"),
                "Exception must say 'Invalid merchant ID'");
        verify(userRepository, never()).findById(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 4: Merchant does not exist → Failure
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 4: merchantID=9999 (does not exist in system) — merchant not found.
     *
     * Design-doc: recordPayment(merchantID=9a9a, amount=250)
     *   Comment: Merchant does not exist
     *   Expected: Failure. Exception raised ('Invalid merchant ID'). No payment recorded.
     *
     * NOTE: "9a9a" in the design represents a non-existent ID.  In our Long-typed
     * service, this is simulated by an ID that is not found in the repository.
     */
    @Test
    @DisplayName("T4 recordPayment: Non-existent merchant — 'Invalid merchant ID' exception")
    void recordPayment_nonExistentMerchant_throwsInvalidMerchantId() {
        Long nonExistentId = 9999L;
        when(userRepository.findById(nonExistentId)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                paymentService.recordPayment(nonExistentId, new BigDecimal("250")));

        assertTrue(ex.getMessage().contains("Invalid merchant ID"),
                "Exception must say 'Invalid merchant ID'");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 5: Negative amount → Failure
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 5: Valid merchant, amount=-50 — negative amount not allowed.
     *
     * Design-doc: recordPayment(merchantID=0001, amount=-50)
     *   Comment: Valid merchant with negative amount
     *   Expected: Failure. Exception raised ('Invalid payment amount'). No payment recorded.
     */
    @Test
    @DisplayName("T5 recordPayment: Negative amount=-50 — 'Invalid payment amount' exception")
    void recordPayment_negativeAmount_throwsInvalidPaymentAmount() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                paymentService.recordPayment(1L, new BigDecimal("-50")));

        assertTrue(ex.getMessage().contains("Invalid payment amount")
                        || ex.getMessage().contains("amount"),
                "Exception must mention invalid payment amount");
        verify(userRepository, never()).findById(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 6: Zero amount → Failure
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 6: Valid merchant, amount=0 — zero amount not allowed.
     *
     * Design-doc: recordPayment(merchantID=0001, amount=0)
     *   Comment: Valid merchant. Zero for amount provided
     *   Expected: Failure. Exception raised ('payment amount must be greater than 0').
     *             No payment recorded.
     */
    @Test
    @DisplayName("T6 recordPayment: Zero amount — 'payment amount must be greater than 0' exception")
    void recordPayment_zeroAmount_throwsPaymentAmountMustBeGreaterThanZero() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                paymentService.recordPayment(1L, BigDecimal.ZERO));

        assertTrue(ex.getMessage().contains("greater than 0"),
                "Exception must say 'payment amount must be greater than 0'");
        verify(userRepository, never()).findById(any());
    }
}
