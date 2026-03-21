/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Spring Data JPA Repository for MonthlyRebateSettlement.              ║
 * ║                                                                              ║
 * ║  WHY:  Supports the month-close settlement workflow (brief §i — flexible    ║
 * ║        discount plan).  The key query is the idempotency check:             ║
 * ║        "has this month already been settled for this merchant?"             ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - findByMerchantId(Long) for settlement history display.            ║
 * ║        - findByYearMonth(String) for batch reporting.                      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.MonthlyRebateSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MonthlyRebateSettlementRepository extends JpaRepository<MonthlyRebateSettlement, Long> {

    /*
     * Idempotency guard: check whether a settlement already exists for a
     * given merchant + month combination before closing.
     */
    Optional<MonthlyRebateSettlement> findByMerchantIdAndYearMonth(Long merchantId, String yearMonth);
}
