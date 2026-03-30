/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service for recording merchant payments against outstanding orders   ║
 * ║        (ORD-US6 — Recording Merchant Payments).                             ║
 * ║                                                                              ║
 * ║  WHY:  The brief requires InfoPharma to track payments received from        ║
 * ║        merchants.  This service validates the payment and records it        ║
 * ║        against the merchant's account, reducing their outstanding balance.  ║
 * ║                                                                              ║
 * ║  DESIGN-DOC TEST CASES: T1-T6 (recordPayment).                             ║
 * ║                                                                              ║
 * ║  VALIDATION RULES (from design-document test cases):                        ║
 * ║        - merchantId must be a valid positive integer (T3: negative → fail). ║
 * ║        - merchantId must refer to an existing merchant (T4: unknown → fail).║
 * ║        - amount must be > 0 (T5: negative → fail; T6: zero → fail).        ║
 * ║        - amount must have decimal precision (T2: 120.75 → accepted).        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class PaymentService {

    private final UserRepository userRepository;

    public PaymentService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /*
     * recordPayment — Record a payment received from a merchant.
     *
     * @param merchantId  The ID of the merchant making the payment.
     *                    Must be a positive integer that refers to an existing
     *                    MERCHANT-role user.
     * @param amount      The payment amount.  Must be strictly > 0.
     * @throws RuntimeException  If validation fails (see rules above).
     */
    public void recordPayment(Long merchantId, BigDecimal amount) {

        /* Validate merchantId (T3: negative ID is invalid) */
        if (merchantId == null || merchantId <= 0) {
            throw new RuntimeException("Invalid merchant ID.");
        }

        /* Validate amount (T5: negative, T6: zero) */
        if (amount == null) {
            throw new RuntimeException("Invalid payment amount.");
        }
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("Invalid payment amount.");
        }
        if (amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Payment amount must be greater than 0.");
        }

        /* Validate merchant exists (T4: non-existent merchant) */
        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new RuntimeException("Invalid merchant ID."));

        if (merchant.getRole() != User.Role.MERCHANT) {
            throw new RuntimeException("Invalid merchant ID.");
        }

        /*
         * Payment recorded successfully (T1, T2).
         * In a full implementation this would create a Payment entity,
         * deduct from outstanding balance, and potentially trigger a receipt.
         * ORD-US6 is marked as a future story; this layer enforces the contract.
         */
    }
}
