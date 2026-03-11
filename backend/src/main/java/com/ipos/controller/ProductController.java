/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for Product endpoints (the drug catalogue).          ║
 * ║                                                                              ║
 * ║  WHY:  The React frontend calls these endpoints to display available         ║
 * ║        products and to let admins add new products to the catalogue.         ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add @PutMapping("/{id}") to update price or stock.                ║
 * ║        - Add @GetMapping("/search?q=…") for keyword search.                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.entity.Product;
import com.ipos.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /* GET /api/products → Returns every product in the catalogue. */
    @GetMapping
    public List<Product> findAll() {
        return productService.findAll();
    }

    /* POST /api/products → Creates a new product from the JSON request body. */
    @PostMapping
    public Product create(@RequestBody Product product) {
        return productService.save(product);
    }
}
