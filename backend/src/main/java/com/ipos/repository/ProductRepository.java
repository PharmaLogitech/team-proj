/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for the Product entity.                   ║
 * ║                                                                              ║
 * ║  WHY:  Provides all CRUD database operations for products without writing    ║
 * ║        a single line of SQL.  The service layer calls this repository        ║
 * ║        whenever it needs to read or write product data.                      ║
 * ║                                                                              ║
 * ║  SEARCH (CAT-US5/US6):                                                       ║
 * ║        The search() method combines optional filters with AND logic.        ║
 * ║        Null parameters are ignored (each IS NULL check short-circuits).     ║
 * ║                                                                              ║
 * ║  LOW-STOCK QUERY (CAT-US9/US10):                                            ║
 * ║        findLowStockProducts() returns products whose current stock is       ║
 * ║        strictly below their configured threshold (< not ≤), matching the    ║
 * ║        verbatim acceptance criterion in US9. Products without a threshold   ║
 * ║        (minStockThreshold IS NULL) are excluded.                            ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Add query methods by naming convention:                               ║
 * ║          - List<Product> findByAvailabilityCountGreaterThan(int min);        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Product codes are stored in uppercase — pass normalized code. */
    boolean existsByProductCode(String productCode);

    /**
     * Combined search with AND logic across all non-null filters (CAT-US5).
     * Null params are treated as "no filter" via IS NULL short-circuit.
     * Description and productCode use case-insensitive substring (LIKE %...%) matching
     * to support partial-word search (CAT-US6 acceptance criterion 3).
     */
    @Query("""
        SELECT p FROM Product p WHERE
          (:productCode IS NULL OR UPPER(p.productCode) LIKE UPPER(CONCAT('%', :productCode, '%')))
          AND (:q IS NULL OR LOWER(p.description) LIKE LOWER(CONCAT('%', :q, '%')))
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        """)
    List<Product> search(@Param("productCode") String productCode,
                         @Param("q") String q,
                         @Param("minPrice") BigDecimal minPrice,
                         @Param("maxPrice") BigDecimal maxPrice);

    /**
     * Returns products whose current stock is strictly below their configured
     * minimum threshold (CAT-US9/US10). Uses strict {@code <} per the verbatim
     * user-story acceptance criterion: "Current Availability < Threshold".
     *
     * <p>COALESCE handles legacy rows where availabilityCount may be null
     * (treated as 0). Products without a threshold are excluded.
     */
    @Query("""
        SELECT p FROM Product p
         WHERE p.minStockThreshold IS NOT NULL
           AND COALESCE(p.availabilityCount, 0) < p.minStockThreshold
        """)
    List<Product> findLowStockProducts();
}
