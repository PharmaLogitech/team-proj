/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the Payment entity (ORD-US6).        ║
 * ║                                                                              ║
 * ║  QUERIES:                                                                    ║
 * ║    sumByInvoiceId  — Total payments against a single invoice.               ║
 * ║                      Used to calculate remaining outstanding.               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Sum of all payments recorded against a specific invoice. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.invoice.id = :invoiceId")
    BigDecimal sumByInvoiceId(@Param("invoiceId") Long invoiceId);
}
