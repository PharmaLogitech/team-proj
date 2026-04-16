/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for recording payments against invoices (ORD-US6).    ║
 * ║                                                                              ║
 * ║  WHY:  Administrators record payments made by merchants.  Each payment     ║
 * ║        applies to a single invoice and must not exceed the invoice's        ║
 * ║        remaining outstanding balance.  Once recorded, the merchant's       ║
 * ║        overall balance (ORD-US3) is updated automatically because it is    ║
 * ║        derived from invoices minus payments.                                ║
 * ║                                                                              ║
 * ║  VALIDATION:                                                                ║
 * ║        - Amount must be > 0.                                                ║
 * ║        - Amount must not exceed invoice outstanding (totalDue - paid so    ║
 * ║          far). This prevents overpayment.                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.Invoice;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.Payment;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.PaymentRepository;
import com.ipos.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final MerchantProfileRepository profileRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          InvoiceRepository invoiceRepository,
                          UserRepository userRepository,
                          MerchantProfileRepository profileRepository) {
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Records a payment against an invoice (ORD-US6).
     *
     * @param invoiceId      the invoice to pay against
     * @param amount         payment amount (must be > 0 and <= outstanding)
     * @param method         BANK_TRANSFER, CARD, or CHEQUE
     * @param adminUsername  the authenticated ADMIN user recording the payment
     * @return the persisted Payment
     */
    @Transactional
    public Payment recordPayment(Long invoiceId, BigDecimal amount,
                                 Payment.PaymentMethod method, String adminUsername) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Invoice not found with id: " + invoiceId));

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment amount must be greater than zero.");
        }

        BigDecimal alreadyPaid = paymentRepository.sumByInvoiceId(invoiceId);
        BigDecimal outstanding = invoice.getTotalDue().subtract(alreadyPaid);

        if (amount.compareTo(outstanding) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Payment amount (" + amount + ") exceeds outstanding balance (" + outstanding + ").");
        }

        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Admin user not found: " + adminUsername));

        Payment payment = new Payment();
        payment.setInvoice(invoice);
        payment.setAmount(amount);
        payment.setMethod(method);
        payment.setRecordedAt(Instant.now());
        payment.setRecordedBy(admin);

        Payment saved = paymentRepository.save(payment);

        /*
         * ACC-US5: Auto-restore to NORMAL if the merchant is IN_DEFAULT and the
         * payment brings their net outstanding balance within the credit limit.
         * Outstanding = total invoiced − total paid (across all invoices).
         */
        profileRepository.findByUserId(invoice.getMerchant().getId()).ifPresent(profile -> {
            if (profile.getStanding() == MerchantProfile.MerchantStanding.IN_DEFAULT) {
                BigDecimal totalInvoiced = invoiceRepository.sumTotalDueByMerchantId(
                        invoice.getMerchant().getId());
                BigDecimal totalPaid = invoiceRepository.sumPaymentsByMerchantId(
                        invoice.getMerchant().getId());
                BigDecimal netOutstanding = totalInvoiced.subtract(totalPaid);
                if (netOutstanding.compareTo(BigDecimal.ZERO) < 0) {
                    netOutstanding = BigDecimal.ZERO;
                }
                if (netOutstanding.compareTo(new BigDecimal("0.01")) < 0) {
                    profile.setStanding(MerchantProfile.MerchantStanding.NORMAL);
                    profile.setInDefaultSince(null);
                    profileRepository.save(profile);
                }
            }
        });

        return saved;
    }
}
