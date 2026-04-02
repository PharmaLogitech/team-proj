/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for the Reporting package (IPOS-SA-RPRT).            ║
 * ║                                                                              ║
 * ║  WHY:  Centralises all report-generation endpoints under /api/reports.       ║
 * ║        Currently serves the low-stock report (CAT-US10); future RPT-US1–5   ║
 * ║        endpoints can be added here.                                         ║
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

import com.ipos.dto.LowStockProductDto;
import com.ipos.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ProductService productService;

    public ReportController(ProductService productService) {
        this.productService = productService;
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
}
