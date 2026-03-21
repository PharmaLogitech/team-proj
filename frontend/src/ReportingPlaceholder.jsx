/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Placeholder component for the Reporting package (IPOS-SA-RPRT).      ║
 * ║                                                                              ║
 * ║  WHY:  This stub proves that RBAC navigation works correctly:               ║
 * ║        - MANAGER and ADMIN can see and navigate to this page.              ║
 * ║        - MERCHANT cannot see this nav item or access this page.            ║
 * ║                                                                              ║
 * ║        The actual reporting features will be built in future iterations:    ║
 * ║          - RPT-US1: Sales Turnover Reporting                               ║
 * ║          - RPT-US2: Merchant History Tracking                              ║
 * ║          - RPT-US3: Individual Merchant Activity Report                    ║
 * ║          - RPT-US4: Global Invoice Monitoring                              ║
 * ║          - RPT-US5: Stock Turnover Analysis                                ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        Visible to: MANAGER, ADMIN                                          ║
 * ║        Hidden from: MERCHANT                                               ║
 * ║        Enforced by: rbac.js + App.jsx nav guards                           ║
 * ║        Backend: /api/reports/** → hasAnyRole("MANAGER", "ADMIN")           ║
 * ║                                                                              ║
 * ║  HOW TO REPLACE:                                                             ║
 * ║        When implementing RPT-US1–US5, replace this component with a real   ║
 * ║        Reporting.jsx that includes date pickers, report generation, and     ║
 * ║        data tables.  The RBAC wiring in App.jsx and rbac.js will continue  ║
 * ║        to work — just swap the component.                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

function ReportingPlaceholder() {
  return (
    <div className="placeholder-page">
      <h2>Reporting (IPOS-SA-RPRT)</h2>
      <p className="status-message">
        Reporting features are coming in a future iteration.
      </p>
      <div style={{ marginTop: "1.5rem", color: "#6b7280", fontSize: "0.9rem" }}>
        <p><strong>Planned reports:</strong></p>
        <ul style={{ marginTop: "0.5rem", paddingLeft: "1.5rem" }}>
          <li>Sales Turnover Report (RPT-US1)</li>
          <li>Merchant Order History (RPT-US2)</li>
          <li>Individual Merchant Activity (RPT-US3)</li>
          <li>Global Invoice Monitoring (RPT-US4)</li>
          <li>Stock Turnover Analysis (RPT-US5)</li>
        </ul>
      </div>
    </div>
  );
}

export default ReportingPlaceholder;
