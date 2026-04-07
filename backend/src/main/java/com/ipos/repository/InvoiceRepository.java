/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the Invoice entity (ORD-US5).        ║
 * ║                                                                              ║
 * ║  QUERIES:                                                                    ║
 * ║    findByOrder_Id               — 1:1 lookup; prevents duplicate invoices.  ║
 * ║    findByMerchant_Id...         — Merchant-scoped invoice listing.          ║
 * ║    findAll...                   — Staff listing (all invoices).             ║
 * ║    sumTotalDueByMerchantId      — Used for outstanding balance (ORD-US3).  ║
 * ║    sumPaymentsByMerchantId      — Used for outstanding balance (ORD-US3).  ║
 * ║    countByInvoiceNumberPrefix   — Sequential invoice numbering.            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    /** Prevent duplicate invoices for the same order (idempotency guard). */
    Optional<Invoice> findByOrder_Id(Long orderId);

    /** Merchant-scoped listing, newest first (ORD-US5 visibility). */
    List<Invoice> findByMerchant_IdOrderByIssuedAtDesc(Long merchantId);

    /** Staff listing — all invoices, newest first. */
    List<Invoice> findAllByOrderByIssuedAtDesc();

    /**
     * Sum of totalDue across all invoices for a merchant.
     * Used with sumPaymentsByMerchantId to compute outstanding balance (ORD-US3).
     */
    @Query("SELECT COALESCE(SUM(i.totalDue), 0) FROM Invoice i " +
           "WHERE i.merchant.id = :merchantId")
    BigDecimal sumTotalDueByMerchantId(@Param("merchantId") Long merchantId);

    /**
     * Sum of all payments received across all invoices for a merchant.
     * Outstanding = sumTotalDue - sumPayments (ORD-US3).
     * Also used in OrderService.placeOrder to reduce credit-limit exposure (ORD-US1).
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.invoice.merchant.id = :merchantId")
    BigDecimal sumPaymentsByMerchantId(@Param("merchantId") Long merchantId);

    /** Count invoices whose number starts with a given prefix (for sequential numbering). */
    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.invoiceNumber LIKE CONCAT(:prefix, '%')")
    long countByInvoiceNumberPrefix(@Param("prefix") String prefix);
}
