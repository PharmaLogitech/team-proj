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

import com.ipos.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // Inherits: save, findById, findAll, deleteById, etc.
}
