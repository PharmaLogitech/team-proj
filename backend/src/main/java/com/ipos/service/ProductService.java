/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Product-related business logic.                    ║
 * ║                                                                              ║
 * ║  WHY:  Validates catalogue creation rules (CAT-US2), handles product CRUD   ║
 * ║        (CAT-US3/US4), and provides role-aware search (CAT-US5) with stock   ║
 * ║        masking for merchants (CAT-US6).                                     ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add inventory management: restockProduct(Long id, int qty).        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.dto.CatalogueProductDto;
import com.ipos.dto.CreateProductRequest;
import com.ipos.dto.UpdateProductRequest;
import com.ipos.entity.Product;
import com.ipos.entity.ProductDeletionLog;
import com.ipos.entity.User;
import com.ipos.repository.OrderItemRepository;
import com.ipos.repository.ProductDeletionLogRepository;
import com.ipos.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CatalogueService catalogueService;
    private final OrderItemRepository orderItemRepository;
    private final ProductDeletionLogRepository productDeletionLogRepository;

    public ProductService(ProductRepository productRepository,
                          CatalogueService catalogueService,
                          OrderItemRepository orderItemRepository,
                          ProductDeletionLogRepository productDeletionLogRepository) {
        this.productRepository = productRepository;
        this.catalogueService = catalogueService;
        this.orderItemRepository = orderItemRepository;
        this.productDeletionLogRepository = productDeletionLogRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Returns all products mapped to the role-aware catalogue DTO (CAT-US6).
     *
     * @param maskStock true for MERCHANT (hides numeric counts)
     */
    @Transactional(readOnly = true)
    public List<CatalogueProductDto> findAllForCatalogue(boolean maskStock) {
        return productRepository.findAll().stream()
                .map(p -> CatalogueProductDto.fromProduct(p, maskStock))
                .toList();
    }

    /**
     * Combined search with AND-logic across optional filters (CAT-US5).
     * Returns role-aware DTOs with stock masking for merchants (CAT-US6).
     *
     * @throws ResponseStatusException 400 if minPrice > maxPrice
     */
    @Transactional(readOnly = true)
    public List<CatalogueProductDto> searchProducts(String productCode, String q,
                                                     BigDecimal minPrice, BigDecimal maxPrice,
                                                     boolean maskStock) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "minPrice must not exceed maxPrice");
        }
        return productRepository.search(productCode, q, minPrice, maxPrice).stream()
                .map(p -> CatalogueProductDto.fromProduct(p, maskStock))
                .toList();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id));
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

    /**
     * Updates description, price, and availabilityCount of an existing product (CAT-US4).
     * Product code (Product ID) is never changed — immutable per acceptance criteria.
     */
    @Transactional
    public Product updateProduct(Long id, UpdateProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id));
        product.setDescription(request.getDescription().trim());
        product.setPrice(request.getPrice());
        product.setAvailabilityCount(request.getAvailabilityCount());
        return productRepository.save(product);
    }

    /**
     * Hard-deletes a product if no order items reference it (CAT-US3).
     * Logs the deletion (product snapshot + actor) in a single transaction.
     *
     * @throws ResponseStatusException 404 if product not found, 409 if order history exists
     */
    @Transactional
    public void deleteProduct(Long id, User deletedBy) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + id));

        if (orderItemRepository.existsByProduct_Id(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot delete product with existing order history");
        }

        ProductDeletionLog log = new ProductDeletionLog();
        log.setProductIdSnapshot(product.getId());
        log.setProductCodeSnapshot(product.getProductCode());
        log.setDescriptionSnapshot(product.getDescription());
        log.setDeletedBy(deletedBy);
        log.setDeletedAt(Instant.now());
        productDeletionLogRepository.save(log);

        productRepository.delete(product);
    }
}
