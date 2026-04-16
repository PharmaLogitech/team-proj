package com.ipos.service;

import com.ipos.dto.GlobalInvoiceReportResponse;
import com.ipos.dto.GlobalInvoiceRowDto;
import com.ipos.dto.MerchantActivityHeaderDto;
import com.ipos.dto.MerchantActivityLineDto;
import com.ipos.dto.MerchantActivityOrderDto;
import com.ipos.dto.MerchantActivityReportResponse;
import com.ipos.dto.MerchantOrderHistoryResponse;
import com.ipos.dto.MerchantOrderHistoryRowDto;
import com.ipos.dto.SalesTurnoverResponse;
import com.ipos.dto.StockTurnoverReportResponse;
import com.ipos.dto.StockTurnoverRowDto;
import com.ipos.entity.Invoice;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.Order;
import com.ipos.entity.OrderItem;
import com.ipos.entity.User;
import com.ipos.repository.InvoiceGlobalReportProjection;
import com.ipos.repository.InvoiceRepository;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.OrderItemQtySoldProjection;
import com.ipos.repository.OrderItemRepository;
import com.ipos.repository.OrderRepository;
import com.ipos.repository.PaymentRepository;
import com.ipos.repository.StockDeliveryQtyReceivedProjection;
import com.ipos.repository.StockDeliveryRepository;
import com.ipos.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operational reports for IPOS-SA-RPRT (RPT-US1–US5, including global invoices and stock turnover).
 *
 * <p>Date ranges are interpreted in {@link #REPORT_ZONE} (start day 00:00 through end day
 * 23:59:59.999 end-exclusive as [start, end+1)).
 */
@Service
public class ReportingService {

    /** UK calendar day boundaries for report date filters. */
    public static final ZoneId REPORT_ZONE = ZoneId.of("Europe/London");

    public static final String CURRENCY_GBP = "GBP";

    public static final String PAYMENT_PENDING = "PENDING";
    public static final String PAYMENT_PARTIAL = "PARTIAL";
    public static final String PAYMENT_PAID = "PAID";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final UserRepository userRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final StockDeliveryRepository stockDeliveryRepository;

    public ReportingService(OrderRepository orderRepository,
                            OrderItemRepository orderItemRepository,
                            UserRepository userRepository,
                            InvoiceRepository invoiceRepository,
                            PaymentRepository paymentRepository,
                            MerchantProfileRepository merchantProfileRepository,
                            StockDeliveryRepository stockDeliveryRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.userRepository = userRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
        this.merchantProfileRepository = merchantProfileRepository;
        this.stockDeliveryRepository = stockDeliveryRepository;
    }

    /**
     * RPT-US1 — Total quantities sold and revenue (sum of order totalDue) for non-cancelled
     * orders with {@link Order#getPlacedAt()} in the inclusive date range.
     */
    @Transactional(readOnly = true)
    public SalesTurnoverResponse getSalesTurnover(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start and end dates are required.");
        }
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start date must not be after end date.");
        }
        Instant from = start.atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        Long qtySum = orderItemRepository.sumQuantityInPeriodExcludingStatus(
                from, to, Order.OrderStatus.CANCELLED);
        long totalQty = qtySum != null ? qtySum : 0L;

        BigDecimal revenue = orderRepository.sumTotalDueInPeriodExcludingStatus(
                from, to, Order.OrderStatus.CANCELLED);
        if (revenue == null) {
            revenue = BigDecimal.ZERO;
        }

        return new SalesTurnoverResponse(totalQty, revenue, CURRENCY_GBP);
    }

    /**
     * RPT-US4 — Every invoice issued in the date range across all merchants, with payment status.
     */
    @Transactional(readOnly = true)
    public GlobalInvoiceReportResponse getGlobalInvoiceReport(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start and end dates are required.");
        }
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start date must not be after end date.");
        }
        Instant from = start.atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        List<InvoiceGlobalReportProjection> raw = invoiceRepository.findGlobalInvoiceReport(from, to);
        List<GlobalInvoiceRowDto> rows = new ArrayList<>(raw.size());
        for (InvoiceGlobalReportProjection p : raw) {
            BigDecimal paid = p.getPaidSum();
            if (paid == null) {
                paid = BigDecimal.ZERO;
            }
            BigDecimal totalDue = p.getTotalDue();
            GlobalInvoiceRowDto dto = new GlobalInvoiceRowDto();
            dto.setMerchantId(p.getMerchantId());
            dto.setMerchantUsername(p.getMerchantUsername());
            dto.setMerchantName(p.getMerchantName());
            dto.setInvoiceId(p.getInvoiceId());
            dto.setInvoiceNumber(p.getInvoiceNumber());
            dto.setIssuedAt(p.getIssuedAt());
            dto.setAmount(totalDue);
            dto.setPaymentStatus(resolvePaymentStatus(totalDue, paid));
            rows.add(dto);
        }
        return new GlobalInvoiceReportResponse(rows);
    }

    /**
     * RPT-US5 — Per product: sum of quantities sold (non-cancelled orders in period) vs sum of
     * stock deliveries (business delivery date in inclusive date range).
     */
    @Transactional(readOnly = true)
    public StockTurnoverReportResponse getStockTurnover(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start and end dates are required.");
        }
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start date must not be after end date.");
        }
        Instant from = start.atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        List<OrderItemQtySoldProjection> soldRows = orderItemRepository.sumQuantitySoldByProductExcludingStatus(
                from, to, Order.OrderStatus.CANCELLED);
        List<StockDeliveryQtyReceivedProjection> recvRows = stockDeliveryRepository.sumQuantityReceivedByProductBetween(
                start, end);

        Map<Long, StockTurnoverRowDto> byProductId = new HashMap<>();
        for (OrderItemQtySoldProjection p : soldRows) {
            long qty = p.getQtySold() != null ? p.getQtySold() : 0L;
            StockTurnoverRowDto row = new StockTurnoverRowDto();
            row.setProductId(p.getProductId());
            row.setProductCode(p.getProductCode());
            row.setQuantitySold(qty);
            row.setQuantityReceived(0L);
            byProductId.put(p.getProductId(), row);
        }
        for (StockDeliveryQtyReceivedProjection p : recvRows) {
            long qty = p.getQtyReceived() != null ? p.getQtyReceived() : 0L;
            StockTurnoverRowDto row = byProductId.get(p.getProductId());
            if (row == null) {
                row = new StockTurnoverRowDto();
                row.setProductId(p.getProductId());
                row.setProductCode(p.getProductCode());
                row.setQuantitySold(0L);
                row.setQuantityReceived(qty);
                byProductId.put(p.getProductId(), row);
            } else {
                row.setQuantityReceived(qty);
            }
        }

        List<StockTurnoverRowDto> list = new ArrayList<>(byProductId.values());
        list.sort(Comparator.comparing(StockTurnoverRowDto::getProductCode,
                Comparator.nullsLast(String::compareToIgnoreCase)));
        return new StockTurnoverReportResponse(list);
    }

    /**
     * RPT-US2 — Order history for one merchant: all orders placed in the date range
     * (inclusive), with payment status derived from invoice payments.
     */
    @Transactional(readOnly = true)
    public MerchantOrderHistoryResponse getMerchantOrderHistory(Long merchantId, LocalDate start, LocalDate end) {
        if (merchantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "merchant id is required.");
        }
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start and end dates are required.");
        }
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start date must not be after end date.");
        }

        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found."));
        if (merchant.getRole() != User.Role.MERCHANT) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a merchant account.");
        }

        Instant from = start.atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        List<Order> orders = orderRepository.findByMerchantIdAndPlacedAtRange(merchantId, from, to);
        List<MerchantOrderHistoryRowDto> rows = new ArrayList<>();
        BigDecimal periodTotal = BigDecimal.ZERO;

        for (Order order : orders) {
            Optional<Invoice> invOpt = invoiceRepository.findByOrder_Id(order.getId());
            BigDecimal totalDue = invOpt.map(Invoice::getTotalDue)
                    .orElse(order.getTotalDue() != null ? order.getTotalDue() : BigDecimal.ZERO);
            BigDecimal paid = invOpt.map(inv -> paymentRepository.sumByInvoiceId(inv.getId()))
                    .orElse(BigDecimal.ZERO);
            if (paid == null) {
                paid = BigDecimal.ZERO;
            }

            String status = resolvePaymentStatus(totalDue, paid);
            periodTotal = periodTotal.add(totalDue);

            rows.add(new MerchantOrderHistoryRowDto(
                    order.getId(),
                    order.getPlacedAt(),
                    order.getDispatchedAt(),
                    totalDue,
                    status));
        }

        return new MerchantOrderHistoryResponse(rows, periodTotal);
    }

    /**
     * RPT-US3 — Detailed activity: merchant contact header plus each order with line items
     * and discount breakdown.
     */
    @Transactional(readOnly = true)
    public MerchantActivityReportResponse getMerchantActivityReport(Long merchantId, LocalDate start, LocalDate end) {
        if (merchantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "merchant id is required.");
        }
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start and end dates are required.");
        }
        if (start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start date must not be after end date.");
        }

        User merchant = userRepository.findById(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found."));
        if (merchant.getRole() != User.Role.MERCHANT) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User is not a merchant account.");
        }

        MerchantProfile profile = merchantProfileRepository.findByUserId(merchantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found."));

        Instant from = start.atStartOfDay(REPORT_ZONE).toInstant();
        Instant to = end.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();

        List<Order> orders = orderRepository.findByMerchantIdAndPlacedAtRangeWithItemsAndProducts(
                merchantId, from, to);

        MerchantActivityReportResponse response = new MerchantActivityReportResponse();
        response.setHeader(buildActivityHeader(profile));

        List<MerchantActivityOrderDto> orderDtos = new ArrayList<>();
        for (Order order : orders) {
            orderDtos.add(toActivityOrderDto(order));
        }
        response.setOrders(orderDtos);
        return response;
    }

    private static MerchantActivityHeaderDto buildActivityHeader(MerchantProfile profile) {
        MerchantActivityHeaderDto h = new MerchantActivityHeaderDto();
        User u = profile.getUser();
        h.setUserId(u.getId());
        h.setMerchantName(u.getName());
        h.setUsername(u.getUsername());
        h.setContactEmail(profile.getContactEmail());
        h.setContactPhone(profile.getContactPhone());
        h.setAddressLine(profile.getAddressLine());
        h.setVatRegistrationNumber(profile.getVatRegistrationNumber());
        return h;
    }

    private static MerchantActivityOrderDto toActivityOrderDto(Order order) {
        MerchantActivityOrderDto dto = new MerchantActivityOrderDto();
        dto.setOrderId(order.getId());
        dto.setPlacedAt(order.getPlacedAt());
        dto.setOrderStatus(order.getStatus() != null ? order.getStatus().name() : null);
        dto.setGrossTotal(order.getGrossTotal());
        dto.setFixedDiscountAmount(order.getFixedDiscountAmount());
        dto.setFlexibleCreditApplied(order.getFlexibleCreditApplied());
        dto.setTotalDue(order.getTotalDue());

        List<OrderItem> items = new ArrayList<>(order.getItems());
        items.sort(Comparator.comparing(OrderItem::getId, Comparator.nullsLast(Long::compareTo)));

        List<MerchantActivityLineDto> lines = new ArrayList<>();
        for (OrderItem item : items) {
            BigDecimal unit = item.getUnitPriceAtOrder() != null ? item.getUnitPriceAtOrder() : BigDecimal.ZERO;
            int qty = item.getQuantity() != null ? item.getQuantity() : 0;
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            String description = item.getProduct() != null && item.getProduct().getDescription() != null
                    ? item.getProduct().getDescription()
                    : "—";
            lines.add(new MerchantActivityLineDto(description, qty, unit, lineTotal));
        }
        dto.setLines(lines);
        return dto;
    }

    static String resolvePaymentStatus(BigDecimal totalDue, BigDecimal paid) {
        BigDecimal due = totalDue != null ? totalDue : BigDecimal.ZERO;
        BigDecimal p = paid != null ? paid : BigDecimal.ZERO;
        if (due.signum() == 0) {
            return PAYMENT_PAID;
        }
        if (p.signum() == 0) {
            return PAYMENT_PENDING;
        }
        if (p.compareTo(due) >= 0) {
            return PAYMENT_PAID;
        }
        return PAYMENT_PARTIAL;
    }

    /**
     * Generate debtor reminders: for every merchant, compute outstanding balance
     * and persist it on MerchantProfile so the merchant sees a warning on login.
     *
     * @return summary DTO with the count of merchants flagged and their details.
     */
    @Transactional
    public DebtorReminderSummary generateDebtorReminders() {
        List<MerchantProfile> profiles = merchantProfileRepository.findAll();
        List<DebtorReminderRow> flagged = new ArrayList<>();

        for (MerchantProfile profile : profiles) {
            Long merchantId = profile.getUser().getId();
            BigDecimal totalInvoiced = invoiceRepository.sumTotalDueByMerchantId(merchantId);
            BigDecimal totalPaid = invoiceRepository.sumPaymentsByMerchantId(merchantId);
            BigDecimal outstanding = totalInvoiced.subtract(totalPaid);

            if (outstanding.compareTo(BigDecimal.ZERO) > 0) {
                profile.setDebtReminderOutstanding(outstanding);
                flagged.add(new DebtorReminderRow(
                        merchantId,
                        profile.getUser().getName(),
                        profile.getUser().getUsername(),
                        outstanding));
            } else {
                profile.setDebtReminderOutstanding(null);
            }
            merchantProfileRepository.save(profile);
        }

        return new DebtorReminderSummary(flagged.size(), flagged);
    }

    public record DebtorReminderRow(Long merchantId, String name, String username, BigDecimal outstanding) {}
    public record DebtorReminderSummary(int merchantsFlagged, List<DebtorReminderRow> merchants) {}
}
