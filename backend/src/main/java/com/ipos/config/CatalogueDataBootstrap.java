package com.ipos.config;

import com.ipos.dto.CreateProductRequest;
import com.ipos.repository.ProductRepository;
import com.ipos.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds the InfoPharma catalogue from IPOS_SampleData_2026_v1.1m.pdf (page 2) when bootstrap is enabled
 * and the products table is empty — same gate as {@link DataBootstrap}.
 */
@Component
@Order(30)
public class CatalogueDataBootstrap implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final ProductService productService;

    @Value("${ipos.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    public CatalogueDataBootstrap(ProductRepository productRepository, ProductService productService) {
        this.productRepository = productRepository;
        this.productService = productService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            return;
        }
        if (productRepository.count() > 0) {
            System.out.println("[Bootstrap] Catalogue seed skipped — products already exist.");
            return;
        }

        System.out.println("[Bootstrap] Seeding InfoPharma catalogue (PDF sample data, 14 products)...");
        for (PdfRow row : PDF_CATALOGUE) {
            CreateProductRequest req = new CreateProductRequest();
            req.setItemIdRange(row.range);
            req.setItemIdSuffix(row.suffix);
            req.setDescription(row.description);
            req.setPackageType(row.packageType);
            req.setUnit(row.unit);
            req.setUnitsPerPack(row.unitsPerPack);
            req.setPrice(row.price);
            req.setAvailabilityCount(row.availabilityPacks);
            req.setMinStockThreshold(row.stockLimitPacks);
            productService.createProduct(req);
        }
        System.out.println("[Bootstrap] Catalogue seed complete.");
    }

    private record PdfRow(
            String range,
            String suffix,
            String description,
            String packageType,
            String unit,
            int unitsPerPack,
            BigDecimal price,
            int availabilityPacks,
            int stockLimitPacks
    ) {}

    /*
     * IPOS_SampleData_2026_v1.1m — InfoPharma catalogue. OCR fixes: "12, 453" -> 12453; "2,2134" -> 22134.
     */
    private static final PdfRow[] PDF_CATALOGUE = new PdfRow[] {
            new PdfRow("100", "00001", "Paracetamol box", "Caps", null, 20, bd("0.10"), 10345, 300),
            new PdfRow("100", "00002", "Aspirin box", "Caps", null, 20, bd("0.50"), 12453, 500),
            new PdfRow("100", "00003", "Analgin box", "Caps", null, 10, bd("1.20"), 4235, 200),
            new PdfRow("100", "00004", "Celebrex, caps 100 mg box", "Caps", null, 10, bd("10.00"), 3420, 200),
            new PdfRow("100", "00005", "Celebrex, caps 200 mg box", "caps", null, 10, bd("18.50"), 1450, 150),
            new PdfRow("100", "00006", "Retin-A Tretin, 30 g box", "caps", null, 20, bd("25.00"), 2013, 200),
            new PdfRow("100", "00007", "Lipitor TB, 20 mg box", "caps", null, 30, bd("15.50"), 1562, 200),
            new PdfRow("100", "00008", "Claritin CR, 60g box", "caps", null, 20, bd("19.50"), 2540, 200),
            new PdfRow("200", "00004", "Iodine tincture", "bottle", "ml", 100, bd("0.30"), 22134, 200),
            new PdfRow("200", "00005", "Rhynol", "bottle", "ml", 200, bd("2.50"), 1908, 300),
            new PdfRow("300", "00001", "Ospen box", "caps", null, 20, bd("10.50"), 809, 200),
            new PdfRow("300", "00002", "Amopen box", "caps", null, 30, bd("15.00"), 1340, 300),
            new PdfRow("400", "00001", "Vitamin C box", "caps", null, 30, bd("1.20"), 3258, 300),
            new PdfRow("400", "00002", "Vitamin B12 box", "caps", null, 30, bd("1.30"), 2673, 300),
    };

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
