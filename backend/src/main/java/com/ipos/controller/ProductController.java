/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for Product endpoints (the drug catalogue).          ║
 * ║                                                                              ║
 * ║  WHY:  The React frontend calls these endpoints to display available         ║
 * ║        products and to let admins add new products to the catalogue.         ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4 — RBAC):                                           ║
 * ║        Enforced in SecurityConfig.java (URL-level rules):                   ║
 * ║        - GET  /api/products/** → Authenticated (all roles can read).       ║
 * ║          Merchants see catalogue read-only (CAT-US6) with masked stock.    ║
 * ║        - POST /api/products/** → ADMIN only (CAT-US2 product creation).   ║
 * ║        - PUT  /api/products/** → ADMIN only (CAT-US4 data maintenance).   ║
 * ║        - DELETE /api/products/** → ADMIN only (CAT-US3 discontinuation).  ║
 * ║                                                                              ║
 * ║  ENDPOINTS:                                                                   ║
 * ║        GET    /api/products         → list all (role-aware DTO)             ║
 * ║        GET    /api/products/search  → combined search (CAT-US5/US6)        ║
 * ║        POST   /api/products         → create (CAT-US2)                     ║
 * ║        PUT    /api/products/{id}    → update (CAT-US4)                     ║
 * ║        DELETE /api/products/{id}    → remove (CAT-US3)                     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.dto.CatalogueProductDto;
import com.ipos.dto.CreateProductRequest;
import com.ipos.dto.UpdateProductRequest;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import com.ipos.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final UserRepository userRepository;

    public ProductController(ProductService productService, UserRepository userRepository) {
        this.productService = productService;
        this.userRepository = userRepository;
    }

    /* GET /api/products → Role-aware catalogue list (CAT-US6 stock masking). */
    @GetMapping
    public List<CatalogueProductDto> findAll(Authentication authentication) {
        return productService.findAllForCatalogue(isMerchant(authentication));
    }

    /*
     * GET /api/products/search → Combined search with AND-logic (CAT-US5).
     * Merchants receive masked stock counts (CAT-US6).
     */
    @GetMapping("/search")
    public List<CatalogueProductDto> search(
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Authentication authentication) {
        return productService.searchProducts(
                blankToNull(productCode),
                blankToNull(q),
                minPrice,
                maxPrice,
                isMerchant(authentication));
    }

    /* POST /api/products → Creates a new product (CAT-US2). */
    @PostMapping
    public Product create(@Valid @RequestBody CreateProductRequest request) {
        return productService.createProduct(request);
    }

    /* PUT /api/products/{id} → Updates product details (CAT-US4). Product ID is immutable. */
    @PutMapping("/{id}")
    public Product update(@PathVariable Long id,
                          @Valid @RequestBody UpdateProductRequest request) {
        return productService.updateProduct(id, request);
    }

    /* DELETE /api/products/{id} → Removes a product with audit log (CAT-US3). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        User deletedBy = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Authenticated user not found in database"));
        productService.deleteProduct(id, deletedBy);
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isMerchant(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_MERCHANT"::equals);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
