/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Product-related business logic (IPOS-SA-CAT).      ║
 * ║                                                                              ║
 * ║  WHY:  Having a service layer means validations and inventory operations     ║
 * ║        (e.g., stock increase/decrease) live here, not in the controller.    ║
 * ║                                                                              ║
 * ║  METHODS (non-trivial class — 6 public methods):                            ║
 * ║        findAll()           — Return the full catalogue (CAT-US1/US6).       ║
 * ║        findById()          — Look up a single product (throws if absent).   ║
 * ║        save()              — Create or update a product.                    ║
 * ║        delete()            — Remove a product from the catalogue (CAT-US3). ║
 * ║        increaseStock()     — Record a delivery and add units (CAT-US7).     ║
 * ║        decreaseStock()     — Deduct units; throws if insufficient stock.    ║
 * ║                                                                              ║
 * ║  SYSTEM LIMITS:                                                              ║
 * ║        MAX_STOCK_DELTA (10_000_000) guards against absurdly large delivery  ║
 * ║        quantities that would overflow Integer.MAX_VALUE when added to the   ║
 * ║        existing stock level.                                                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.Product;
import com.ipos.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    static final int MAX_STOCK_DELTA = 10_000_000;

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /* ── IPOS-SA-CAT: Catalogue browsing ─────────────────────────────────── */

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Item not found with id: " + id));
    }

    /* ── IPOS-SA-CAT: Catalogue management (ADMIN) ───────────────────────── */

    public Product save(Product product) {
        return productRepository.save(product);
    }

    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new RuntimeException("Item not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    /* ── IPOS-SA-CAT: Inventory management (CAT-US7, CAT-US8) ───────────── */

    /*
     * increaseStock — Record a stock delivery for a product.
     *
     * Rules (CAT-US7 acceptance criteria):
     *   - itemID must refer to an existing product.
     *   - quantity must be > 0 ("reject any delivery quantity equal to or less than zero").
     *   - quantity must be within system limits (MAX_STOCK_DELTA) to prevent
     *     integer overflow when added to the current stock level.
     *
     * @param id       The product to restock.
     * @param quantity Number of units being delivered (must be 1 ≤ qty ≤ MAX_STOCK_DELTA).
     * @return         The updated Product with the new availability count.
     */
    public Product increaseStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("Quantity must be positive.");
        }
        if (quantity > MAX_STOCK_DELTA) {
            throw new RuntimeException("Quantity exceeds system limits.");
        }
        Product product = findById(id);
        product.setAvailabilityCount(product.getAvailabilityCount() + quantity);
        return productRepository.save(product);
    }

    /*
     * decreaseStock — Deduct units from a product's availability.
     *
     * Used internally by OrderService when an order is placed, and also
     * exposed here for direct stock-adjustment operations.
     *
     * Rules:
     *   - itemID must refer to an existing product.
     *   - quantity must be > 0.
     *   - Current stock must be >= quantity ("Insufficient stock").
     *
     * @param id       The product to deduct stock from.
     * @param quantity Number of units to remove (must be ≥ 1).
     * @return         The updated Product.
     */
    public Product decreaseStock(Long id, int quantity) {
        if (quantity <= 0) {
            throw new RuntimeException("Reduction quantity must be greater than zero.");
        }
        Product product = findById(id);
        if (product.getAvailabilityCount() < quantity) {
            throw new RuntimeException(
                    "Insufficient stock for '" + product.getDescription()
                    + "'. Available: " + product.getAvailabilityCount()
                    + ", Requested: " + quantity);
        }
        product.setAvailabilityCount(product.getAvailabilityCount() - quantity);
        return productRepository.save(product);
    }
}
