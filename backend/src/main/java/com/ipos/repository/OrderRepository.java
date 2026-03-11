/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the Order entity.                     ║
 * ║                                                                              ║
 * ║  WHY:  Handles persistence of orders.  Because Order has a @OneToMany        ║
 * ║        with cascade = ALL, saving an Order also saves its OrderItems.        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - List<Order> findByMerchantId(Long merchantId);                     ║
 * ║        - List<Order> findByStatus(Order.OrderStatus status);                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Inherits: save, findById, findAll, deleteById, etc.
}
