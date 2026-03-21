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
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - List<Order> findByMerchantId(Long merchantId);                     ║
 * ║        - List<Order> findByStatus(Order.OrderStatus status);                ║
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

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /*
     * ── CREDIT LIMIT CHECK ──────────────────────────────────────────────────
     *
     * Returns the sum of totalDue for all non-cancelled orders belonging to
     * a merchant.  Used by OrderService to verify that placing a new order
     * would not exceed the merchant's credit limit.
     *
     * COALESCE ensures we get 0 (not null) when the merchant has no orders.
     *
     * ASSUMPTION: "outstanding exposure" = sum of totalDue across all
     * non-cancelled orders.  A more sophisticated system would subtract
     * payments received, but payment tracking is out of scope for now.
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
