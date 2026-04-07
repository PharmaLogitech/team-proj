/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the Order entity.                     ║
 * ║                                                                              ║
 * ║  WHY:  Handles persistence of orders.  Because Order has a @OneToMany        ║
 * ║        with cascade = ALL, saving an Order also saves its OrderItems.        ║
 * ║                                                                              ║
 * ║  QUERIES ADDED FOR ACC-US1 / BRIEF §i:                                       ║
 * ║        sumTotalDueByMerchant — Credit limit enforcement: compute the        ║
 * ║            merchant's total outstanding exposure across non-cancelled orders.║
 * ║        sumGrossByMerchantAndPeriod — Month-close flexible rebate: compute   ║
 * ║            the merchant's total gross spend for a calendar month.           ║
 * ║                                                                              ║
 * ║  ORD-US2 (scoped order listing):                                             ║
 * ║        findByMerchant_IdOrderByPlacedAtDesc — Merchant-scoped listing,     ║
 * ║            newest first.                                                   ║
 * ║        findAllByOrderByPlacedAtDesc — Staff listing (all orders).          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Merchant-scoped listing, newest first (ORD-US2). */
    List<Order> findByMerchant_IdOrderByPlacedAtDesc(Long merchantId);

    /** Staff listing — all orders, newest first (ORD-US2). */
    List<Order> findAllByOrderByPlacedAtDesc();

    /*
     * ── CREDIT LIMIT CHECK (gross component) ───────────────────────────────
     *
     * Returns the sum of totalDue for all non-cancelled orders belonging to
     * a merchant.  OrderService subtracts InvoiceRepository.sumPaymentsByMerchantId
     * to obtain net exposure before comparing to creditLimit (ORD-US6).
     *
     * COALESCE ensures we get 0 (not null) when the merchant has no orders.
     */
    @Query("SELECT COALESCE(SUM(o.totalDue), 0) FROM Order o " +
           "WHERE o.merchant.id = :merchantId AND o.status <> :excludeStatus")
    BigDecimal sumTotalDueByMerchantExcludingStatus(
            @Param("merchantId") Long merchantId,
            @Param("excludeStatus") Order.OrderStatus excludeStatus);

    /*
     * ── MONTHLY GROSS FOR FLEXIBLE SETTLEMENT ────────────────────────────────
     *
     * Returns the sum of grossTotal for a merchant's non-cancelled orders
     * placed within a specific time window [from, to).  Used by the
     * month-close service to determine which flexible discount tier applies.
     *
     * @param from  Start of the calendar month (inclusive), e.g. 2026-03-01T00:00:00Z.
     * @param to    Start of the NEXT month (exclusive), e.g. 2026-04-01T00:00:00Z.
     */
    @Query("SELECT COALESCE(SUM(o.grossTotal), 0) FROM Order o " +
           "WHERE o.merchant.id = :merchantId " +
           "AND o.placedAt >= :from AND o.placedAt < :to " +
           "AND o.status <> :excludeStatus")
    BigDecimal sumGrossByMerchantAndPeriod(
            @Param("merchantId") Long merchantId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeStatus") Order.OrderStatus excludeStatus);
}
