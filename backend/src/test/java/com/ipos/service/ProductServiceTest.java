/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit 5 unit tests for ProductService (IPOS-SA-CAT) — service layer.  ║
 * ║                                                                              ║
 * ║  WHY:  Complements {@link com.ipos.cat.CatalogueCatTest} (CAT-US1–US10) by   ║
 * ║        exercising {@link ProductService} entry points that are not covered  ║
 * ║        elsewhere: {@code findAll}, {@code findById}, {@code getLowStockProducts}. ║
 * ║        Together with MerchantAccountServiceTest this satisfies the brief’s   ║
 * ║        “two non-trivial classes with unit tests” requirement.                ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.dto.LowStockProductDto;
import com.ipos.entity.Product;
import com.ipos.repository.CatalogueMetadataRepository;
import com.ipos.repository.OrderItemRepository;
import com.ipos.repository.ProductDeletionLogRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.StockDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CatalogueMetadataRepository catalogueMetadataRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductDeletionLogRepository productDeletionLogRepository;

    @Mock
    private StockDeliveryRepository stockDeliveryRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        CatalogueService catalogueService = new CatalogueService(catalogueMetadataRepository);
        productService = new ProductService(
                productRepository,
                catalogueService,
                orderItemRepository,
                productDeletionLogRepository,
                stockDeliveryRepository);
    }

    private static Product product(long id, String code, int stock) {
        Product p = new Product(code, "Desc", new BigDecimal("9.99"), stock);
        p.setId(id);
        return p;
    }

    @Test
    @DisplayName("T12 findAll: catalogue has items — returns non-empty list")
    void findAll_withItems_returnsNonEmpty() {
        when(productRepository.findAll()).thenReturn(List.of(product(1L, "A", 1), product(2L, "B", 2)));

        List<Product> result = productService.findAll();

        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("T13 findAll: empty repository — empty list (not null)")
    void findAll_empty_returnsEmptyNotNull() {
        when(productRepository.findAll()).thenReturn(Collections.emptyList());

        List<Product> result = productService.findAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("findById: existing product — returned")
    void findById_found_returnsProduct() {
        Product p = product(10L, "SKU", 5);
        when(productRepository.findById(10L)).thenReturn(Optional.of(p));

        Product result = productService.findById(10L);

        assertEquals(10L, result.getId());
        assertEquals("SKU", result.getProductCode());
    }

    @Test
    @DisplayName("findById: missing id — 404 ResponseStatusException")
    void findById_missing_throws404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.findById(999L));

        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("getLowStockProducts: maps repository low-stock rows to DTOs")
    void getLowStockProducts_delegatesToRepository() {
        Product low = product(1L, "LOW", 2);
        low.setMinStockThreshold(10);
        when(productRepository.findLowStockProducts()).thenReturn(List.of(low));

        List<LowStockProductDto> dtos = productService.getLowStockProducts();

        assertEquals(1, dtos.size());
        assertEquals("LOW", dtos.get(0).getProductCode());
    }

    @Test
    @DisplayName("getLowStockProducts: none low — empty list")
    void getLowStockProducts_none_empty() {
        when(productRepository.findLowStockProducts()).thenReturn(Collections.emptyList());

        assertTrue(productService.getLowStockProducts().isEmpty());
    }
}
