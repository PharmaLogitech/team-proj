/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Reporting page for the IPOS-SA-RPRT package.                         ║
 * ║                                                                              ║
 * ║  WHY:  CAT-US10 requires a low-stock report accessible through RPRT.        ║
 * ║        This component fetches GET /api/reports/low-stock on mount and       ║
 * ║        displays a real-time table of products whose current availability    ║
 * ║        is strictly below their configured minimum threshold.                ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        Visible to: MANAGER, ADMIN                                          ║
 * ║        Hidden from: MERCHANT                                               ║
 * ║        Enforced by: rbac.js + App.jsx nav guards                           ║
 * ║        Backend: /api/reports/** → hasAnyRole("MANAGER", "ADMIN")           ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Add sections for RPT-US1–US5 (sales turnover, merchant history,     ║
 * ║        etc.) below the low-stock section. Each section fetches its own      ║
 * ║        endpoint and renders independently.                                  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

import { useState, useEffect, useCallback } from "react";
import { getLowStockReport } from "./api.js";

function ReportingPlaceholder() {
  const [lowStockProducts, setLowStockProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchReport = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getLowStockReport();
      setLowStockProducts(data);
    } catch (err) {
      setError(err.message || "Failed to load low-stock report.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchReport();
  }, [fetchReport]);

  return (
    <div className="placeholder-page">
      <h2>Reporting (IPOS-SA-RPRT)</h2>

      {/* ── Low-Stock Report (CAT-US10) ──────────────────────────────── */}
      <section style={{ marginTop: "1.5rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>
          Low-Stock Report
          <button
            type="button"
            onClick={fetchReport}
            disabled={loading}
            style={{
              marginLeft: "1rem",
              fontSize: "0.8rem",
              padding: "0.25rem 0.75rem",
              cursor: loading ? "wait" : "pointer",
            }}
          >
            {loading ? "Refreshing…" : "Refresh"}
          </button>
        </h3>

        {error && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {error}
          </p>
        )}

        {!loading && !error && lowStockProducts.length === 0 && (
          <p className="status-message" style={{ color: "#16a34a" }}>
            All products are above their minimum stock thresholds.
          </p>
        )}

        {!loading && !error && lowStockProducts.length > 0 && (
          <table className="product-table" style={{ marginTop: "0.5rem" }}>
            <thead>
              <tr>
                <th>Product ID</th>
                <th>Description</th>
                <th>Current Stock</th>
                <th>Min Threshold</th>
                <th>Shortfall</th>
              </tr>
            </thead>
            <tbody>
              {lowStockProducts.map((p) => (
                <tr key={p.id}>
                  <td>{p.productCode ?? "—"}</td>
                  <td>{p.description}</td>
                  <td style={{ color: "#dc2626", fontWeight: 600 }}>
                    {p.availabilityCount}
                  </td>
                  <td>{p.minStockThreshold}</td>
                  <td style={{ color: "#dc2626" }}>
                    {p.minStockThreshold - p.availabilityCount}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      {/* ── Planned Reports (RPT-US1–US5) ────────────────────────────── */}
      <div style={{ marginTop: "2rem", color: "#6b7280", fontSize: "0.9rem" }}>
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
