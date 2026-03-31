/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit 5 unit tests for ProductService (IPOS-SA-CAT).                ║
 * ║                                                                              ║
 * ║  ROLE:  Unit testing — one of the TWO required non-trivial class tests.    ║
 * ║         (The second non-trivial class test is MerchantAccountServiceTest.) ║
 * ║                                                                              ║
 * ║  WHY:  ProductService now has 6 public methods, qualifying it as a         ║
 * ║        "non-trivial class with more than 5 methods" per the brief.         ║
 * ║                                                                              ║
 * ║  COVERAGE (core behaviours under test):                                    ║
 * ║    • getCatalogue / findAll (catalogue browsing)                           ║
 * ║    • increaseStock — success and failure paths                             ║
 * ║    • decreaseStock — success and failure paths                             ║
 * ║    • basic CRUD helpers: findById, save, delete                            ║
 * ║                                                                            ║
 * ║  NOTE: Individual @Test methods may be added/renamed over time; this       ║
 * ║                                                                              ║
 * ║  HOW:  Pure Mockito unit tests — no Spring context, no database.           ║
 * ║        ProductRepository is mocked so tests run in milliseconds.           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.Product;
import com.ipos.repository.CatalogueMetadataRepository;
import com.ipos.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CatalogueMetadataRepository catalogueMetadataRepository;

    private ProductService productService;
    private CatalogueService catalogueService;

    @BeforeEach
    void setUp() {
        // Use real CatalogueService (with mocked repo) so Mockito doesn't need to mock CatalogueService itself.
        catalogueService = new CatalogueService(catalogueMetadataRepository);
        productService = new ProductService(productRepository, catalogueService);
    }

    /* ── Helper ──────────────────────────────────────────────────────────── */

    private Product makeProduct(Long id, String description, int stock) {
        Product p = new Product("SKU-" + id, description, new BigDecimal("10.00"), stock);
        p.setId(id);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 12-13: getCatalogue() / findAll()
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 12: Catalogue contains items → non-empty List<Product> returned.
     *
     * Design-doc: getCatalogue() : List<ItemInfo>
     *   Comment: Catalogue contains items
     *   Expected: Non-empty List<ItemInfo> returned.
     */
    @Test
    @DisplayName("T12 getCatalogue: Catalogue contains items — non-empty list returned")
    void getCatalogue_withItems_returnsNonEmptyList() {
        Product p1 = makeProduct(1L, "Amoxicillin 500mg", 100);
        Product p2 = makeProduct(2L, "Ibuprofen 400mg",   200);
        when(productRepository.findAll()).thenReturn(List.of(p1, p2));

        List<Product> result = productService.findAll();

        assertNotNull(result, "Result must not be null");
        assertFalse(result.isEmpty(), "Result must not be empty when catalogue has items");
        assertEquals(2, result.size());
    }

    /*
     * TEST 13: Catalogue exists but is empty → empty list returned (NOT null).
     *
     * Design-doc: getCatalogue() : List<ItemInfo>
     *   Comment: Catalogue exists but is empty
     *   Expected: Empty list returned (not null)
     */
    @Test
    @DisplayName("T13 getCatalogue: Empty catalogue — empty list returned, not null")
    void getCatalogue_emptyRepo_returnsEmptyListNotNull() {
        when(productRepository.findAll()).thenReturn(Collections.emptyList());

        List<Product> result = productService.findAll();

        assertNotNull(result, "Result must not be null even when catalogue is empty");
        assertTrue(result.isEmpty(), "Result must be empty");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 17-18: increaseStock — success paths
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 17: Item exists with valid quantity — stock increases by 5.
     *
     * Design-doc: increaseStock(itemID=10, quantity=5)
     *   Comment: Item exists with valid quantity
     *   Expected: Stock level increases by 5
     */
    @Test
    @DisplayName("T17 increaseStock: Valid item + quantity — stock increases correctly")
    void increaseStock_validItemAndQuantity_stockIncreases() {
        Product product = makeProduct(10L, "Paracetamol 500mg", 20);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.increaseStock(10L, 5);

        assertEquals(25, result.getAvailabilityCount(),
                "Stock should increase from 20 to 25");
        verify(productRepository).save(product);
    }

    /*
     * TEST 18: Item is out of stock (availabilityCount=0); adding 1 unit → stock=1.
     *
     * Design-doc: increaseStock(itemID=15, quantity=1)
     *   Comment: Adding a single item to a product that is out of stock
     *   Expected: Stock level increases from 0 to 1
     */
    @Test
    @DisplayName("T18 increaseStock: Out-of-stock item + quantity=1 — stock goes from 0 to 1")
    void increaseStock_outOfStockItem_stockGoesFromZeroToOne() {
        Product product = makeProduct(15L, "Metformin 850mg", 0);
        when(productRepository.findById(15L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.increaseStock(15L, 1);

        assertEquals(1, result.getAvailabilityCount(),
                "Stock should increase from 0 to 1");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 19-22: increaseStock — failure paths
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 19: Item doesn't exist — exception raised, stock unchanged.
     *
     * Design-doc: increaseStock(itemID=9999999, quantity=5)
     *   Comment: Item doesn't exist
     *   Expected: Failure. Exception raised ('Item not found'). Stock unchanged.
     */
    @Test
    @DisplayName("T19 increaseStock: Non-existent item — 'Item not found' exception")
    void increaseStock_nonExistentItem_throwsItemNotFound() {
        when(productRepository.findById(9999999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.increaseStock(9999999L, 5));

        assertTrue(ex.getMessage().contains("Item not found"),
                "Exception must mention 'Item not found'");
        verify(productRepository, never()).save(any());
    }

    /*
     * TEST 20: Valid item, negative quantity — exception raised.
     *
     * Design-doc: increaseStock(itemID=10, quantity=-5)
     *   Comment: Valid item with negative quantity provided
     *   Expected: Failure. Exception raised ('Quantity must be positive'). Stock unchanged.
     */
    @Test
    @DisplayName("T20 increaseStock: Negative quantity — 'Quantity must be positive' exception")
    void increaseStock_negativeQuantity_throwsQuantityMustBePositive() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.increaseStock(10L, -5));

        assertTrue(ex.getMessage().contains("Quantity must be positive"),
                "Exception must mention 'Quantity must be positive'");
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    /*
     * TEST 21: Valid item, quantity=0 — exception raised.
     *
     * Design-doc: increaseStock(itemID=10, quantity=0)
     *   Comment: Valid item id, zero quantity provided
     *   Expected: Failure. Exception raised ('Quantity must be greater than zero'). Stock remains unchanged.
     */
    @Test
    @DisplayName("T21 increaseStock: Zero quantity — exception raised, stock unchanged")
    void increaseStock_zeroQuantity_throwsQuantityMustBePositive() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.increaseStock(10L, 0));

        assertTrue(ex.getMessage().contains("Quantity must be positive"),
                "Exception must mention that quantity must be positive");
        verify(productRepository, never()).save(any());
    }

    /*
     * TEST 22: Quantity value exceeds system limits (Integer.MAX_VALUE = 2147483647).
     *
     * Design-doc: increaseStock(itemID=10, quantity=2147483647)
     *   Comment: Quantity value is too large
     *   Expected: Failure. Exception raised ('Quantity exceeds system limits'). Stock level unchanged.
     */
    @Test
    @DisplayName("T22 increaseStock: Quantity exceeds system limits — exception raised")
    void increaseStock_quantityExceedsSystemLimits_throwsSystemLimits() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.increaseStock(10L, 2_147_483_647));

        assertTrue(ex.getMessage().contains("system limits"),
                "Exception must mention system limits");
        verify(productRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 23: decreaseStock — success path
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 23: Stock available >= requested quantity — stock decreases correctly.
     *
     * Design-doc: decreaseStock(itemID=10, quantity=3)
     *   Comment: Stock available >= 3
     *   Expected: Success. Stock decreases correctly by 3.
     */
    @Test
    @DisplayName("T23 decreaseStock: Sufficient stock — stock decreases correctly by 3")
    void decreaseStock_sufficientStock_stockDecreases() {
        Product product = makeProduct(10L, "Omeprazole 20mg", 50);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.decreaseStock(10L, 3);

        assertEquals(47, result.getAvailabilityCount(),
                "Stock should decrease from 50 to 47");
        verify(productRepository).save(product);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 24-27: decreaseStock — failure paths
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 24: Insufficient stock — exception raised, stock unchanged.
     *
     * Design-doc: decreaseStock(itemID=10, quantity=100)
     *   Comment: Stock insufficient
     *   Expected: Failure. Exception raised ('Insufficient stock'). Stock unchanged.
     */
    @Test
    @DisplayName("T24 decreaseStock: Insufficient stock — 'Insufficient stock' exception")
    void decreaseStock_insufficientStock_throwsInsufficientStock() {
        Product product = makeProduct(10L, "Azithromycin 250mg", 10);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.decreaseStock(10L, 100));

        assertTrue(ex.getMessage().contains("Insufficient stock"),
                "Exception must mention 'Insufficient stock'");
        verify(productRepository, never()).save(any());
    }

    /*
     * TEST 25: Item doesn't exist — exception raised, stock not changed.
     *
     * Design-doc: decreaseStock(itemID=999999, quantity=3)
     *   Comment: Item doesn't exist
     *   Expected: Failure. Exception raised ('Item not found'). Stock not changed.
     */
    @Test
    @DisplayName("T25 decreaseStock: Non-existent item — 'Item not found' exception")
    void decreaseStock_nonExistentItem_throwsItemNotFound() {
        when(productRepository.findById(999999L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.decreaseStock(999999L, 3));

        assertTrue(ex.getMessage().contains("Item not found"),
                "Exception must mention 'Item not found'");
        verify(productRepository, never()).save(any());
    }

    /*
     * TEST 26: Negative quantity — exception raised, stock not changed.
     *
     * Design-doc: decreaseStock(itemID=10, quantity=-2)
     *   Comment: Valid item. Negative quantity provided
     *   Expected: Failure. Exception raised ('Quantity must be positive'). Stock not changed.
     */
    @Test
    @DisplayName("T26 decreaseStock: Negative quantity — exception raised, stock not changed")
    void decreaseStock_negativeQuantity_throwsQuantityMustBePositive() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.decreaseStock(10L, -2));

        assertTrue(ex.getMessage().contains("greater than zero"),
                "Exception must say quantity must be greater than zero");
        verify(productRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    /*
     * TEST 27: Zero quantity — exception raised.
     *
     * Design-doc: decreaseStock(itemID=10, quantity=0)
     *   Comment: Requesting a 0 quantity stock reduction
     *   Expected: Failure. Exception raised ('Reduction quantity must be greater than zero').
     */
    @Test
    @DisplayName("T27 decreaseStock: Zero quantity — exception raised")
    void decreaseStock_zeroQuantity_throwsReductionQuantityMustBeGreaterThanZero() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                productService.decreaseStock(10L, 0));

        assertTrue(ex.getMessage().contains("greater than zero"),
                "Exception must say 'Reduction quantity must be greater than zero'");
        verify(productRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  Additional unit tests: findById, save, delete
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("findById: Existing product is returned correctly")
    void findById_existingProduct_returned() {
        Product p = makeProduct(1L, "Test Drug", 50);
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        Product result = productService.findById(1L);

        assertEquals(p, result);
    }

    @Test
    @DisplayName("findById: Non-existent product throws RuntimeException")
    void findById_nonExistentProduct_throws() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> productService.findById(99L));
    }

    @Test
    @DisplayName("save: Product is persisted via repository")
    void save_delegatesToRepository() {
        Product p = makeProduct(null, "New Drug", 30);
        when(productRepository.save(p)).thenReturn(p);

        Product result = productService.save(p);

        assertEquals(p, result);
        verify(productRepository).save(p);
    }

    @Test
    @DisplayName("delete: Existing product is deleted via repository")
    void delete_existingProduct_deleted() {
        when(productRepository.existsById(1L)).thenReturn(true);

        productService.delete(1L);

        verify(productRepository).deleteById(1L);
    }

    @Test
    @DisplayName("delete: Non-existent product throws RuntimeException")
    void delete_nonExistentProduct_throws() {
        when(productRepository.existsById(99L)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> productService.delete(99L));
        verify(productRepository, never()).deleteById(any());
    }
}
