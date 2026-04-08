/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the OrderItem entity.                 ║
 * ║                                                                              ║
 * ║  WHY:  Although OrderItems are cascaded through Order (so we rarely save     ║
 * ║        them directly), having a dedicated repository is useful for queries   ║
 * ║        like "find all items for a specific product" in future phases.        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - List<OrderItem> findByProductId(Long productId);                   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** True if any order line item references the given product (CAT-US3 delete guard). */
    boolean existsByProduct_Id(Long productId);

    /** Sum of line quantities for non-cancelled orders placed in [from, to) (RPT-US1). */
    @Query("SELECT COALESCE(SUM(oi.quantity), 0) FROM OrderItem oi "
            + "WHERE oi.order.placedAt >= :from AND oi.order.placedAt < :to "
            + "AND oi.order.status <> :excludeStatus")
    Long sumQuantityInPeriodExcludingStatus(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeStatus") Order.OrderStatus excludeStatus);

    /**
     * RPT-US5 — Sum line quantities per product for non-cancelled orders placed in [from, to).
     */
    @Query("SELECT oi.product.id AS productId, oi.product.productCode AS productCode, "
            + "COALESCE(SUM(oi.quantity), 0) AS qtySold FROM OrderItem oi "
            + "WHERE oi.order.placedAt >= :from AND oi.order.placedAt < :to "
            + "AND oi.order.status <> :excludeStatus "
            + "GROUP BY oi.product.id, oi.product.productCode")
    List<OrderItemQtySoldProjection> sumQuantitySoldByProductExcludingStatus(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("excludeStatus") Order.OrderStatus excludeStatus);
}
