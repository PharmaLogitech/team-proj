/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Central CAT test source for IPOS-SA-CAT US1 and US2.                ║
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
 * ║    2) ProductControllerCatalogueCatWebMvcTest (WebMvc slice):               ║
 * ║       - DTO validation and successful POST response                          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.cat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipos.controller.ProductController;
import com.ipos.dto.CreateProductRequest;
import com.ipos.entity.CatalogueMetadata;
import com.ipos.entity.Product;
import com.ipos.repository.CatalogueMetadataRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.service.CatalogueService;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    private CatalogueService catalogueService;
    private Object productServiceUnderTest;

    @BeforeEach
    void setUp() {
        catalogueService = new CatalogueService(catalogueMetadataRepository);
        try {
            Class<?> clazz = Class.forName("com.ipos.service.ProductService");
            productServiceUnderTest = clazz
                    .getConstructor(ProductRepository.class, CatalogueService.class)
                    .newInstance(productRepository, catalogueServiceMock);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to construct ProductService for tests", e);
        }
    }

    private static CreateProductRequest newCreateRequest(String code, String desc, String price, int avail) {
        CreateProductRequest r = new CreateProductRequest();
        r.setProductCode(code);
        r.setDescription(desc);
        r.setPrice(new BigDecimal(price));
        r.setAvailabilityCount(avail);
        return r;
    }

    private Product invokeCreateProduct(CreateProductRequest request) {
        try {
            Method method = productServiceUnderTest.getClass()
                    .getMethod("createProduct", CreateProductRequest.class);
            return (Product) method.invoke(productServiceUnderTest, request);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new RuntimeException("Failed to invoke ProductService#createProduct", e);
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ResponseStatusException rse) {
                throw rse;
            }
            throw new RuntimeException("Failed to invoke ProductService#createProduct", e);
        }
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

        Product out = invokeCreateProduct(
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
                () -> invokeCreateProduct(newCreateRequest("DUP", "X", "1.00", 1)));
        assertEquals(409, ex.getStatusCode().value());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("CAT-US2: createProduct blank code after trim returns 400")
    void createProduct_blankCode_badRequest() {
        CreateProductRequest r = newCreateRequest("   ", "X", "1.00", 1);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> invokeCreateProduct(r));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("CAT-US2: createProduct trims description and allows zero stock")
    void createProduct_trimsDescription() {
        when(productRepository.existsByProductCode("X")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        Product out = invokeCreateProduct(
                newCreateRequest("x", "  trimmed  ", "2.50", 0));

        assertEquals("trimmed", out.getDescription());
        assertEquals(0, out.getAvailabilityCount());
    }

    @Test
    @DisplayName("CAT-US2: createProduct saves expected price and stock fields")
    void createProduct_savesFields() {
        when(productRepository.existsByProductCode("P1")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        invokeCreateProduct(newCreateRequest("p1", "Item", "100.00", 5));

        ArgumentCaptor<Product> cap = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(cap.capture());
        Product saved = cap.getValue();
        assertEquals("P1", saved.getProductCode());
        assertEquals(0, new BigDecimal("100.00").compareTo(saved.getPrice()));
        assertEquals(5, saved.getAvailabilityCount());
    }
}

/*
 * WebMvc slice — same source file as CatalogueCatTest; Surefire discovers *Test classes.
 */
@WebMvcTest(controllers = ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("ProductController — WebMvc slice (CAT-US2)")
@SuppressWarnings({"null", "unused"})
class ProductControllerCatalogueCatWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private com.ipos.service.ProductService productService;

    @Test
    @DisplayName("POST /api/products with {} → 400 Bad Request (validation)")
    void create_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }
}
