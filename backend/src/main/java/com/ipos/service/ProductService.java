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
