/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Invoice operations (ORD-US5).                     ║
 * ║                                                                              ║
 * ║  WHY:  The brief requires an invoice to be generated automatically when    ║
 * ║        an order is marked as "Accepted."  In this codebase, orders are     ║
 * ║        created with ACCEPTED status at placement time, so invoice          ║
 * ║        generation happens in the same @Transactional boundary as the       ║
 * ║        order save.                                                          ║
 * ║                                                                              ║
 * ║  INVOICE CONTENT (per acceptance criteria):                                 ║
 * ║        - Merchant contact details (snapshotted from profile)               ║
 * ║        - Order items with descriptions, quantities, and prices             ║
 * ║        - VAT registration number                                            ║
 * ║        - Final total values (gross, discounts, totalDue)                   ║
 * ║                                                                              ║
 * ║  NUMBERING: INV-YYYY-NNNNN format (sequential per year).                   ║
 * ║                                                                              ║
 * ║  IDEMPOTENCY: Prevents duplicate invoices via unique order_id FK.          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.Invoice;
import com.ipos.entity.InvoiceLine;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.MerchantProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.Optional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository profileRepository;

    public InvoiceService(InvoiceRepository invoiceRepository,
                          MerchantProfileRepository profileRepository) {
        this.invoiceRepository = invoiceRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Generates an invoice for the given order, snapshotting merchant contact
     * details and order line items. Called from OrderService.placeOrder within
     * the same transaction.
     *
     * @return the persisted Invoice, or the existing one if already created
     */
    @Transactional
    public Invoice generateForOrder(Order order) {
        Optional<Invoice> existing = invoiceRepository.findByOrder_Id(order.getId());
        if (existing.isPresent()) {
            return existing.get();
        }

        User merchant = order.getMerchant();
        MerchantProfile profile = profileRepository.findByUserId(merchant.getId())
                .orElseThrow(() -> new RuntimeException(
                        "Merchant profile not found for user " + merchant.getId()));

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setMerchant(merchant);
        invoice.setIssuedAt(Instant.now());

        invoice.setDueDate(LocalDate.now().plusDays(profile.getPaymentTermsDays()));

        invoice.setInvoiceNumber(nextInvoiceNumber());

        /* Snapshot merchant details so historical invoices remain accurate. */
        invoice.setMerchantName(merchant.getName());
        invoice.setMerchantAddress(profile.getAddressLine());
        invoice.setMerchantEmail(profile.getContactEmail());
        invoice.setMerchantPhone(profile.getContactPhone());
        invoice.setMerchantVat(profile.getVatRegistrationNumber());

        /* Snapshot financial totals from the order. */
        invoice.setGrossTotal(order.getGrossTotal());
        invoice.setFixedDiscountAmount(
                order.getFixedDiscountAmount() != null ? order.getFixedDiscountAmount() : BigDecimal.ZERO);
        invoice.setFlexibleCreditApplied(
                order.getFlexibleCreditApplied() != null ? order.getFlexibleCreditApplied() : BigDecimal.ZERO);
        invoice.setTotalDue(order.getTotalDue());

        /* Build invoice line items from order items. */
        int lineNum = 1;
        for (OrderItem oi : order.getItems()) {
            InvoiceLine line = new InvoiceLine();
            line.setInvoice(invoice);
            line.setLineNumber(lineNum++);
            line.setProductId(oi.getProduct() != null ? oi.getProduct().getId() : null);
            line.setDescription(oi.getProduct() != null ? oi.getProduct().getDescription() : "Unknown product");
            line.setQuantity(oi.getQuantity());
            line.setUnitPrice(oi.getUnitPriceAtOrder());
            line.setLineTotal(oi.getUnitPriceAtOrder().multiply(BigDecimal.valueOf(oi.getQuantity())));
            invoice.getLines().add(line);
        }

        return invoiceRepository.save(invoice);
    }

    /**
     * Generates the next sequential invoice number in INV-YYYY-NNNNN format.
     */
    private String nextInvoiceNumber() {
        String yearStr = String.valueOf(Year.now().getValue());
        String prefix = "INV-" + yearStr + "-";
        long count = invoiceRepository.countByInvoiceNumberPrefix(prefix);
        return prefix + String.format("%05d", count + 1);
    }
}
