/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Central ORD test source for IPOS-SA-ORD US3, US5, US6.             ║
 * ║                                                                              ║
 * ║  WHY:  Verifies invoice generation, payment recording, and balance          ║
 * ║        calculation as Mockito unit tests and WebMvc slice tests.            ║
 * ║                                                                              ║
 * ║  CONTENTS:                                                                    ║
 * ║    1) InvoiceServiceTest (Mockito — 4 tests):                               ║
 * ║       - generateForOrder creates invoice with correct snapshot              ║
 * ║       - generateForOrder is idempotent (returns existing)                   ║
 * ║       - invoice lines match order items                                     ║
 * ║       - invoice number follows INV-YYYY-NNNNN format                        ║
 * ║    2) PaymentServiceTest (Mockito — 3 tests):                               ║
 * ║       - recordPayment succeeds within outstanding                           ║
 * ║       - recordPayment rejects amount exceeding outstanding                  ║
 * ║       - recordPayment rejects zero/negative amount                          ║
 * ║    3) InvoiceControllerWebMvcTest (WebMvc — 5 tests):                       ║
 * ║       - GET /api/invoices as MERCHANT -> 200                                ║
 * ║       - GET /api/invoices as ADMIN -> 200                                   ║
 * ║       - POST /api/invoices/{id}/payments as ADMIN -> 201                    ║
 * ║       - POST /api/invoices/{id}/payments as MERCHANT -> 403                 ║
 * ║       - GET /api/merchant-financials/balance as MERCHANT -> 200             ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.ord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipos.controller.InvoiceController;
import com.ipos.controller.MerchantFinancialsController;
import com.ipos.entity.Invoice;
import com.ipos.entity.InvoiceLine;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Payment;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.PaymentRepository;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.UserRepository;
import com.ipos.security.SecurityConfig;
import com.ipos.service.InvoiceService;
import com.ipos.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/* ═══════════════════════════════════════════════════════════════════════════════
 *  1) Mockito unit tests — InvoiceService
 * ═══════════════════════════════════════════════════════════════════════════════ */

@DisplayName("IPOS-SA-ORD — Invoice generation (InvoiceServiceTest)")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unused", "null"})
public class ORDInvoicePaymentTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private MerchantProfileRepository profileRepository;

    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        invoiceService = new InvoiceService(invoiceRepository, profileRepository);
    }

    private Order sampleOrder() {
        User merchant = new User();
        merchant.setId(1L);
        merchant.setName("Test Merchant");
        merchant.setRole(User.Role.MERCHANT);

        Product product = new Product();
        product.setId(10L);
        product.setDescription("Aspirin 500mg");

        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(5);
        item.setUnitPriceAtOrder(new BigDecimal("10.00"));

        Order order = new Order();
        order.setId(100L);
        order.setMerchant(merchant);
        order.setStatus(Order.OrderStatus.ACCEPTED);
        order.setPlacedAt(Instant.now());
        order.setGrossTotal(new BigDecimal("50.00"));
        order.setFixedDiscountAmount(new BigDecimal("2.50"));
        order.setFlexibleCreditApplied(BigDecimal.ZERO);
        order.setTotalDue(new BigDecimal("47.50"));
        order.getItems().add(item);
        item.setOrder(order);

        return order;
    }

    private MerchantProfile sampleProfile() {
        MerchantProfile p = new MerchantProfile();
        p.setContactEmail("merchant@test.com");
        p.setContactPhone("07700 000000");
        p.setAddressLine("1 Test Street");
        p.setVatRegistrationNumber("GB999999999");
        p.setPaymentTermsDays(30);
        return p;
    }

    @Test
    @DisplayName("ORD-US5: generateForOrder creates invoice with correct merchant snapshot")
    void generateForOrder_snapshotsMerchantDetails() {
        Order order = sampleOrder();
        MerchantProfile profile = sampleProfile();

        when(invoiceRepository.findByOrder_Id(100L)).thenReturn(Optional.empty());
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(invoiceRepository.countByInvoiceNumberPrefix(anyString())).thenReturn(0L);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.generateForOrder(order);

        assertEquals("merchant@test.com", result.getMerchantEmail());
        assertEquals("1 Test Street", result.getMerchantAddress());
        assertEquals("GB999999999", result.getMerchantVat());
        assertEquals(new BigDecimal("47.50"), result.getTotalDue());
        assertNotNull(result.getDueDate());
    }

    @Test
    @DisplayName("ORD-US5: generateForOrder is idempotent — returns existing invoice")
    void generateForOrder_idempotent() {
        Invoice existing = new Invoice();
        existing.setId(50L);

        when(invoiceRepository.findByOrder_Id(100L)).thenReturn(Optional.of(existing));

        Invoice result = invoiceService.generateForOrder(sampleOrder());

        assertEquals(50L, result.getId());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("ORD-US5: invoice lines match order items")
    void generateForOrder_linesMatchOrderItems() {
        Order order = sampleOrder();
        MerchantProfile profile = sampleProfile();

        when(invoiceRepository.findByOrder_Id(100L)).thenReturn(Optional.empty());
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(invoiceRepository.countByInvoiceNumberPrefix(anyString())).thenReturn(0L);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.generateForOrder(order);

        assertEquals(1, result.getLines().size());
        InvoiceLine line = result.getLines().get(0);
        assertEquals("Aspirin 500mg", line.getDescription());
        assertEquals(5, line.getQuantity());
        assertEquals(new BigDecimal("10.00"), line.getUnitPrice());
        assertEquals(new BigDecimal("50.00"), line.getLineTotal());
    }

    @Test
    @DisplayName("ORD-US5: invoice number follows INV-YYYY-NNNNN format")
    void generateForOrder_invoiceNumberFormat() {
        Order order = sampleOrder();
        MerchantProfile profile = sampleProfile();

        when(invoiceRepository.findByOrder_Id(100L)).thenReturn(Optional.empty());
        when(profileRepository.findByUserId(1L)).thenReturn(Optional.of(profile));
        when(invoiceRepository.countByInvoiceNumberPrefix(anyString())).thenReturn(4L);
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(inv -> inv.getArgument(0));

        Invoice result = invoiceService.generateForOrder(order);

        assertTrue(result.getInvoiceNumber().matches("INV-\\d{4}-00005"),
                "Expected INV-YYYY-00005 but got: " + result.getInvoiceNumber());
    }
}


/* ═══════════════════════════════════════════════════════════════════════════════
 *  2) Mockito unit tests — PaymentService
 * ═══════════════════════════════════════════════════════════════════════════════ */

@DisplayName("IPOS-SA-ORD — Payment recording (PaymentServiceTest)")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unused", "null"})
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantProfileRepository merchantProfileRepository;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, invoiceRepository, userRepository, merchantProfileRepository);
    }

    private Invoice sampleInvoice() {
        Invoice inv = new Invoice();
        inv.setId(1L);
        inv.setTotalDue(new BigDecimal("100.00"));
        return inv;
    }

    private User adminUser() {
        User u = new User();
        u.setId(99L);
        u.setUsername("admin");
        u.setRole(User.Role.ADMIN);
        return u;
    }

    @Test
    @DisplayName("ORD-US6: recordPayment succeeds when amount <= outstanding")
    void recordPayment_withinOutstanding_succeeds() {
        Invoice inv = sampleInvoice();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));
        when(paymentRepository.sumByInvoiceId(1L)).thenReturn(new BigDecimal("40.00"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser()));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.recordPayment(1L, new BigDecimal("50.00"),
                Payment.PaymentMethod.CARD, "admin");

        assertEquals(new BigDecimal("50.00"), result.getAmount());
        assertEquals(Payment.PaymentMethod.CARD, result.getMethod());
    }

    @Test
    @DisplayName("ORD-US6: recordPayment rejects amount exceeding outstanding")
    void recordPayment_exceedsOutstanding_throws() {
        Invoice inv = sampleInvoice();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));
        when(paymentRepository.sumByInvoiceId(1L)).thenReturn(new BigDecimal("90.00"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.recordPayment(1L, new BigDecimal("20.00"),
                        Payment.PaymentMethod.BANK_TRANSFER, "admin"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("ORD-US6: recordPayment rejects zero amount")
    void recordPayment_zeroAmount_throws() {
        Invoice inv = sampleInvoice();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> paymentService.recordPayment(1L, BigDecimal.ZERO,
                        Payment.PaymentMethod.CHEQUE, "admin"));
        assertEquals(400, ex.getStatusCode().value());
    }
}


/* ═══════════════════════════════════════════════════════════════════════════════
 *  3) WebMvc slice — InvoiceController + MerchantFinancialsController
 * ═══════════════════════════════════════════════════════════════════════════════ */

@WebMvcTest(controllers = {InvoiceController.class, MerchantFinancialsController.class})
@Import(SecurityConfig.class)
@DisplayName("InvoiceController + MerchantFinancials — WebMvc slice (ORD-US3/US5/US6)")
@SuppressWarnings({"null", "unused"})
class InvoicePaymentWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private InvoiceRepository invoiceRepository;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private PaymentRepository paymentRepository;

    private User merchantUser() {
        User u = new User();
        u.setId(1L);
        u.setUsername("merchant");
        u.setRole(User.Role.MERCHANT);
        return u;
    }

    private User adminUser() {
        User u = new User();
        u.setId(99L);
        u.setUsername("admin");
        u.setRole(User.Role.ADMIN);
        return u;
    }

    @Test
    @DisplayName("GET /api/invoices as MERCHANT -> 200")
    void getInvoices_merchant_returns200() throws Exception {
        User m = merchantUser();
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(m));
        when(invoiceRepository.findByMerchant_IdOrderByIssuedAtDesc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/invoices")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/invoices as ADMIN -> 200")
    void getInvoices_admin_returns200() throws Exception {
        User a = adminUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(a));
        when(invoiceRepository.findAllByOrderByIssuedAtDesc()).thenReturn(List.of());

        mockMvc.perform(get("/api/invoices")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("POST /api/invoices/1/payments as ADMIN -> 201")
    void recordPayment_admin_returns201() throws Exception {
        Payment p = new Payment();
        p.setId(1L);
        p.setAmount(new BigDecimal("50.00"));
        p.setMethod(Payment.PaymentMethod.CARD);
        p.setRecordedAt(Instant.now());

        when(paymentService.recordPayment(eq(1L), any(), any(), eq("admin")))
                .thenReturn(p);

        mockMvc.perform(post("/api/invoices/1/payments")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"method\":\"CARD\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/invoices/1/payments as MERCHANT -> 403")
    void recordPayment_merchant_returns403() throws Exception {
        mockMvc.perform(post("/api/invoices/1/payments")
                        .with(user("merchant").roles("MERCHANT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":50.00,\"method\":\"CARD\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/merchant-financials/balance as MERCHANT -> 200")
    void getBalance_merchant_returns200() throws Exception {
        User m = merchantUser();
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(m));
        when(invoiceRepository.sumTotalDueByMerchantId(1L)).thenReturn(new BigDecimal("100.00"));
        when(invoiceRepository.sumPaymentsByMerchantId(1L)).thenReturn(new BigDecimal("30.00"));
        when(invoiceRepository.findByMerchant_IdOrderByIssuedAtDesc(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/merchant-financials/balance")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingTotal").value(70.00))
                .andExpect(jsonPath("$.currency").value("GBP"));
    }
}
