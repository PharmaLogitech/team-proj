package com.ipos.repository;

import com.ipos.entity.CatalogueMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CatalogueMetadataRepository extends JpaRepository<CatalogueMetadata, Long> {
}
