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
            }
            backfillPdfColumns(p);
            productRepository.save(p);
        }
        if (productRepository.count() > 0
                && !catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)) {
            catalogueMetadataRepository.save(new CatalogueMetadata(Instant.now()));
        }
    }

    /**
     * Split legacy {@code productCode} into Item ID parts; default package/units for pre-PDF rows.
     */
    private static void backfillPdfColumns(Product p) {
        if (p.getItemIdRange() == null && p.getItemIdSuffix() == null && p.getProductCode() != null) {
            String pc = p.getProductCode().trim();
            int dash = pc.indexOf('-');
            if (dash > 0 && dash < pc.length() - 1) {
                p.setItemIdRange(pc.substring(0, dash).trim());
                p.setItemIdSuffix(pc.substring(dash + 1).trim());
            } else if (!pc.isEmpty()) {
                p.setItemIdRange("");
                p.setItemIdSuffix(pc);
            }
        }
        if (p.getPackageType() == null || p.getPackageType().isBlank()) {
            p.setPackageType("\u2014");
        }
        if (p.getUnitsPerPack() == null) {
            p.setUnitsPerPack(1);
        }
    }
}
