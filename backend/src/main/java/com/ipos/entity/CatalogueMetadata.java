package com.ipos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Singleton row (id = 1) marking that the electronic catalogue has been registered (CAT-US1).
 */
@Entity
@Table(name = "catalogue_metadata")
public class CatalogueMetadata {

    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private Instant initializedAt;

    public CatalogueMetadata() {
    }

    public CatalogueMetadata(Instant initializedAt) {
        this.id = SINGLETON_ID;
        this.initializedAt = initializedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getInitializedAt() {
        return initializedAt;
    }

    public void setInitializedAt(Instant initializedAt) {
        this.initializedAt = initializedAt;
    }
}
