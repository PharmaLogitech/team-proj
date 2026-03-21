/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Request DTO for the month-close flexible discount settlement.        ║
 * ║                                                                              ║
 * ║  WHY (brief §i — flexible discount):                                         ║
 * ║        At the end of each calendar month, the system computes the discount  ║
 * ║        rebate for FLEXIBLE-plan merchants.  The manager or admin triggers   ║
 * ║        this by specifying which month to close and how to disburse:         ║
 * ║          APPLY_CREDIT — Credited to the merchant's balance for next orders. ║
 * ║          CHEQUE       — Recorded as a pending cheque payout.               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import jakarta.validation.constraints.NotBlank;

public record CloseMonthRequest(

        /* Format: "YYYY-MM", e.g. "2026-03". */
        @NotBlank(message = "Year-month is required (format: YYYY-MM).")
        String yearMonth,

        /* "APPLY_CREDIT" or "CHEQUE". */
        @NotBlank(message = "Settlement mode is required (APPLY_CREDIT or CHEQUE).")
        String settlementMode

) {}
