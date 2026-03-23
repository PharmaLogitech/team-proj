/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JPA Entity representing the "standing_change_logs" table — an        ║
 * ║        audit trail of every merchant standing transition.                   ║
 * ║                                                                              ║
 * ║  WHY (ACC-US5 — Managing Defaulted Accounts):                                ║
 * ║        "The system must log which Manager performed the status change."     ║
 * ║                                                                              ║
 * ║        Every time a Manager (or Admin) changes a merchant's standing        ║
 * ║        (e.g. IN_DEFAULT → NORMAL), a row is written here recording:         ║
 * ║          - Which merchant was affected.                                     ║
 * ║          - What the standing was BEFORE and AFTER.                          ║
 * ║          - WHO performed the change (the authenticated user).              ║
 * ║          - WHEN the change happened.                                        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a "reason" text field for optional Manager notes.             ║
 * ║        - Expose a GET endpoint for compliance dashboards.                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "standing_change_logs")
public class StandingChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* The merchant whose standing was changed. */
    @ManyToOne
    @JoinColumn(name = "merchant_id", nullable = false)
    private User merchant;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_standing", nullable = false)
    private MerchantProfile.MerchantStanding previousStanding;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_standing", nullable = false)
    private MerchantProfile.MerchantStanding newStanding;

    /* The Manager or Admin who performed the change. */
    @ManyToOne
    @JoinColumn(name = "changed_by_user_id", nullable = false)
    private User changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    public StandingChangeLog() {
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getMerchant() { return merchant; }
    public void setMerchant(User merchant) { this.merchant = merchant; }

    public MerchantProfile.MerchantStanding getPreviousStanding() { return previousStanding; }
    public void setPreviousStanding(MerchantProfile.MerchantStanding previousStanding) { this.previousStanding = previousStanding; }

    public MerchantProfile.MerchantStanding getNewStanding() { return newStanding; }
    public void setNewStanding(MerchantProfile.MerchantStanding newStanding) { this.newStanding = newStanding; }

    public User getChangedBy() { return changedBy; }
    public void setChangedBy(User changedBy) { this.changedBy = changedBy; }

    public Instant getChangedAt() { return changedAt; }
    public void setChangedAt(Instant changedAt) { this.changedAt = changedAt; }
}
