package com.ipos.config;

import com.ipos.entity.CatalogueMetadata;
import com.ipos.entity.Product;
import com.ipos.repository.CatalogueMetadataRepository;
import com.ipos.repository.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Backfills legacy {@link Product} rows with a {@code product_code} and ensures
 * {@link CatalogueMetadata} exists when products already exist (CAT-US1 migration).
 */
@Component
@Order(100)
public class CatalogueLifecycleRunner implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final CatalogueMetadataRepository catalogueMetadataRepository;

    public CatalogueLifecycleRunner(ProductRepository productRepository,
                                    CatalogueMetadataRepository catalogueMetadataRepository) {
        this.productRepository = productRepository;
        this.catalogueMetadataRepository = catalogueMetadataRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (Product p : productRepository.findAll()) {
            if (p.getProductCode() == null || p.getProductCode().isBlank()) {
                p.setProductCode("LEGACY-" + p.getId());
                productRepository.save(p);
            }
        }
        if (productRepository.count() > 0
                && !catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)) {
            catalogueMetadataRepository.save(new CatalogueMetadata(Instant.now()));
        }
    }
}
