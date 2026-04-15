/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Central ORD test source for IPOS-SA-ORD US1–US2.                    ║
 * ║                                                                              ║
 * ║  WHY:  Keep order behavior in one file for easier marking/review while       ║
 * ║        following the working style used by CatalogueCatTest:                 ║
 * ║        - flat Mockito unit tests in one class                                ║
 * ║        - one @BeforeEach setup                                               ║
 * ║        - clear scenario-driven test names                                    ║
 * ║                                                                              ║
 * ║  CONTENTS:                                                                    ║
 * ║    1) ORDOrderTest (Mockito unit tests — 9 tests):                           ║
 * ║       - ORD-US1 placeOrder sets ACCEPTED, rejects empty items                ║
 * ║       - ORD-US1 credit limit net of payments                                 ║
 * ║       - ORD-US2 findOrdersForActor scoping (merchant vs staff)               ║
 * ║       - ORD-US2 updateOrderStatus valid/invalid transitions                  ║
 * ║    2) OrderControllerWebMvcTest (WebMvc slice — 5 tests):                    ║
 * ║       - GET /api/orders role-scoped (MERCHANT, ADMIN)                        ║
 * ║       - PUT /api/orders/{id}/status as MANAGER → 200                        ║
 * ║       - PUT /api/orders/{id}/status as MERCHANT → 403                       ║
 * ║       - PUT /api/orders/{id}/status unauthenticated → 401                   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.ord;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ipos.controller.OrderController;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.entity.Order;
import com.ipos.entity.Order.OrderStatus;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.ProductRepository;
import com.ipos.repository.UserRepository;
import com.ipos.security.SecurityConfig;
import com.ipos.config.IntegrationCaProperties;
import com.ipos.service.InvoiceService;
import com.ipos.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/* ═══════════════════════════════════════════════════════════════════════════════
 *  1) Mockito unit tests — OrderService
 * ═══════════════════════════════════════════════════════════════════════════════ */

@DisplayName("IPOS-SA-ORD — Order placement & tracking (ORDOrderTest)")
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unused", "null"})
public class ORDOrderTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductRepository productRepository;
    @Mock private UserRepository userRepository;
    @Mock private MerchantProfileRepository profileRepository;
    @Mock private InvoiceService invoiceService;
    @Mock private InvoiceRepository invoiceRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(
                orderRepository, productRepository, userRepository, profileRepository,
                invoiceService,
                invoiceRepository,
                new IntegrationCaProperties());
    }

    /* ── helpers ────────────────────────────────────────────────────────────── */

    private static User sampleMerchantUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("merchant" + id);
        u.setRole(User.Role.MERCHANT);
        return u;
    }

    private static MerchantProfile sampleProfile(User user) {
        MerchantProfile p = new MerchantProfile();
        p.setUser(user);
        p.setStanding(MerchantStanding.NORMAL);
        p.setDiscountPlanType(DiscountPlanType.FIXED);
        p.setFixedDiscountPercent(BigDecimal.ZERO);
        p.setCreditLimit(new BigDecimal("10000"));
        p.setFlexibleDiscountCredit(BigDecimal.ZERO);
        return p;
    }

    private static OrderItem itemFor(Long productId, int qty) {
        Product stub = new Product();
        stub.setId(productId);
        OrderItem oi = new OrderItem();
        oi.setProduct(stub);
        oi.setQuantity(qty);
        return oi;
    }

    private static Product sampleProduct(Long id, String desc, String price, int stock) {
        Product p = new Product("P" + id, desc, new BigDecimal(price), stock);
        p.setId(id);
        return p;
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ORD-US1: placeOrder sets ACCEPTED and rejects empty items
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ORD-US1: placeOrder sets status to ACCEPTED")
    void placeOrder_setsStatusAccepted() {
        Long merchantId = 1L;
        User merchant = sampleMerchantUser(merchantId);
        MerchantProfile profile = sampleProfile(merchant);
        Product product = sampleProduct(10L, "Paracetamol", "5.00", 100);

        when(userRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(profileRepository.findByUserId(merchantId)).thenReturn(Optional.of(profile));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(orderRepository.sumTotalDueByMerchantExcludingStatus(
                eq(merchantId), any(OrderStatus.class))).thenReturn(BigDecimal.ZERO);
        when(invoiceRepository.sumPaymentsByMerchantId(merchantId)).thenReturn(BigDecimal.ZERO);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        List<OrderItem> items = List.of(itemFor(10L, 2));
        Order result = orderService.placeOrder(merchantId, items, merchantId, User.Role.MERCHANT);

        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        assertNotNull(result.getPlacedAt());
    }

    @Test
    @DisplayName("ORD-US1: placeOrder rejects empty items list")
    void placeOrder_emptyItems_throws() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.placeOrder(1L, List.of(), 1L, User.Role.MERCHANT));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("ORD-US1: placeOrder rejects null items list")
    void placeOrder_nullItems_throws() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.placeOrder(1L, null, 1L, User.Role.MERCHANT));
        assertEquals(400, ex.getStatusCode().value());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ORD-US2: findOrdersForActor — role-based scoping
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ORD-US2: findOrdersForActor(MERCHANT) queries only merchant's orders")
    void findOrdersForActor_merchant_scoped() {
        when(orderRepository.findByMerchant_IdOrderByPlacedAtDesc(5L))
                .thenReturn(List.of());

        orderService.findOrdersForActor(5L, User.Role.MERCHANT);

        verify(orderRepository).findByMerchant_IdOrderByPlacedAtDesc(5L);
        verify(orderRepository, never()).findAllByOrderByPlacedAtDesc();
    }

    @Test
    @DisplayName("ORD-US2: findOrdersForActor(ADMIN) returns all orders")
    void findOrdersForActor_admin_allOrders() {
        when(orderRepository.findAllByOrderByPlacedAtDesc()).thenReturn(List.of());

        orderService.findOrdersForActor(99L, User.Role.ADMIN);

        verify(orderRepository).findAllByOrderByPlacedAtDesc();
        verify(orderRepository, never()).findByMerchant_IdOrderByPlacedAtDesc(any());
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  ORD-US2: updateOrderStatus — lifecycle transitions
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("ORD-US2: updateOrderStatus ACCEPTED → PROCESSING succeeds")
    void updateOrderStatus_acceptedToProcessing_ok() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.ACCEPTED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = orderService.updateOrderStatus(1L, OrderStatus.PROCESSING);
        assertEquals(OrderStatus.PROCESSING, result.getStatus());
    }

    @Test
    @DisplayName("ORD-US2: updateOrderStatus DISPATCHED → PROCESSING rejected")
    void updateOrderStatus_dispatchedToProcessing_throws() {
        Order order = new Order();
        order.setId(1L);
        order.setStatus(OrderStatus.DISPATCHED);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.updateOrderStatus(1L, OrderStatus.PROCESSING));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("ORD-US2: updateOrderStatus on non-existent order throws 404")
    void updateOrderStatus_notFound_throws404() {
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> orderService.updateOrderStatus(999L, OrderStatus.PROCESSING));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    @DisplayName("Credit limit: net exposure subtracts payments — order allowed when gross would fail")
    void placeOrder_netExposureAfterPayments_allowsOrder() {
        Long merchantId = 1L;
        User merchant = sampleMerchantUser(merchantId);
        MerchantProfile profile = sampleProfile(merchant);
        profile.setCreditLimit(new BigDecimal("200.00"));
        Product product = sampleProduct(10L, "Paracetamol", "5.00", 100);

        when(userRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        when(profileRepository.findByUserId(merchantId)).thenReturn(Optional.of(profile));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        /* Gross order exposure £500, but merchant paid £400 — net £100. New order £90 → £190 < £200. */
        when(orderRepository.sumTotalDueByMerchantExcludingStatus(
                eq(merchantId), any(OrderStatus.class))).thenReturn(new BigDecimal("500.00"));
        when(invoiceRepository.sumPaymentsByMerchantId(merchantId)).thenReturn(new BigDecimal("400.00"));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        List<OrderItem> items = List.of(itemFor(10L, 18));
        Order result = orderService.placeOrder(merchantId, items, merchantId, User.Role.MERCHANT);

        assertEquals(OrderStatus.ACCEPTED, result.getStatus());
        verify(orderRepository).save(any(Order.class));
    }
}

/* ═══════════════════════════════════════════════════════════════════════════════
 *  2) WebMvc slice — OrderController
 * ═══════════════════════════════════════════════════════════════════════════════ */

@WebMvcTest(controllers = OrderController.class)
@Import(SecurityConfig.class)
@DisplayName("OrderController — WebMvc slice (ORD-US1/US2)")
@SuppressWarnings({"null", "unused"})
class OrderControllerWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private OrderService orderService;
    @MockitoBean private UserRepository userRepository;

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

    // ── ORD-US2: GET /api/orders ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders as MERCHANT → 200, calls findOrdersForActor with MERCHANT role")
    void getOrders_merchant_returns200() throws Exception {
        User m = merchantUser();
        when(userRepository.findByUsername("merchant")).thenReturn(Optional.of(m));
        when(orderService.findOrdersForActor(1L, User.Role.MERCHANT)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .with(user("merchant").roles("MERCHANT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(orderService).findOrdersForActor(1L, User.Role.MERCHANT);
    }

    @Test
    @DisplayName("GET /api/orders as ADMIN → 200, calls findOrdersForActor with ADMIN role")
    void getOrders_admin_returns200() throws Exception {
        User a = adminUser();
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(a));
        when(orderService.findOrdersForActor(99L, User.Role.ADMIN)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(orderService).findOrdersForActor(99L, User.Role.ADMIN);
    }

    // ── ORD-US2: PUT /api/orders/{id}/status ─────────────────────────────────

    @Test
    @DisplayName("PUT /api/orders/1/status as MANAGER → 200")
    void updateStatus_manager_returns200() throws Exception {
        Order updated = new Order();
        updated.setId(1L);
        updated.setStatus(OrderStatus.PROCESSING);

        when(orderService.updateOrderStatus(1L, OrderStatus.PROCESSING, null, null, null, null))
                .thenReturn(updated);

        mockMvc.perform(put("/api/orders/1/status")
                        .with(user("manager").roles("MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PROCESSING"));
    }

    @Test
    @DisplayName("PUT /api/orders/1/status as MERCHANT → 403 Forbidden")
    void updateStatus_merchant_returns403() throws Exception {
        mockMvc.perform(put("/api/orders/1/status")
                        .with(user("merchant").roles("MERCHANT"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PUT /api/orders/1/status unauthenticated → 401")
    void updateStatus_unauthenticated_returns401() throws Exception {
        mockMvc.perform(put("/api/orders/1/status")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PROCESSING\"}"))
                .andExpect(status().isUnauthorized());
    }
}
