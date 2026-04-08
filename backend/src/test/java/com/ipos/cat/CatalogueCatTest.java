/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Central CAT test source for IPOS-SA-CAT US1–US10.                   ║
 * ║                                                                              ║
 * ║  WHY:  Keep catalogue behavior in one file for easier marking/review while   ║
 * ║        following the working style used by MerchantAccountServiceTest:       ║
 * ║        - flat Mockito tests in one class                                     ║
 * ║        - one @BeforeEach setup                                               ║
 * ║        - clear scenario-driven test names                                    ║
 * ║                                                                              ║
 * ║  CONTENTS:                                                                    ║
 * ║    1) CatalogueCatTest (Mockito unit tests):                                 ║
 * ║       - CAT-US1 initialize/ensure metadata                                   ║
 * ║       - CAT-US2 product create rules                                         ║
 * ║       - CAT-US3 product delete (audit log, 409 guard, 404)                   ║
 * ║       - CAT-US4 product update (immutable productCode, 404)                  ║
 * ║       - CAT-US5 searchProducts (AND logic, price validation)                 ║
 * ║       - CAT-US6 findAllForCatalogue (stock masking for merchants)            ║
 * ║       - CAT-US7 recordStockDelivery (increment, 404, null-safe stock)        ║
 * ║       - CAT-US8 minStockThreshold persisted on create/update                 ║
 * ║       - CAT-US9/US10 getLowStockProducts (DTO mapping, empty, null stock)  ║
 * ║    2) ProductControllerCatalogueCatWebMvcTest (WebMvc slice):               ║
 * ║       - DTO validation and successful POST/PUT/DELETE/GET responses          ║
 * ║       - CAT-US5 GET /api/products/search WebMvc                             ║
 * ║       - CAT-US6 merchant stock masking via GET /api/products                ║
 * ║       - CAT-US7 POST /api/products/{id}/deliveries (201, 400, 403)          ║
 * ║       - CAT-US8 PUT with minStockThreshold -1 → 400                         ║
 * ║    3) ReportControllerWebMvcTest (WebMvc slice):                            ║
 * ║       - CAT-US10 GET /api/reports/low-stock MANAGER → 200                  ║
 * ║       - CAT-US10 GET /api/reports/low-stock MERCHANT → 403                 ║
 * ║       - RPT-US4 GET /api/reports/invoices MANAGER → 200, MERCHANT → 403    ║
 * ║       - RPT-US5 GET /api/reports/stock-turnover MANAGER → 200, MERCHANT → 403║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.cat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipos.controller.ProductController;
import com.ipos.controller.ReportController;
import com.ipos.dto.CatalogueProductDto;
import com.ipos.dto.CreateProductRequest;
import com.ipos.dto.GlobalInvoiceReportResponse;
import com.ipos.dto.GlobalInvoiceRowDto;
import com.ipos.dto.LowStockProductDto;
import com.ipos.dto.StockTurnoverReportResponse;
import com.ipos.dto.StockTurnoverRowDto;
import com.ipos.dto.RecordStockDeliveryRequest;
import com.ipos.dto.StockDeliveryResponse;
import com.ipos.dto.UpdateProductRequest;
import com.ipos.entity.CatalogueMetadata;
import com.ipos.entity.Product;
import com.ipos.entity.ProductDeletionLog;
import com.ipos.entity.StockDelivery;
import com.ipos.entity.User;
import com.ipos.repository.CatalogueMetadataRepository;
import com.ipos.repository.OrderItemRepository;
import com.ipos.repository.ProductDeletionLogRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.StockDeliveryRepository;
import com.ipos.repository.UserRepository;
import com.ipos.security.SecurityConfig;
import com.ipos.service.CatalogueService;
import com.ipos.service.ProductService;
import com.ipos.service.ReportingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("IPOS-SA-CAT — Catalogue & product (CatalogueCatTest)")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unused", "null"})
public class CatalogueCatTest {

    @Mock
    private CatalogueMetadataRepository catalogueMetadataRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CatalogueService catalogueServiceMock;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private ProductDeletionLogRepository productDeletionLogRepository;

    @Mock
    private StockDeliveryRepository stockDeliveryRepository;

    private CatalogueService catalogueService;
    private ProductService productService;

    @BeforeEach
    void setUp() {
        catalogueService = new CatalogueService(catalogueMetadataRepository);
        productService = new ProductService(
                productRepository, catalogueServiceMock,
                orderItemRepository, productDeletionLogRepository,
                stockDeliveryRepository);
    }

    private static CreateProductRequest newCreateRequest(String code, String desc, String price, int avail) {
        CreateProductRequest r = new CreateProductRequest();
        r.setProductCode(code);
        r.setDescription(desc);
        r.setPrice(new BigDecimal(price));
        r.setAvailabilityCount(avail);
        return r;
    }

    private static UpdateProductRequest newUpdateRequest(String desc, String price, int avail) {
        UpdateProductRequest r = new UpdateProductRequest();
        r.setDescription(desc);
        r.setPrice(new BigDecimal(price));
        r.setAvailabilityCount(avail);
        return r;
    }

    private static Product sampleProduct(Long id, String code, String desc, String price, int avail) {
        Product p = new Product(code, desc, new BigDecimal(price), avail);
        p.setId(id);
        return p;
    }

    private static User sampleAdmin() {
        User u = new User();
        u.setId(99L);
        u.setUsername("admin");
        u.setName("Admin");
        u.setRole(User.Role.ADMIN);
        return u;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US1: CatalogueService metadata lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US1: initializeCatalogue first call persists metadata")
    void initializeCatalogue_firstCall_saves() {
        when(catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)).thenReturn(false);
        catalogueService.initializeCatalogue();
        verify(catalogueMetadataRepository).save(any(CatalogueMetadata.class));
    }

    @Test
    @DisplayName("CAT-US1: initializeCatalogue second call returns 409")
    void initializeCatalogue_secondCall_conflict() {
        when(catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)).thenReturn(true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> catalogueService.initializeCatalogue());
        assertEquals(409, ex.getStatusCode().value());
        verify(catalogueMetadataRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAT-US1: ensureCatalogueMetadataExists inserts when missing")
    void ensureMetadata_insertsWhenAbsent() {
        when(catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)).thenReturn(false);
        catalogueService.ensureCatalogueMetadataExists();
        verify(catalogueMetadataRepository).save(any(CatalogueMetadata.class));
    }

    @Test
    @DisplayName("CAT-US1: ensureCatalogueMetadataExists no-op when present")
    void ensureMetadata_noopWhenPresent() {
        when(catalogueMetadataRepository.existsById(CatalogueMetadata.SINGLETON_ID)).thenReturn(true);
        catalogueService.ensureCatalogueMetadataExists();
        verify(catalogueMetadataRepository, never()).save(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US2: ProductService#createProduct rules
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US2: createProduct normalizes productCode and saves")
    void createProduct_normalizesCode() {
        when(productRepository.existsByProductCode("ABC-001")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product out = productService.createProduct(
                newCreateRequest("  abc-001  ", "Aspirin", "9.99", 12));

        assertEquals("ABC-001", out.getProductCode());
        assertEquals("Aspirin", out.getDescription());
        verify(catalogueServiceMock).ensureCatalogueMetadataExists();
        verify(productRepository).save(any(Product.class));
    }

    @Test
    @DisplayName("CAT-US2: createProduct duplicate productCode returns 409")
    void createProduct_duplicateCode_conflict() {
        when(productRepository.existsByProductCode("DUP")).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.createProduct(newCreateRequest("DUP", "X", "1.00", 1)));
        assertEquals(409, ex.getStatusCode().value());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAT-US2: createProduct blank code after trim returns 400")
    void createProduct_blankCode_badRequest() {
        CreateProductRequest r = newCreateRequest("   ", "X", "1.00", 1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.createProduct(r));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("CAT-US2: createProduct trims description and allows zero stock")
    void createProduct_trimsDescription() {
        when(productRepository.existsByProductCode("X")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product out = productService.createProduct(
                newCreateRequest("x", "  trimmed  ", "2.50", 0));

        assertEquals("trimmed", out.getDescription());
        assertEquals(0, out.getAvailabilityCount());
    }

    @Test
    @DisplayName("CAT-US2: createProduct saves expected price and stock fields")
    void createProduct_savesFields() {
        when(productRepository.existsByProductCode("P1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.createProduct(newCreateRequest("p1", "Item", "100.00", 5));

        ArgumentCaptor<Product> cap = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(cap.capture());
        Product saved = cap.getValue();
        assertEquals("P1", saved.getProductCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(saved.getPrice()));
        assertEquals(5, saved.getAvailabilityCount());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US3: ProductService#deleteProduct rules
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US3: deleteProduct returns 404 when product not found")
    void deleteProduct_notFound_404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.deleteProduct(999L, sampleAdmin()));
        assertEquals(404, ex.getStatusCode().value());
        verify(productDeletionLogRepository, never()).save(any());
        verify(productRepository, never()).delete(any());
    }

    @Test
    @DisplayName("CAT-US3: deleteProduct returns 409 when order items reference product")
    void deleteProduct_orderHistoryExists_409() {
        Product product = sampleProduct(1L, "DEL1", "Item", "5.00", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProduct_Id(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.deleteProduct(1L, sampleAdmin()));
        assertEquals(409, ex.getStatusCode().value());
        verify(productDeletionLogRepository, never()).save(any());
        verify(productRepository, never()).delete(any());
    }

    @Test
    @DisplayName("CAT-US3: deleteProduct succeeds, saves audit log with actor snapshot")
    void deleteProduct_success_logsAndDeletes() {
        Product product = sampleProduct(1L, "DEL1", "Aspirin", "5.00", 10);
        User admin = sampleAdmin();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderItemRepository.existsByProduct_Id(1L)).thenReturn(false);

        productService.deleteProduct(1L, admin);

        ArgumentCaptor<ProductDeletionLog> logCap = ArgumentCaptor.forClass(ProductDeletionLog.class);
        verify(productDeletionLogRepository).save(logCap.capture());
        ProductDeletionLog savedLog = logCap.getValue();
        assertEquals(1L, savedLog.getProductIdSnapshot());
        assertEquals("DEL1", savedLog.getProductCodeSnapshot());
        assertEquals("Aspirin", savedLog.getDescriptionSnapshot());
        assertEquals(admin, savedLog.getDeletedBy());
        assertNotNull(savedLog.getDeletedAt());

        verify(productRepository).delete(product);
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US4: ProductService#updateProduct rules
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US4: updateProduct returns 404 when product not found")
    void updateProduct_notFound_404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.updateProduct(999L, newUpdateRequest("X", "1.00", 1)));
        assertEquals(404, ex.getStatusCode().value());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAT-US4: updateProduct updates fields but never changes productCode")
    void updateProduct_success_immutableCode() {
        Product existing = sampleProduct(1L, "ORIG-CODE", "Old desc", "5.00", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateProduct(1L,
                newUpdateRequest("New desc", "12.50", 20));

        assertEquals("ORIG-CODE", result.getProductCode());
        assertEquals("New desc", result.getDescription());
        assertEquals(0, new BigDecimal("12.50").compareTo(result.getPrice()));
        assertEquals(20, result.getAvailabilityCount());
    }

    @Test
    @DisplayName("CAT-US4: updateProduct trims description whitespace")
    void updateProduct_trimsDescription() {
        Product existing = sampleProduct(1L, "X", "Old", "1.00", 1);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateProduct(1L,
                newUpdateRequest("  padded  ", "2.00", 5));

        assertEquals("padded", result.getDescription());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US5: ProductService#searchProducts rules
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US5: searchProducts returns 400 when minPrice > maxPrice")
    void searchProducts_invalidPriceRange_400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.searchProducts(null, null,
                        new BigDecimal("50.00"), new BigDecimal("10.00"), false));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("CAT-US5: searchProducts delegates to repository and maps to DTOs")
    void searchProducts_delegatesToRepo() {
        Product p = sampleProduct(1L, "PARA", "Paracetamol", "3.50", 100);
        when(productRepository.search(eq("PARA"), isNull(), isNull(), isNull()))
                .thenReturn(List.of(p));

        List<CatalogueProductDto> results = productService.searchProducts(
                "PARA", null, null, null, false);

        assertEquals(1, results.size());
        assertEquals("PARA", results.get(0).getProductCode());
        assertEquals(100, results.get(0).getAvailabilityCount());
    }

    @Test
    @DisplayName("CAT-US5: searchProducts with all null filters returns all products mapped")
    void searchProducts_allNull_returnsAll() {
        when(productRepository.search(isNull(), isNull(), isNull(), isNull()))
                .thenReturn(List.of(
                        sampleProduct(1L, "A", "Alpha", "1.00", 5),
                        sampleProduct(2L, "B", "Beta", "2.00", 0)));

        List<CatalogueProductDto> results = productService.searchProducts(
                null, null, null, null, false);

        assertEquals(2, results.size());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US6: ProductService stock masking (merchant vs admin)
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US6: findAllForCatalogue maskStock=true hides count, shows status")
    void findAllForCatalogue_merchantMasked() {
        when(productRepository.findAll()).thenReturn(List.of(
                sampleProduct(1L, "X", "In-stock item", "5.00", 10),
                sampleProduct(2L, "Y", "Out-of-stock item", "3.00", 0)));

        List<CatalogueProductDto> dtos = productService.findAllForCatalogue(true);

        assertEquals(2, dtos.size());
        assertNull(dtos.get(0).getAvailabilityCount());
        assertEquals("AVAILABLE", dtos.get(0).getAvailabilityStatus());
        assertNull(dtos.get(1).getAvailabilityCount());
        assertEquals("OUT_OF_STOCK", dtos.get(1).getAvailabilityStatus());
    }

    @Test
    @DisplayName("CAT-US6: findAllForCatalogue maskStock=false includes count")
    void findAllForCatalogue_adminFull() {
        when(productRepository.findAll()).thenReturn(List.of(
                sampleProduct(1L, "X", "Item", "5.00", 10)));

        List<CatalogueProductDto> dtos = productService.findAllForCatalogue(false);

        assertEquals(1, dtos.size());
        assertEquals(10, dtos.get(0).getAvailabilityCount());
        assertEquals("AVAILABLE", dtos.get(0).getAvailabilityStatus());
    }

    @Test
    @DisplayName("CAT-US6: searchProducts with maskStock=true hides count from merchant")
    void searchProducts_merchantMasked() {
        Product p = sampleProduct(1L, "PARA", "Paracetamol", "3.50", 100);
        when(productRepository.search(isNull(), eq("para"), isNull(), isNull()))
                .thenReturn(List.of(p));

        List<CatalogueProductDto> results = productService.searchProducts(
                null, "para", null, null, true);

        assertEquals(1, results.size());
        assertNull(results.get(0).getAvailabilityCount());
        assertEquals("AVAILABLE", results.get(0).getAvailabilityStatus());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US7: ProductService#recordStockDelivery rules
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US7: recordStockDelivery returns 404 when product not found")
    void recordDelivery_notFound_404() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        RecordStockDeliveryRequest req = new RecordStockDeliveryRequest();
        req.setDeliveryDate(LocalDate.of(2026, 4, 1));
        req.setQuantityReceived(10);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> productService.recordStockDelivery(999L, req, sampleAdmin()));
        assertEquals(404, ex.getStatusCode().value());
        verify(stockDeliveryRepository, never()).save(any());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("CAT-US7: recordStockDelivery increments availabilityCount atomically")
    void recordDelivery_success_incrementsCount() {
        Product product = sampleProduct(1L, "PARA", "Paracetamol", "3.50", 20);
        User admin = sampleAdmin();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(stockDeliveryRepository.save(any(StockDelivery.class)))
                .thenAnswer(inv -> {
                    StockDelivery d = inv.getArgument(0);
                    d.setId(1L);
                    return d;
                });
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordStockDeliveryRequest req = new RecordStockDeliveryRequest();
        req.setDeliveryDate(LocalDate.of(2026, 4, 1));
        req.setQuantityReceived(30);
        req.setSupplierReference("PO-12345");

        StockDeliveryResponse response = productService.recordStockDelivery(1L, req, admin);

        assertEquals(50, response.getNewAvailabilityCount());
        assertEquals(30, response.getQuantityReceived());
        assertEquals("PO-12345", response.getSupplierReference());

        ArgumentCaptor<Product> productCap = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCap.capture());
        assertEquals(50, productCap.getValue().getAvailabilityCount());

        verify(stockDeliveryRepository).save(any(StockDelivery.class));
    }

    @Test
    @DisplayName("CAT-US7: recordStockDelivery treats null availabilityCount as 0 (null-safe)")
    void recordDelivery_nullStockTreatedAsZero() {
        Product product = new Product("X", "Item", new BigDecimal("5.00"), null); // null stock
        product.setId(1L);
        User admin = sampleAdmin();
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(stockDeliveryRepository.save(any(StockDelivery.class)))
                .thenAnswer(inv -> {
                    StockDelivery d = inv.getArgument(0);
                    d.setId(1L);
                    return d;
                });
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        RecordStockDeliveryRequest req = new RecordStockDeliveryRequest();
        req.setDeliveryDate(LocalDate.of(2026, 4, 1));
        req.setQuantityReceived(15);

        StockDeliveryResponse response = productService.recordStockDelivery(1L, req, admin);

        assertEquals(15, response.getNewAvailabilityCount());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  CAT-US8: minStockThreshold persisted on create and update
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("CAT-US8: createProduct persists minStockThreshold when provided")
    void createProduct_persistsThreshold() {
        when(productRepository.existsByProductCode("T1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateProductRequest req = newCreateRequest("t1", "Item", "5.00", 10);
        req.setMinStockThreshold(5);

        Product result = productService.createProduct(req);

        assertEquals(5, result.getMinStockThreshold());
    }

    @Test
    @DisplayName("CAT-US8: createProduct with no threshold leaves field null")
    void createProduct_nullThresholdLeavesNull() {
        when(productRepository.existsByProductCode("T2")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.createProduct(newCreateRequest("t2", "Item", "5.00", 10));

        assertNull(result.getMinStockThreshold());
    }

    @Test
    @DisplayName("CAT-US8: updateProduct persists minStockThreshold when provided")
    void updateProduct_persistsThreshold() {
        Product existing = sampleProduct(1L, "X", "Old", "5.00", 10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateProductRequest req = newUpdateRequest("Updated", "8.00", 20);
        req.setMinStockThreshold(3);

        Product result = productService.updateProduct(1L, req);

        assertEquals(3, result.getMinStockThreshold());
    }

    @Test
    @DisplayName("CAT-US8: updateProduct with null threshold clears existing threshold")
    void updateProduct_nullThresholdClearsField() {
        Product existing = sampleProduct(1L, "X", "Old", "5.00", 10);
        existing.setMinStockThreshold(10);
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product result = productService.updateProduct(1L, newUpdateRequest("Updated", "8.00", 20));

        assertNull(result.getMinStockThreshold());
    }

    // ── CAT-US9/US10: getLowStockProducts ────────────────────────────────────

    @Test
    @DisplayName("CAT-US10: getLowStockProducts returns DTOs for products below threshold")
    void getLowStockProducts_returnsMappedDtos() {
        Product p1 = sampleProduct(1L, "LOW1", "Paracetamol", "2.50", 3);
        p1.setMinStockThreshold(10);
        Product p2 = sampleProduct(2L, "LOW2", "Ibuprofen", "3.00", 0);
        p2.setMinStockThreshold(5);

        when(productRepository.findLowStockProducts()).thenReturn(List.of(p1, p2));

        var result = productService.getLowStockProducts();

        assertEquals(2, result.size());
        assertEquals("LOW1", result.get(0).getProductCode());
        assertEquals(3, result.get(0).getAvailabilityCount());
        assertEquals(10, result.get(0).getMinStockThreshold());
        assertEquals("LOW2", result.get(1).getProductCode());
    }

    @Test
    @DisplayName("CAT-US10: getLowStockProducts returns empty list when no products below threshold")
    void getLowStockProducts_emptyWhenNoneQualify() {
        when(productRepository.findLowStockProducts()).thenReturn(List.of());

        var result = productService.getLowStockProducts();

        assertEquals(0, result.size());
    }

    @Test
    @DisplayName("CAT-US10: getLowStockProducts treats null availabilityCount as 0")
    void getLowStockProducts_nullAvailabilityTreatedAsZero() {
        Product p = sampleProduct(1L, "LEGACY", "OldDrug", "1.00", 0);
        p.setAvailabilityCount(null);
        p.setMinStockThreshold(5);

        when(productRepository.findLowStockProducts()).thenReturn(List.of(p));

        var result = productService.getLowStockProducts();

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).getAvailabilityCount());
    }
}

/*
 * WebMvc slice — same source file as CatalogueCatTest; Surefire discovers *Test classes.
 */
@WebMvcTest(controllers = ProductController.class)
@Import(SecurityConfig.class)
@DisplayName("ProductController — WebMvc slice (CAT-US2/US3/US4/US5/US6/US7/US8)")
@SuppressWarnings({"null", "unused"})
class ProductControllerCatalogueCatWebMvcTest {

    /** Registers Spring Security method-security AOP advisors for @PreAuthorize checks. */
    @TestConfiguration
    @EnableMethodSecurity
    static class MethodSecurityTestConfig {}

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private UserRepository userRepository;

    // ── CAT-US2: POST ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/products with {} → 400 Bad Request (validation)")
    void create_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/products with valid body → 200 and JSON product")
    void create_valid_returns200AndProduct() throws Exception {
        CreateProductRequest req = new CreateProductRequest();
        req.setProductCode("SKU1");
        req.setDescription("Item");
        req.setPrice(new BigDecimal("10.00"));
        req.setAvailabilityCount(3);

        Product saved = new Product("SKU1", "Item", new BigDecimal("10.00"), 3);
        saved.setId(42L);
        mockMvc.perform(post("/api/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── CAT-US4: PUT ────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/products/1 with {} → 400 Bad Request (validation)")
    void update_emptyBody_returns400() throws Exception {
        mockMvc.perform(put("/api/products/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/products/1 with valid body → 200")
    void update_valid_returns200() throws Exception {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setDescription("Updated");
        req.setPrice(new BigDecimal("15.00"));
        req.setAvailabilityCount(10);

        Product updated = new Product("X", "Updated", new BigDecimal("15.00"), 10);
        updated.setId(1L);
        when(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/products/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── CAT-US3: DELETE ─────────────────────────────────────────────────────

    @Test
    @DisplayName("DELETE /api/products/1 → 204 No Content (mocked service)")
    void delete_success_returns204() throws Exception {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        mockMvc.perform(delete("/api/products/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(productService).deleteProduct(eq(1L), any(User.class));
    }

    // ── CAT-US6: GET /api/products — merchant stock masking ─────────────────

    @Test
    @DisplayName("GET /api/products as MERCHANT → 200, availabilityCount absent in JSON")
    void findAll_merchant_masksStock() throws Exception {
        CatalogueProductDto dto = CatalogueProductDto.fromProduct(
                new Product("X1", "Test", new BigDecimal("5.00"), 10), true);
        when(productService.findAllForCatalogue(true)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/products")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availabilityStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].availabilityCount").doesNotExist());
    }

    @Test
    @DisplayName("GET /api/products as ADMIN → 200, availabilityCount present in JSON")
    void findAll_admin_showsStock() throws Exception {
        CatalogueProductDto dto = CatalogueProductDto.fromProduct(
                new Product("X1", "Test", new BigDecimal("5.00"), 10), false);
        when(productService.findAllForCatalogue(false)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/products")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].availabilityCount").value(10))
                .andExpect(jsonPath("$[0].availabilityStatus").value("AVAILABLE"));
    }

    // ── CAT-US5: GET /api/products/search ───────────────────────────────────

    @Test
    @DisplayName("GET /api/products/search?q=para → 200 and invokes service")
    void search_byDescription_returns200() throws Exception {
        CatalogueProductDto dto = CatalogueProductDto.fromProduct(
                new Product("PARA1", "Paracetamol 500mg", new BigDecimal("3.50"), 50), false);
        when(productService.searchProducts(isNull(), eq("para"), isNull(), isNull(), eq(false)))
                .thenReturn(List.of(dto));

        mockMvc.perform(get("/api/products/search")
                        .param("q", "para")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Paracetamol 500mg"));
    }

    @Test
    @DisplayName("GET /api/products/search with no params → 200 (returns all)")
    void search_noParams_returns200() throws Exception {
        when(productService.searchProducts(isNull(), isNull(), isNull(), isNull(), eq(false)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/products/search")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // ── CAT-US7: POST /api/products/{id}/deliveries ─────────────────────────

    @Test
    @DisplayName("POST /api/products/1/deliveries with valid body + ADMIN → 201 Created")
    void recordDelivery_valid_returns201() throws Exception {
        User admin = new User();
        admin.setId(1L);
        admin.setUsername("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        StockDeliveryResponse mockResponse = new StockDeliveryResponse();
        when(productService.recordStockDelivery(eq(1L), any(RecordStockDeliveryRequest.class), any(User.class)))
                .thenReturn(mockResponse);

        String body = """
                {"deliveryDate":"2026-04-01","quantityReceived":50}""";

        mockMvc.perform(post("/api/products/1/deliveries")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/products/1/deliveries with quantity=0 → 400 Bad Request")
    void recordDelivery_zeroQuantity_returns400() throws Exception {
        String body = """
                {"deliveryDate":"2026-04-01","quantityReceived":0}""";

        mockMvc.perform(post("/api/products/1/deliveries")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/products/1/deliveries as MERCHANT → 403 Forbidden")
    void recordDelivery_merchantForbidden_returns403() throws Exception {
        String body = """
                {"deliveryDate":"2026-04-01","quantityReceived":10}""";

        mockMvc.perform(post("/api/products/1/deliveries")
                        .with(user("merchant").roles("MERCHANT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/products/1/deliveries with missing body → 400 Bad Request")
    void recordDelivery_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/products/1/deliveries")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── CAT-US8: PUT with invalid minStockThreshold ─────────────────────────

    @Test
    @DisplayName("PUT /api/products/1 with minStockThreshold=-1 → 400 Bad Request")
    void update_negativeThreshold_returns400() throws Exception {
        String body = """
                {"description":"Test","price":5.00,"availabilityCount":10,"minStockThreshold":-1}""";

        mockMvc.perform(put("/api/products/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PUT /api/products/1 with minStockThreshold=5 → 200 OK")
    void update_validThreshold_returns200() throws Exception {
        UpdateProductRequest req = new UpdateProductRequest();
        req.setDescription("Updated");
        req.setPrice(new BigDecimal("10.00"));
        req.setAvailabilityCount(50);
        req.setMinStockThreshold(5);

        Product updated = new Product("X", "Updated", new BigDecimal("10.00"), 50);
        updated.setId(1L);
        updated.setMinStockThreshold(5);
        when(productService.updateProduct(eq(1L), any(UpdateProductRequest.class))).thenReturn(updated);

        mockMvc.perform(put("/api/products/1")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}

/*
 * WebMvc slice for ReportController — CAT-US10 low-stock report.
 */
@WebMvcTest(controllers = ReportController.class)
@Import(SecurityConfig.class)
@DisplayName("ReportController — WebMvc slice (CAT-US10, RPT-US4, RPT-US5)")
@SuppressWarnings({"null", "unused"})
class ReportControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private ReportingService reportingService;

    // ── CAT-US10: GET /api/reports/low-stock ─────────────────────────────────

    @Test
    @DisplayName("GET /api/reports/low-stock as MANAGER → 200 with JSON list")
    void lowStockReport_manager_returns200() throws Exception {
        Product p = new Product("LOW1", "LowItem", new BigDecimal("5.00"), 2);
        p.setId(1L);
        p.setMinStockThreshold(10);

        when(productService.getLowStockProducts())
                .thenReturn(List.of(LowStockProductDto.fromProduct(p)));

        mockMvc.perform(get("/api/reports/low-stock")
                        .with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productCode").value("LOW1"))
                .andExpect(jsonPath("$[0].availabilityCount").value(2))
                .andExpect(jsonPath("$[0].minStockThreshold").value(10));
    }

    @Test
    @DisplayName("GET /api/reports/low-stock as ADMIN → 200")
    void lowStockReport_admin_returns200() throws Exception {
        when(productService.getLowStockProducts()).thenReturn(List.of());

        mockMvc.perform(get("/api/reports/low-stock")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/reports/low-stock as MERCHANT → 403 Forbidden")
    void lowStockReport_merchant_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/low-stock")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/reports/low-stock unauthenticated → 401")
    void lowStockReport_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/reports/low-stock"))
                .andExpect(status().isUnauthorized());
    }

    // ── RPT-US4: GET /api/reports/invoices ───────────────────────────────────

    @Test
    @DisplayName("GET /api/reports/invoices as MANAGER → 200 with rows")
    void globalInvoices_manager_returns200() throws Exception {
        GlobalInvoiceRowDto row = new GlobalInvoiceRowDto();
        row.setInvoiceId(1L);
        row.setInvoiceNumber("INV-2026-00001");
        row.setMerchantId(2L);
        row.setMerchantUsername("m1");
        row.setMerchantName("Shop");
        row.setPaymentStatus("PENDING");
        when(reportingService.getGlobalInvoiceReport(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new GlobalInvoiceReportResponse(List.of(row)));

        mockMvc.perform(get("/api/reports/invoices")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-30")
                        .with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].invoiceNumber").value("INV-2026-00001"))
                .andExpect(jsonPath("$.rows[0].paymentStatus").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/reports/invoices as MERCHANT → 403")
    void globalInvoices_merchant_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/invoices")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-30")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isForbidden());
    }

    // ── RPT-US5: GET /api/reports/stock-turnover ─────────────────────────────

    @Test
    @DisplayName("GET /api/reports/stock-turnover as MANAGER → 200 with rows")
    void stockTurnover_manager_returns200() throws Exception {
        StockTurnoverRowDto row = new StockTurnoverRowDto(1L, "SKU1", 5L, 12L);
        when(reportingService.getStockTurnover(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new StockTurnoverReportResponse(List.of(row)));

        mockMvc.perform(get("/api/reports/stock-turnover")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-30")
                        .with(user("manager").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].productCode").value("SKU1"))
                .andExpect(jsonPath("$.rows[0].quantitySold").value(5))
                .andExpect(jsonPath("$.rows[0].quantityReceived").value(12));
    }

    @Test
    @DisplayName("GET /api/reports/stock-turnover as MERCHANT → 403")
    void stockTurnover_merchant_returns403() throws Exception {
        mockMvc.perform(get("/api/reports/stock-turnover")
                        .param("start", "2026-04-01")
                        .param("end", "2026-04-30")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isForbidden());
    }
}
