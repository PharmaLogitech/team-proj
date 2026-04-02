/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Product-related business logic.                    ║
 * ║                                                                              ║
 * ║  WHY:  Validates catalogue creation rules (CAT-US2), handles product CRUD   ║
 * ║        (CAT-US3/US4), provides role-aware search (CAT-US5) with stock       ║
 * ║        masking for merchants (CAT-US6), and exposes a low-stock query       ║
 * ║        for the report (CAT-US10) and admin warning banner (CAT-US9).        ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add inventory management: recordStockDelivery already done (US7).  ║
 * ║        - Add stock-take / adjustment endpoints as needed.                   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.dto.CatalogueProductDto;
import com.ipos.dto.CreateProductRequest;
import com.ipos.dto.LowStockProductDto;
import com.ipos.dto.RecordStockDeliveryRequest;
import com.ipos.dto.StockDeliveryResponse;
import com.ipos.dto.UpdateProductRequest;
import com.ipos.entity.Product;
import com.ipos.entity.ProductDeletionLog;
import com.ipos.entity.StockDelivery;
import com.ipos.entity.User;
import com.ipos.repository.OrderItemRepository;
import com.ipos.repository.ProductDeletionLogRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.StockDeliveryRepository;
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
    private final StockDeliveryRepository stockDeliveryRepository;

    public ProductService(ProductRepository productRepository,
                          CatalogueService catalogueService,
                          OrderItemRepository orderItemRepository,
                          ProductDeletionLogRepository productDeletionLogRepository,
                          StockDeliveryRepository stockDeliveryRepository) {
        this.productRepository = productRepository;
        this.catalogueService = catalogueService;
        this.orderItemRepository = orderItemRepository;
        this.productDeletionLogRepository = productDeletionLogRepository;
        this.stockDeliveryRepository = stockDeliveryRepository;
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
     * Returns all products whose current stock is strictly below their
     * configured minimum threshold (CAT-US9/US10).
     *
     * <p>Uses strict {@code <} per the verbatim user-story acceptance criterion:
     * "Current Availability &lt; Threshold". Products without a threshold are excluded.
     *
     * <p>No caching — query runs on every call for real-time accuracy (US10 criterion 2).
     */
    @Transactional(readOnly = true)
    public List<LowStockProductDto> getLowStockProducts() {
        return productRepository.findLowStockProducts().stream()
                .map(LowStockProductDto::fromProduct)
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
        product.setMinStockThreshold(request.getMinStockThreshold()); // CAT-US8: optional
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
        product.setMinStockThreshold(request.getMinStockThreshold()); // CAT-US8: null clears threshold
        return productRepository.save(product);
    }

    /**
     * Records a stock delivery and atomically increments the product's availabilityCount (CAT-US7).
     *
     * <p>Null-safe: if a legacy product row has a null availabilityCount, it is treated as 0
     * before adding the delivery quantity.
     *
     * @param productId  the surrogate PK of the product being restocked
     * @param request    validated delivery data (date, quantity ≥ 1, optional supplier ref)
     * @param recordedBy the ADMIN user recording this delivery
     * @return response DTO with delivery details and updated stock count
     * @throws ResponseStatusException 404 if product not found
     */
    @Transactional
    public StockDeliveryResponse recordStockDelivery(Long productId,
                                                      RecordStockDeliveryRequest request,
                                                      User recordedBy) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Product not found with id: " + productId));

        // Null-safe: treat missing count as 0 (legacy rows)
        int current = product.getAvailabilityCount() != null ? product.getAvailabilityCount() : 0;
        int newCount = current + request.getQuantityReceived();
        product.setAvailabilityCount(newCount);

        StockDelivery delivery = new StockDelivery();
        delivery.setProduct(product);
        delivery.setDeliveryDate(request.getDeliveryDate());
        delivery.setQuantityReceived(request.getQuantityReceived());
        delivery.setSupplierReference(request.getSupplierReference());
        delivery.setRecordedBy(recordedBy);
        delivery.setRecordedAt(Instant.now());

        stockDeliveryRepository.save(delivery);
        productRepository.save(product);

        return StockDeliveryResponse.from(delivery, newCount);
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
