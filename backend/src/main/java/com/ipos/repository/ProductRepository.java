/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the Product entity.                   ║
 * ║                                                                              ║
 * ║  WHY:  Provides all CRUD database operations for products without writing    ║
 * ║        a single line of SQL.  The service layer calls this repository        ║
 * ║        whenever it needs to read or write product data.                      ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Add query methods by naming convention:                               ║
 * ║          - List<Product> findByDescriptionContaining(String keyword);        ║
 * ║          - List<Product> findByAvailabilityCountGreaterThan(int min);        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    // Inherits: save, findById, findAll, deleteById, count, etc.
}
