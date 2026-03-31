/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Product-related business logic.                    ║
 * ║                                                                              ║
 * ║  WHY:  Validates catalogue creation rules (CAT-US2) and delegates persistence ║
 * ║        to the repository.                                                    ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add search/filter methods: findByKeyword(String keyword).          ║
 * ║        - Add inventory management: restockProduct(Long id, int qty).        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.dto.CreateProductRequest;
import com.ipos.entity.Product;
import com.ipos.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CatalogueService catalogueService;

    public ProductService(ProductRepository productRepository, CatalogueService catalogueService) {
        this.productRepository = productRepository;
        this.catalogueService = catalogueService;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    /**
     * Creates a product with validated, unique business Product ID (CAT-US2).
     */
    @Transactional
    public Product createProduct(CreateProductRequest request) {
        String code = request.getProductCode().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product ID cannot be blank");
        }
        if (productRepository.existsByProductCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product ID already exists: " + code);
        }
        catalogueService.ensureCatalogueMetadataExists();
        Product product = new Product();
        product.setProductCode(code);
        product.setDescription(request.getDescription().trim());
        product.setPrice(request.getPrice());
        product.setAvailabilityCount(request.getAvailabilityCount());
        return productRepository.save(product);
    }
}
