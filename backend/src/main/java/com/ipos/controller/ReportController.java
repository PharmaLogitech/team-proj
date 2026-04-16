/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for the Reporting package (IPOS-SA-RPRT).            ║
 * ║                                                                              ║
 * ║  WHY:  Centralises all report-generation endpoints under /api/reports.       ║
 * ║        Low-stock (CAT-US10); RPT-US1–US5 report endpoints.                 ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4 — RBAC):                                           ║
 * ║        SecurityConfig.java secures /api/reports/** for MANAGER and ADMIN.   ║
 * ║        No additional matchers needed for new sub-paths.                     ║
 * ║                                                                              ║
 * ║  ENDPOINTS:                                                                   ║
 * ║        GET /api/reports/low-stock → real-time low-stock product list         ║
 * ║                                     (CAT-US10)                               ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Add @GetMapping methods for RPT-US1–US5 (sales turnover, merchant    ║
 * ║        history, etc.) — the /api/reports/** security rule covers them all.  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.dto.GlobalInvoiceReportResponse;
import com.ipos.dto.LowStockProductDto;
import com.ipos.dto.MerchantActivityReportResponse;
import com.ipos.dto.MerchantOrderHistoryResponse;
import com.ipos.dto.SalesTurnoverResponse;
import com.ipos.dto.StockTurnoverReportResponse;
import com.ipos.service.ProductService;
import com.ipos.service.ReportingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ProductService productService;
    private final ReportingService reportingService;

    public ReportController(ProductService productService, ReportingService reportingService) {
        this.productService = productService;
        this.reportingService = reportingService;
    }

    /**
     * GET /api/reports/low-stock — Returns all products whose current stock is
     * strictly below their configured minimum threshold (CAT-US10).
     *
     * <p>The query runs on every request (no caching) to satisfy the real-time
     * requirement: "The report must reflect real-time catalogue data at the
     * moment of generation."
     *
     * <p>Access: MANAGER and ADMIN (enforced by SecurityConfig URL rule
     * on /api/reports/**).
     */
    @GetMapping("/low-stock")
    public List<LowStockProductDto> getLowStockReport() {
        return productService.getLowStockProducts();
    }

    /**
     * GET /api/reports/sales-turnover — RPT-US1 (MANAGER/ADMIN via /api/reports/**).
     */
    @GetMapping("/sales-turnover")
    public SalesTurnoverResponse getSalesTurnover(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        return reportingService.getSalesTurnover(start, end);
    }

    /**
     * GET /api/reports/invoices — RPT-US4 (MANAGER/ADMIN via /api/reports/**).
     */
    @GetMapping("/invoices")
    public GlobalInvoiceReportResponse getGlobalInvoiceReport(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        return reportingService.getGlobalInvoiceReport(start, end);
    }

    /**
     * GET /api/reports/stock-turnover — RPT-US5 (MANAGER/ADMIN via /api/reports/**).
     */
    @GetMapping("/stock-turnover")
    public StockTurnoverReportResponse getStockTurnover(
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        return reportingService.getStockTurnover(start, end);
    }

    /**
     * GET /api/reports/merchants/{merchantId}/order-history — RPT-US2.
     */
    @GetMapping("/merchants/{merchantId}/order-history")
    public MerchantOrderHistoryResponse getMerchantOrderHistory(
            @PathVariable Long merchantId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        return reportingService.getMerchantOrderHistory(merchantId, start, end);
    }

    /**
     * GET /api/reports/merchants/{merchantId}/activity — RPT-US3.
     */
    @GetMapping("/merchants/{merchantId}/activity")
    public MerchantActivityReportResponse getMerchantActivityReport(
            @PathVariable Long merchantId,
            @RequestParam LocalDate start,
            @RequestParam LocalDate end) {
        return reportingService.getMerchantActivityReport(merchantId, start, end);
    }

    /**
     * POST /api/reports/generate-debtor-reminders — Flags all merchants with
     * outstanding balances so they see a warning banner on next login.
     * MANAGER/ADMIN via /api/reports/** security rule.
     */
    @PostMapping("/generate-debtor-reminders")
    public ReportingService.DebtorReminderSummary generateDebtorReminders() {
        return reportingService.generateDebtorReminders();
    }
}
