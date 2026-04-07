/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for Invoice and Payment endpoints (ORD-US5/US6).    ║
 * ║                                                                              ║
 * ║  ENDPOINTS:                                                                  ║
 * ║    GET  /api/invoices           Role-scoped listing (ORD-US5).             ║
 * ║         MERCHANT: own invoices.  MANAGER/ADMIN: all invoices.              ║
 * ║    GET  /api/invoices/{id}      Invoice detail with lines (ORD-US5).       ║
 * ║         MERCHANT: own only.  MANAGER/ADMIN: any.                           ║
 * ║    POST /api/invoices/{id}/payments  Record payment (ORD-US6, ADMIN).      ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL:                                                             ║
 * ║    SecurityConfig + @PreAuthorize enforce role restrictions.                ║
 * ║    MERCHANT isolation enforced at service/controller level.                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.entity.Invoice;
import com.ipos.entity.Payment;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.UserRepository;
import com.ipos.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final PaymentService paymentService;

    public InvoiceController(InvoiceRepository invoiceRepository,
                             UserRepository userRepository,
                             PaymentService paymentService) {
        this.invoiceRepository = invoiceRepository;
        this.userRepository = userRepository;
        this.paymentService = paymentService;
    }

    /**
     * GET /api/invoices — role-scoped invoice listing (ORD-US5).
     * MERCHANT sees own invoices; MANAGER/ADMIN see all.
     */
    @GetMapping
    public List<Invoice> list(Authentication auth) {
        User caller = resolveUser(auth);
        if (caller.getRole() == User.Role.MERCHANT) {
            return invoiceRepository.findByMerchant_IdOrderByIssuedAtDesc(caller.getId());
        }
        return invoiceRepository.findAllByOrderByIssuedAtDesc();
    }

    /**
     * GET /api/invoices/{id} — invoice detail with lines (ORD-US5).
     * MERCHANT can only view their own invoices.
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, Authentication auth) {
        User caller = resolveUser(auth);
        Invoice invoice = invoiceRepository.findById(id).orElse(null);
        if (invoice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Invoice not found."));
        }
        if (caller.getRole() == User.Role.MERCHANT
                && !invoice.getMerchant().getId().equals(caller.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied."));
        }
        return ResponseEntity.ok(invoice);
    }

    /**
     * POST /api/invoices/{id}/payments — record payment against invoice (ORD-US6).
     * ADMIN only (SecurityConfig + @PreAuthorize defence-in-depth).
     */
    @PostMapping("/{id}/payments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> recordPayment(@PathVariable Long id,
                                           @RequestBody RecordPaymentRequest request,
                                           Authentication auth) {
        try {
            Payment.PaymentMethod method;
            try {
                method = Payment.PaymentMethod.valueOf(request.method());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error",
                        "Invalid payment method. Must be BANK_TRANSFER, CARD, or CHEQUE."));
            }

            Payment payment = paymentService.recordPayment(
                    id, request.amount(), method, auth.getName());
            return ResponseEntity.status(HttpStatus.CREATED).body(payment);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private User resolveUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Authenticated user not found"));
    }

    /** Payment request payload. */
    record RecordPaymentRequest(BigDecimal amount, String method) {}
}
