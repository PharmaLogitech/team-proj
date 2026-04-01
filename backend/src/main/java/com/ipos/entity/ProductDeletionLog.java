/*
 * JPA Entity for the "product_deletion_logs" table — audit trail for product deletions (CAT-US3).
 *
 * Stores a snapshot of the deleted product's data plus who deleted it and when.
 * Mirrors the pattern used by StandingChangeLog for standing transitions.
 */
package com.ipos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product_deletion_logs")
public class ProductDeletionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id_snapshot", nullable = false)
    private Long productIdSnapshot;

    @Column(name = "product_code_snapshot", length = 64)
    private String productCodeSnapshot;

    @Column(name = "description_snapshot")
    private String descriptionSnapshot;

    @ManyToOne
    @JoinColumn(name = "deleted_by_user_id", nullable = false)
    private User deletedBy;

    @Column(name = "deleted_at", nullable = false)
    private Instant deletedAt;

    public ProductDeletionLog() {
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductIdSnapshot() { return productIdSnapshot; }
    public void setProductIdSnapshot(Long productIdSnapshot) { this.productIdSnapshot = productIdSnapshot; }

    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public void setProductCodeSnapshot(String productCodeSnapshot) { this.productCodeSnapshot = productCodeSnapshot; }

    public String getDescriptionSnapshot() { return descriptionSnapshot; }
    public void setDescriptionSnapshot(String descriptionSnapshot) { this.descriptionSnapshot = descriptionSnapshot; }

    public User getDeletedBy() { return deletedBy; }
    public void setDeletedBy(User deletedBy) { this.deletedBy = deletedBy; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
