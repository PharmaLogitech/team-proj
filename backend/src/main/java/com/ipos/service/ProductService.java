/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service class for Product-related business logic.                    ║
 * ║                                                                              ║
 * ║  WHY:  Even though the current methods are thin wrappers around the          ║
 * ║        repository, having a service layer now means future validations       ║
 * ║        (e.g., "price must be positive") or complex queries slot in here      ║
 * ║        without touching the controller.                                      ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add search/filter methods: findByKeyword(String keyword).          ║
 * ║        - Add inventory management: restockProduct(Long id, int qty).        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.Product;
import com.ipos.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Product findById(Long id) {
        /*
         * findById returns an Optional<Product>.  We call .orElseThrow()
         * which either unwraps the Product or throws an exception if the
         * id doesn't exist.  For Phase 1 we use a simple RuntimeException;
         * in later phases you'd create a custom NotFoundException.
         */
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }

    public Product save(Product product) {
        return productRepository.save(product);
    }
}
