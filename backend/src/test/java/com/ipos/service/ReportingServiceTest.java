/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Mockito unit tests for ReportingService (RPT-US1–US3).              ║
 * ║                                                                              ║
 * ║  HOW:  Same style as MerchantAccountServiceTest — no Spring context.         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.dto.MerchantActivityReportResponse;
import com.ipos.dto.MerchantOrderHistoryResponse;
import com.ipos.dto.SalesTurnoverResponse;
import com.ipos.entity.Invoice;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.Product;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.OrderItemRepository;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.PaymentRepository;
import com.ipos.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportingService (RPT-US1 / RPT-US2 / RPT-US3)")
public class ReportingServiceTest {

    private static final LocalDate START = LocalDate.of(2026, 4, 1);
    private static final LocalDate END = LocalDate.of(2026, 4, 30);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private MerchantProfileRepository merchantProfileRepository;

    @InjectMocks
    private ReportingService reportingService;

    private static User merchantUser42() {
        User u = new User();
        u.setId(42L);
        u.setRole(User.Role.MERCHANT);
        return u;
    }

    @Test
    @DisplayName("RPT-US1: getSalesTurnover returns summed quantity and revenue from repositories")
    void salesTurnover_aggregatesRepositorySums() {
        when(orderItemRepository.sumQuantityInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("12"));
        when(orderRepository.sumTotalDueInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED)))
                .thenReturn(new BigDecimal("450.00"));

        SalesTurnoverResponse r = reportingService.getSalesTurnover(START, END);

        assertEquals(12L, r.getTotalQuantitySold());
        assertEquals(0, new BigDecimal("450.00").compareTo(r.getTotalRevenue()));
        assertEquals("GBP", r.getCurrency());
    }

    @Test
    @DisplayName("RPT-US1: turnover queries exclude CANCELLED orders (repository contract)")
    void salesTurnover_excludesCancelled_viaCancelledStatusArgument() {
        when(orderItemRepository.sumQuantityInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumTotalDueInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED)))
                .thenReturn(BigDecimal.ZERO);

        reportingService.getSalesTurnover(START, END);

        verify(orderItemRepository).sumQuantityInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED));
        verify(orderRepository).sumTotalDueInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("RPT-US1: no matching orders yields zero quantity and zero revenue")
    void salesTurnover_emptyPeriod_returnsZeros() {
        when(orderItemRepository.sumQuantityInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED)))
                .thenReturn(BigDecimal.ZERO);
        when(orderRepository.sumTotalDueInPeriodExcludingStatus(
                any(Instant.class), any(Instant.class), eq(Order.OrderStatus.CANCELLED)))
                .thenReturn(BigDecimal.ZERO);

        SalesTurnoverResponse r = reportingService.getSalesTurnover(START, END);

        assertEquals(0L, r.getTotalQuantitySold());
        assertEquals(0, BigDecimal.ZERO.compareTo(r.getTotalRevenue()));
    }

    @Test
    @DisplayName("RPT-US1: start after end throws 400")
    void salesTurnover_startAfterEnd_throws400() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportingService.getSalesTurnover(LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @DisplayName("RPT-US2: history rows include order id, dates, total value; period total matches sum")
    void merchantHistory_rowsAndPeriodTotal() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(merchantUser42()));

        Order o1 = new Order();
        o1.setId(1L);
        o1.setPlacedAt(Instant.parse("2026-04-05T10:00:00Z"));
        o1.setDispatchedAt(Instant.parse("2026-04-06T12:00:00Z"));
        o1.setTotalDue(new BigDecimal("100.00"));

        Order o2 = new Order();
        o2.setId(2L);
        o2.setPlacedAt(Instant.parse("2026-04-10T14:00:00Z"));
        o2.setDispatchedAt(null);
        o2.setTotalDue(new BigDecimal("50.00"));

        when(orderRepository.findByMerchantIdAndPlacedAtRange(eq(42L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(o1, o2));

        Invoice inv1 = new Invoice();
        inv1.setId(10L);
        inv1.setTotalDue(new BigDecimal("100.00"));
        when(invoiceRepository.findByOrder_Id(1L)).thenReturn(Optional.of(inv1));
        when(paymentRepository.sumByInvoiceId(10L)).thenReturn(new BigDecimal("100.00"));

        Invoice inv2 = new Invoice();
        inv2.setId(11L);
        inv2.setTotalDue(new BigDecimal("50.00"));
        when(invoiceRepository.findByOrder_Id(2L)).thenReturn(Optional.of(inv2));
        when(paymentRepository.sumByInvoiceId(11L)).thenReturn(BigDecimal.ZERO);

        MerchantOrderHistoryResponse r = reportingService.getMerchantOrderHistory(42L, START, END);

        assertEquals(2, r.getRows().size());
        assertEquals(1L, r.getRows().get(0).getOrderId());
        assertEquals(ReportingService.PAYMENT_PAID, r.getRows().get(0).getPaymentStatus());
        assertEquals(ReportingService.PAYMENT_PENDING, r.getRows().get(1).getPaymentStatus());
        assertEquals(0, new BigDecimal("150.00").compareTo(r.getPeriodTotalValue()));
    }

    @Test
    @DisplayName("RPT-US2: payment status PAID when payments cover totalDue")
    void merchantHistory_paymentPaid() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(merchantUser42()));
        Order o = new Order();
        o.setId(9L);
        o.setPlacedAt(Instant.now());
        o.setTotalDue(new BigDecimal("80.00"));
        when(orderRepository.findByMerchantIdAndPlacedAtRange(eq(42L), any(), any()))
                .thenReturn(List.of(o));

        Invoice inv = new Invoice();
        inv.setId(99L);
        inv.setTotalDue(new BigDecimal("80.00"));
        when(invoiceRepository.findByOrder_Id(9L)).thenReturn(Optional.of(inv));
        when(paymentRepository.sumByInvoiceId(99L)).thenReturn(new BigDecimal("80.00"));

        MerchantOrderHistoryResponse r = reportingService.getMerchantOrderHistory(42L, START, END);
        assertEquals(ReportingService.PAYMENT_PAID, r.getRows().get(0).getPaymentStatus());
    }

    @Test
    @DisplayName("RPT-US2: payment status PARTIAL when some payment but not full")
    void merchantHistory_paymentPartial() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(merchantUser42()));
        Order o = new Order();
        o.setId(9L);
        o.setPlacedAt(Instant.now());
        o.setTotalDue(new BigDecimal("80.00"));
        when(orderRepository.findByMerchantIdAndPlacedAtRange(eq(42L), any(), any()))
                .thenReturn(List.of(o));

        Invoice inv = new Invoice();
        inv.setId(99L);
        inv.setTotalDue(new BigDecimal("80.00"));
        when(invoiceRepository.findByOrder_Id(9L)).thenReturn(Optional.of(inv));
        when(paymentRepository.sumByInvoiceId(99L)).thenReturn(new BigDecimal("30.00"));

        MerchantOrderHistoryResponse r = reportingService.getMerchantOrderHistory(42L, START, END);
        assertEquals(ReportingService.PAYMENT_PARTIAL, r.getRows().get(0).getPaymentStatus());
    }

    @Test
    @DisplayName("RPT-US2: payment status PENDING when no payments")
    void merchantHistory_paymentPending() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(merchantUser42()));
        Order o = new Order();
        o.setId(9L);
        o.setPlacedAt(Instant.now());
        o.setTotalDue(new BigDecimal("80.00"));
        when(orderRepository.findByMerchantIdAndPlacedAtRange(eq(42L), any(), any()))
                .thenReturn(List.of(o));

        Invoice inv = new Invoice();
        inv.setId(99L);
        inv.setTotalDue(new BigDecimal("80.00"));
        when(invoiceRepository.findByOrder_Id(9L)).thenReturn(Optional.of(inv));
        when(paymentRepository.sumByInvoiceId(99L)).thenReturn(BigDecimal.ZERO);

        MerchantOrderHistoryResponse r = reportingService.getMerchantOrderHistory(42L, START, END);
        assertEquals(ReportingService.PAYMENT_PENDING, r.getRows().get(0).getPaymentStatus());
    }

    @Test
    @DisplayName("RPT-US2: unknown merchant id throws 404")
    void merchantHistory_unknownMerchant_throws404() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportingService.getMerchantOrderHistory(999L, START, END));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("RPT-US2: non-MERCHANT user id throws 404")
    void merchantHistory_nonMerchantUser_throws404() {
        User admin = new User();
        admin.setId(3L);
        admin.setRole(User.Role.ADMIN);
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportingService.getMerchantOrderHistory(3L, START, END));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("RPT-US3: activity report maps header and line items with computed line totals")
    void merchantActivity_headerAndLines() {
        User u = merchantUser42();
        u.setName("Test Pharmacy");
        u.setUsername("testpharm");
        when(userRepository.findById(42L)).thenReturn(Optional.of(u));

        MerchantProfile profile = new MerchantProfile();
        profile.setUser(u);
        profile.setContactEmail("buyer@example.com");
        profile.setContactPhone("01234 567890");
        profile.setAddressLine("10 High Street");
        profile.setVatRegistrationNumber("GB999");
        when(merchantProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));

        Product prod = new Product("SKU1", "Paracetamol 500mg", new BigDecimal("2.00"), 100);
        prod.setId(1L);
        OrderItem line = new OrderItem();
        line.setId(50L);
        line.setProduct(prod);
        line.setQuantity(4);
        line.setUnitPriceAtOrder(new BigDecimal("2.50"));

        Order order = new Order();
        order.setId(200L);
        order.setPlacedAt(Instant.parse("2026-04-15T12:00:00Z"));
        order.setStatus(Order.OrderStatus.ACCEPTED);
        order.setGrossTotal(new BigDecimal("10.00"));
        order.setFixedDiscountAmount(new BigDecimal("1.00"));
        order.setFlexibleCreditApplied(BigDecimal.ZERO);
        order.setTotalDue(new BigDecimal("9.00"));
        line.setOrder(order);
        order.getItems().add(line);

        when(orderRepository.findByMerchantIdAndPlacedAtRangeWithItemsAndProducts(
                eq(42L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(order));

        MerchantActivityReportResponse r = reportingService.getMerchantActivityReport(42L, START, END);

        assertEquals("buyer@example.com", r.getHeader().getContactEmail());
        assertEquals("GB999", r.getHeader().getVatRegistrationNumber());
        assertEquals(1, r.getOrders().size());
        assertEquals(200L, r.getOrders().get(0).getOrderId());
        assertEquals(1, r.getOrders().get(0).getLines().size());
        assertEquals("Paracetamol 500mg", r.getOrders().get(0).getLines().get(0).getDescription());
        assertEquals(4, r.getOrders().get(0).getLines().get(0).getQuantity());
        assertEquals(0, new BigDecimal("10.00").compareTo(r.getOrders().get(0).getLines().get(0).getLineTotal()));
        assertEquals(0, new BigDecimal("9.00").compareTo(r.getOrders().get(0).getTotalDue()));
    }

    @Test
    @DisplayName("RPT-US3: missing merchant profile throws 404")
    void merchantActivity_profileMissing_throws404() {
        when(userRepository.findById(42L)).thenReturn(Optional.of(merchantUser42()));
        when(merchantProfileRepository.findByUserId(42L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> reportingService.getMerchantActivityReport(42L, START, END));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("RPT-US3: empty order list still returns header")
    void merchantActivity_noOrders_returnsHeaderOnly() {
        User u = merchantUser42();
        u.setName("Solo");
        u.setUsername("solo");
        when(userRepository.findById(42L)).thenReturn(Optional.of(u));
        MerchantProfile profile = new MerchantProfile();
        profile.setUser(u);
        profile.setContactEmail("solo@example.com");
        profile.setContactPhone("000");
        profile.setAddressLine("Addr");
        when(merchantProfileRepository.findByUserId(42L)).thenReturn(Optional.of(profile));
        when(orderRepository.findByMerchantIdAndPlacedAtRangeWithItemsAndProducts(
                eq(42L), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        MerchantActivityReportResponse r = reportingService.getMerchantActivityReport(42L, START, END);

        assertEquals("solo@example.com", r.getHeader().getContactEmail());
        assertEquals(0, r.getOrders().size());
    }
}
