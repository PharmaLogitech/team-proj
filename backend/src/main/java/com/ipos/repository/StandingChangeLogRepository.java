/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Repository for the StandingChangeLog entity (ACC-US5 audit).    ║
 * ║                                                                              ║
 * ║  WHY:  Provides CRUD operations and custom queries for the audit trail     ║
 * ║        of merchant standing transitions.                                   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.StandingChangeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StandingChangeLogRepository extends JpaRepository<StandingChangeLog, Long> {

    /* Retrieve the full audit history for a specific merchant, newest first. */
    List<StandingChangeLog> findByMerchantIdOrderByChangedAtDesc(Long merchantId);
}
