package com.ipos.service;

import com.ipos.entity.CatalogueMetadata;
import com.ipos.repository.CatalogueMetadataRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Service
public class CatalogueService {

    private final CatalogueMetadataRepository catalogueMetadataRepository;

    public CatalogueService(CatalogueMetadataRepository catalogueMetadataRepository) {
        this.catalogueMetadataRepository = catalogueMetadataRepository;
    }

    /**
     * Explicit catalogue registration (CAT-US1). Fails if already initialized.
     */
    @Transactional
    public void initializeCatalogue() {
        if (catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalogue already initialized");
        }
        catalogueMetadataRepository.save(new CatalogueMetadata(Instant.now()));
    }

    /**
     * Ensures metadata exists after a successful product create (lazy init when no prior initialize call).
     */
    @Transactional
    public void ensureCatalogueMetadataExists() {
        if (!catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)) {
            catalogueMetadataRepository.save(new CatalogueMetadata(Instant.now()));
        }
    }

    public boolean isCatalogueInitialized() {
        return catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID);
    }
}
