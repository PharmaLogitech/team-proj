/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for merchant financial overview (ORD-US3).          ║
 * ║                                                                              ║
 * ║  ENDPOINTS:                                                                  ║
 * ║    GET /api/merchant-financials/balance                                     ║
 * ║        Returns the logged-in merchant's outstanding balance, currency,     ║
 * ║        oldest unpaid due date, and days elapsed since that date.            ║
 * ║                                                                              ║
 * ║  WHY:  ORD-US3 requires merchants to see "a read-only total of all         ║
 * ║        unpaid invoices" that "automatically updates when new invoices      ║
 * ║        are raised or payments are recorded," plus "amount of time elapsed  ║
 * ║        since payment has been due."                                        ║
 * ║                                                                              ║
 * ║  ACCESS: MERCHANT only (enforced by SecurityConfig + @PreAuthorize).       ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.entity.Invoice;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.PaymentRepository;
import com.ipos.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/merchant-financials")
public class MerchantFinancialsController {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    public MerchantFinancialsController(InvoiceRepository invoiceRepository,
                                        PaymentRepository paymentRepository,
                                        UserRepository userRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
    }

    /**
     * GET /api/merchant-financials/balance — read-only outstanding balance (ORD-US3).
     * Derived from invoices and payments so it auto-updates.
     */
    @GetMapping("/balance")
    @PreAuthorize("hasRole('MERCHANT')")
    public BalanceResponse getBalance(Authentication auth) {
        User caller = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long merchantId = caller.getId();

        BigDecimal totalInvoiced = invoiceRepository.sumTotalDueByMerchantId(merchantId);
        BigDecimal totalPaid = invoiceRepository.sumPaymentsByMerchantId(merchantId);
        BigDecimal outstanding = totalInvoiced.subtract(totalPaid);

        /* Find the oldest unpaid invoice due date for "time elapsed since due." */
        LocalDate oldestUnpaidDueDate = null;
        long daysElapsedSinceDue = 0;

        List<Invoice> invoices = invoiceRepository.findByMerchant_IdOrderByIssuedAtDesc(merchantId);
        for (Invoice inv : invoices) {
            BigDecimal paid = paymentRepository.sumByInvoiceId(inv.getId());
            BigDecimal invOutstanding = inv.getTotalDue().subtract(paid);
            if (invOutstanding.compareTo(BigDecimal.ZERO) > 0) {
                if (oldestUnpaidDueDate == null || inv.getDueDate().isBefore(oldestUnpaidDueDate)) {
                    oldestUnpaidDueDate = inv.getDueDate();
                }
            }
        }

        if (oldestUnpaidDueDate != null && oldestUnpaidDueDate.isBefore(LocalDate.now())) {
            daysElapsedSinceDue = ChronoUnit.DAYS.between(oldestUnpaidDueDate, LocalDate.now());
        }

        return new BalanceResponse(
                outstanding,
                "GBP",
                oldestUnpaidDueDate,
                daysElapsedSinceDue
        );
    }

    record BalanceResponse(
            BigDecimal outstandingTotal,
            String currency,
            LocalDate oldestUnpaidDueDate,
            long daysElapsedSinceDue
    ) {}
}
