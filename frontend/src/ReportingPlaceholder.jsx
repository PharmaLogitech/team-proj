/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Reporting page — low-stock and operational reports.                 ║
 * ║                                                                              ║
 * ║  WHY:  Printable tables (App.css print rules).                              ║
 * ║                                                                              ║
 * ║  ACCESS: MANAGER, ADMIN — rbac.js + /api/reports/**                         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

import { useState, useEffect, useCallback } from "react";
import {
  getLowStockReport,
  getSalesTurnoverReport,
  getGlobalInvoiceReport,
  getStockTurnoverReport,
  getMerchantOrderHistory,
  getMerchantActivityReport,
  getMerchantProfiles,
  generateDebtorReminders,
} from "./api.js";

function defaultDateRange() {
  const end = new Date();
  const start = new Date(end.getFullYear(), end.getMonth(), 1);
  const toYmd = (d) => d.toISOString().slice(0, 10);
  return { start: toYmd(start), end: toYmd(end) };
}

function formatInstant(iso) {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleString("en-GB", { dateStyle: "short", timeStyle: "short" });
}

function formatMoney(amount) {
  if (amount == null || amount === "") return "—";
  const n = Number(amount);
  if (Number.isNaN(n)) return String(amount);
  return `£${n.toFixed(2)}`;
}

function ReportingPlaceholder() {
  const defaults = defaultDateRange();

  const [lowStockProducts, setLowStockProducts] = useState([]);
  const [lowStockLoading, setLowStockLoading] = useState(true);
  const [lowStockError, setLowStockError] = useState(null);

  const [stStart, setStStart] = useState(defaults.start);
  const [stEnd, setStEnd] = useState(defaults.end);
  const [turnover, setTurnover] = useState(null);
  const [stLoading, setStLoading] = useState(false);
  const [stError, setStError] = useState(null);

  const [merchants, setMerchants] = useState([]);
  const [mhMerchantId, setMhMerchantId] = useState("");
  const [mhStart, setMhStart] = useState(defaults.start);
  const [mhEnd, setMhEnd] = useState(defaults.end);
  const [history, setHistory] = useState(null);
  const [mhLoading, setMhLoading] = useState(false);
  const [mhError, setMhError] = useState(null);

  const [maMerchantId, setMaMerchantId] = useState("");
  const [maStart, setMaStart] = useState(defaults.start);
  const [maEnd, setMaEnd] = useState(defaults.end);
  const [activity, setActivity] = useState(null);
  const [maLoading, setMaLoading] = useState(false);
  const [maError, setMaError] = useState(null);

  const [giStart, setGiStart] = useState(defaults.start);
  const [giEnd, setGiEnd] = useState(defaults.end);
  const [globalInvoices, setGlobalInvoices] = useState(null);
  const [giLoading, setGiLoading] = useState(false);
  const [giError, setGiError] = useState(null);

  const [stoStart, setStoStart] = useState(defaults.start);
  const [stoEnd, setStoEnd] = useState(defaults.end);
  const [stockTurnover, setStockTurnover] = useState(null);
  const [stoLoading, setStoLoading] = useState(false);
  const [stoError, setStoError] = useState(null);

  const [debtLoading, setDebtLoading] = useState(false);
  const [debtResult, setDebtResult] = useState(null);
  const [debtError, setDebtError] = useState(null);

  const fetchLowStock = useCallback(async () => {
    setLowStockLoading(true);
    setLowStockError(null);
    try {
      const data = await getLowStockReport();
      setLowStockProducts(data);
    } catch (err) {
      setLowStockError(err.message || "Failed to load low-stock report.");
    } finally {
      setLowStockLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchLowStock();
  }, [fetchLowStock]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const list = await getMerchantProfiles();
        if (!cancelled && Array.isArray(list) && list.length > 0) {
          setMerchants(list);
          const firstId = String(list[0].userId);
          setMhMerchantId(firstId);
          setMaMerchantId(firstId);
        }
      } catch {
        /* dropdown stays empty */
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const runSalesTurnover = async () => {
    setStLoading(true);
    setStError(null);
    setTurnover(null);
    try {
      const data = await getSalesTurnoverReport({ start: stStart, end: stEnd });
      setTurnover(data);
    } catch (err) {
      setStError(err.message || "Failed to load sales turnover.");
    } finally {
      setStLoading(false);
    }
  };

  const runMerchantHistory = async () => {
    if (!mhMerchantId) {
      setMhError("Select a merchant.");
      setHistory(null);
      return;
    }
    setMhLoading(true);
    setMhError(null);
    setHistory(null);
    try {
      const data = await getMerchantOrderHistory(Number(mhMerchantId), {
        start: mhStart,
        end: mhEnd,
      });
      setHistory(data);
    } catch (err) {
      setMhError(err.message || "Failed to load order history.");
    } finally {
      setMhLoading(false);
    }
  };

  const runMerchantActivity = async () => {
    if (!maMerchantId) {
      setMaError("Select a merchant.");
      setActivity(null);
      return;
    }
    setMaLoading(true);
    setMaError(null);
    setActivity(null);
    try {
      const data = await getMerchantActivityReport(Number(maMerchantId), {
        start: maStart,
        end: maEnd,
      });
      setActivity(data);
    } catch (err) {
      setMaError(err.message || "Failed to load activity report.");
    } finally {
      setMaLoading(false);
    }
  };

  const runGlobalInvoices = async () => {
    setGiLoading(true);
    setGiError(null);
    setGlobalInvoices(null);
    try {
      const data = await getGlobalInvoiceReport({ start: giStart, end: giEnd });
      setGlobalInvoices(data);
    } catch (err) {
      setGiError(err.message || "Failed to load global invoice report.");
    } finally {
      setGiLoading(false);
    }
  };

  const runDebtorReminders = async () => {
    setDebtLoading(true);
    setDebtError(null);
    setDebtResult(null);
    try {
      const data = await generateDebtorReminders();
      setDebtResult(data);
    } catch (err) {
      setDebtError(err.message || "Failed to generate debtor reminders.");
    } finally {
      setDebtLoading(false);
    }
  };

  const runStockTurnover = async () => {
    setStoLoading(true);
    setStoError(null);
    setStockTurnover(null);
    try {
      const data = await getStockTurnoverReport({ start: stoStart, end: stoEnd });
      setStockTurnover(data);
    } catch (err) {
      setStoError(err.message || "Failed to load stock turnover report.");
    } finally {
      setStoLoading(false);
    }
  };

  return (
    <div className="placeholder-page">
      <h2>Reporting</h2>

      {/* ── Low-Stock Report ───────────────────────────────────────────── */}
      <section style={{ marginTop: "1.5rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>
          Low-Stock Report
          <button
            type="button"
            className="no-print"
            onClick={fetchLowStock}
            disabled={lowStockLoading}
            style={{
              marginLeft: "1rem",
              fontSize: "0.8rem",
              padding: "0.25rem 0.75rem",
              cursor: lowStockLoading ? "wait" : "pointer",
            }}
          >
            {lowStockLoading ? "Refreshing…" : "Refresh"}
          </button>
        </h3>

        {lowStockError && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {lowStockError}
          </p>
        )}

        {!lowStockLoading && !lowStockError && lowStockProducts.length === 0 && (
          <p className="status-message" style={{ color: "#16a34a" }}>
            All products are above their minimum stock thresholds.
          </p>
        )}

        {!lowStockLoading && !lowStockError && lowStockProducts.length > 0 && (
          <table className="product-table" style={{ marginTop: "0.5rem" }}>
            <thead>
              <tr>
                <th>Item ID (range)</th>
                <th>Item ID (no.)</th>
                <th>Description</th>
                <th>Current Stock</th>
                <th>Min Threshold</th>
                <th>Shortfall</th>
              </tr>
            </thead>
            <tbody>
              {lowStockProducts.map((p) => (
                <tr key={p.id}>
                  <td>{p.itemIdRange ?? "—"}</td>
                  <td>{p.itemIdSuffix ?? "—"}</td>
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

      {/* ── Sales Turnover ─────────────────────────────────────────────── */}
      <section style={{ marginTop: "2rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Sales Turnover</h3>
        <div
          className="no-print"
          style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "flex-end", marginBottom: "0.75rem" }}
        >
          <label>
            Start date{" "}
            <input
              type="date"
              value={stStart}
              onChange={(e) => setStStart(e.target.value)}
            />
          </label>
          <label>
            End date{" "}
            <input type="date" value={stEnd} onChange={(e) => setStEnd(e.target.value)} />
          </label>
          <button type="button" onClick={runSalesTurnover} disabled={stLoading}>
            {stLoading ? "Loading…" : "Run report"}
          </button>
          <button type="button" onClick={() => window.print()}>
            Print
          </button>
        </div>
        {stError && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {stError}
          </p>
        )}
        {turnover && (
          <div style={{ marginTop: "0.5rem" }}>
            <p>
              <strong>Total quantity sold:</strong> {turnover.totalQuantitySold}
            </p>
            <p>
              <strong>Total revenue ({turnover.currency || "GBP"}):</strong>{" "}
              {formatMoney(turnover.totalRevenue)}
            </p>
            <p style={{ fontSize: "0.85rem", color: "#6b7280" }}>
              Non-cancelled orders with order date in the selected range (invoiced totals).
            </p>
          </div>
        )}
      </section>

      {/* ── Merchant order history ─────────────────────────────────────── */}
      <section style={{ marginTop: "2rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Merchant Order History</h3>
        <div
          className="no-print"
          style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "flex-end", marginBottom: "0.75rem" }}
        >
          <label>
            Merchant{" "}
            <select
              value={mhMerchantId}
              onChange={(e) => setMhMerchantId(e.target.value)}
              style={{ minWidth: "12rem" }}
            >
              <option value="">— Select —</option>
              {merchants.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.name || m.username || m.contactEmail || `User #${m.userId}`}
                </option>
              ))}
            </select>
          </label>
          <label>
            Start date{" "}
            <input
              type="date"
              value={mhStart}
              onChange={(e) => setMhStart(e.target.value)}
            />
          </label>
          <label>
            End date{" "}
            <input type="date" value={mhEnd} onChange={(e) => setMhEnd(e.target.value)} />
          </label>
          <button type="button" onClick={runMerchantHistory} disabled={mhLoading}>
            {mhLoading ? "Loading…" : "Run report"}
          </button>
          <button type="button" onClick={() => window.print()}>
            Print
          </button>
        </div>
        {mhError && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {mhError}
          </p>
        )}
        {history && history.rows && (
          <table className="product-table" style={{ marginTop: "0.5rem" }}>
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Order date</th>
                <th>Dispatch date</th>
                <th>Total value</th>
                <th>Payment status</th>
              </tr>
            </thead>
            <tbody>
              {history.rows.map((row) => (
                <tr key={row.orderId}>
                  <td>{row.orderId}</td>
                  <td>{formatInstant(row.orderDate)}</td>
                  <td>{formatInstant(row.dispatchDate)}</td>
                  <td>{formatMoney(row.totalValue)}</td>
                  <td>{row.paymentStatus}</td>
                </tr>
              ))}
              <tr style={{ fontWeight: 700 }}>
                <td colSpan={3}>Period total</td>
                <td>{formatMoney(history.periodTotalValue)}</td>
                <td />
              </tr>
            </tbody>
          </table>
        )}
      </section>

      {/* ── Merchant activity detail ───────────────────────────────────── */}
      <section style={{ marginTop: "2rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Merchant Activity</h3>
        <div
          className="no-print"
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: "0.75rem",
            alignItems: "flex-end",
            marginBottom: "0.75rem",
          }}
        >
          <label>
            Merchant{" "}
            <select
              value={maMerchantId}
              onChange={(e) => setMaMerchantId(e.target.value)}
              style={{ minWidth: "12rem" }}
            >
              <option value="">— Select —</option>
              {merchants.map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.name || m.username || m.contactEmail || `User #${m.userId}`}
                </option>
              ))}
            </select>
          </label>
          <label>
            Start date{" "}
            <input type="date" value={maStart} onChange={(e) => setMaStart(e.target.value)} />
          </label>
          <label>
            End date{" "}
            <input type="date" value={maEnd} onChange={(e) => setMaEnd(e.target.value)} />
          </label>
          <button type="button" onClick={runMerchantActivity} disabled={maLoading}>
            {maLoading ? "Loading…" : "Run report"}
          </button>
          <button type="button" onClick={() => window.print()}>
            Print
          </button>
        </div>
        {maError && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {maError}
          </p>
        )}
        {activity && activity.header && (
          <div style={{ marginTop: "1rem" }}>
            <h4 style={{ marginBottom: "0.5rem", fontSize: "1rem" }}>Merchant contact</h4>
            <ul style={{ listStyle: "none", padding: 0, marginBottom: "1.25rem", lineHeight: 1.6 }}>
              <li>
                <strong>Name:</strong> {activity.header.merchantName ?? "—"}
              </li>
              <li>
                <strong>Username:</strong> {activity.header.username ?? "—"}
              </li>
              <li>
                <strong>Email:</strong> {activity.header.contactEmail ?? "—"}
              </li>
              <li>
                <strong>Phone:</strong> {activity.header.contactPhone ?? "—"}
              </li>
              <li>
                <strong>Address:</strong> {activity.header.addressLine ?? "—"}
              </li>
              {activity.header.vatRegistrationNumber && (
                <li>
                  <strong>VAT:</strong> {activity.header.vatRegistrationNumber}
                </li>
              )}
            </ul>

            {activity.orders && activity.orders.length === 0 && (
              <p className="status-message" style={{ color: "#6b7280" }}>
                No orders in this period.
              </p>
            )}

            {activity.orders &&
              activity.orders.map((ord) => (
                <div
                  key={ord.orderId}
                  style={{
                    marginBottom: "1.5rem",
                    padding: "0.75rem",
                    border: "1px solid #e5e7eb",
                    borderRadius: "8px",
                    background: "#fafafa",
                  }}
                >
                  <p style={{ fontWeight: 600, marginBottom: "0.5rem" }}>
                    Order #{ord.orderId}{" "}
                    <span style={{ fontWeight: 400, color: "#6b7280" }}>
                      ({formatInstant(ord.placedAt)}
                      {ord.orderStatus ? ` · ${ord.orderStatus}` : ""})
                    </span>
                  </p>
                  <table className="product-table" style={{ marginTop: "0.35rem", width: "100%" }}>
                    <thead>
                      <tr>
                        <th>Description</th>
                        <th>Qty</th>
                        <th>Unit price</th>
                        <th>Line total</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(ord.lines || []).map((line, idx) => (
                        <tr key={`${ord.orderId}-${idx}`}>
                          <td>{line.description}</td>
                          <td>{line.quantity}</td>
                          <td>{formatMoney(line.unitPrice)}</td>
                          <td>{formatMoney(line.lineTotal)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                  <div style={{ marginTop: "0.75rem", fontSize: "0.9rem" }}>
                    <p>
                      <strong>Gross:</strong> {formatMoney(ord.grossTotal)}
                    </p>
                    <p>
                      <strong>Fixed discount:</strong> {formatMoney(ord.fixedDiscountAmount)}
                    </p>
                    <p>
                      <strong>Flexible credit applied:</strong> {formatMoney(ord.flexibleCreditApplied)}
                    </p>
                    <p style={{ fontWeight: 700 }}>
                      <strong>Total due:</strong> {formatMoney(ord.totalDue)}
                    </p>
                  </div>
                </div>
              ))}
          </div>
        )}
      </section>

      {/* ── Global Invoice Monitoring ─────────────────────────────────── */}
      <section style={{ marginTop: "2rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Global Invoice Monitoring</h3>
        <div
          className="no-print"
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: "0.75rem",
            alignItems: "flex-end",
            marginBottom: "0.75rem",
          }}
        >
          <label>
            Start date{" "}
            <input type="date" value={giStart} onChange={(e) => setGiStart(e.target.value)} />
          </label>
          <label>
            End date{" "}
            <input type="date" value={giEnd} onChange={(e) => setGiEnd(e.target.value)} />
          </label>
          <button type="button" onClick={runGlobalInvoices} disabled={giLoading}>
            {giLoading ? "Loading…" : "Run report"}
          </button>
          <button type="button" onClick={() => window.print()}>
            Print
          </button>
        </div>
        {giError && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {giError}
          </p>
        )}
        {globalInvoices && (
          <div style={{ marginTop: "0.5rem" }}>
            <p style={{ fontSize: "0.85rem", color: "#6b7280", marginBottom: "0.5rem" }}>
              All invoices issued in the selected range (issue date). Payment status matches order
              history semantics (PENDING / PARTIAL / PAID).
            </p>
            <table className="product-table">
              <thead>
                <tr>
                  <th>Merchant ID</th>
                  <th>Username</th>
                  <th>Merchant name</th>
                  <th>Invoice #</th>
                  <th>Invoice id</th>
                  <th>Issued</th>
                  <th>Amount</th>
                  <th>Payment status</th>
                </tr>
              </thead>
              <tbody>
                {(globalInvoices.rows || []).map((row) => (
                  <tr key={`${row.invoiceId}-${row.invoiceNumber}`}>
                    <td>{row.merchantId}</td>
                    <td>{row.merchantUsername ?? "—"}</td>
                    <td>{row.merchantName ?? "—"}</td>
                    <td>{row.invoiceNumber ?? "—"}</td>
                    <td>{row.invoiceId}</td>
                    <td>{formatInstant(row.issuedAt)}</td>
                    <td>{formatMoney(row.amount)}</td>
                    <td>{row.paymentStatus ?? "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(globalInvoices.rows || []).length === 0 && (
              <p style={{ marginTop: "0.5rem", color: "#6b7280" }}>No invoices in this period.</p>
            )}
          </div>
        )}
      </section>

      {/* ── Stock Turnover ─────────────────────────────────────────────── */}
      <section style={{ marginTop: "2rem" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Stock Turnover</h3>

        <div
          className="no-print"
          style={{
            display: "flex",
            flexWrap: "wrap",
            gap: "0.75rem",
            alignItems: "flex-end",
            marginBottom: "0.75rem",
          }}
        >
          <label>
            Start date{" "}
            <input type="date" value={stoStart} onChange={(e) => setStoStart(e.target.value)} />
          </label>
          <label>
            End date{" "}
            <input type="date" value={stoEnd} onChange={(e) => setStoEnd(e.target.value)} />
          </label>
          <button type="button" onClick={runStockTurnover} disabled={stoLoading}>
            {stoLoading ? "Loading…" : "Run report"}
          </button>
          <button type="button" onClick={() => window.print()}>
            Print
          </button>
        </div>
        {stoError && (
          <p className="status-message error" style={{ color: "#dc2626" }}>
            {stoError}
          </p>
        )}
        {stockTurnover && (
          <div style={{ marginTop: "0.5rem" }}>
            <p style={{ fontSize: "0.85rem", color: "#6b7280", marginBottom: "0.5rem" }}>
              Quantities sold from non-cancelled orders with order date in range; quantities received
              from stock deliveries with delivery date in range (inclusive).
            </p>
            <table className="product-table">
              <thead>
                <tr>
                  <th>Product code</th>
                  <th>Product id</th>
                  <th>Qty sold</th>
                  <th>Qty received</th>
                </tr>
              </thead>
              <tbody>
                {(stockTurnover.rows || []).map((row) => (
                  <tr key={row.productId}>
                    <td>{row.productCode ?? "—"}</td>
                    <td>{row.productId}</td>
                    <td>{row.quantitySold}</td>
                    <td>{row.quantityReceived}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {(stockTurnover.rows || []).length === 0 && (
              <p style={{ marginTop: "0.5rem", color: "#6b7280" }}>No sales or deliveries in this period.</p>
            )}
          </div>
        )}
      </section>

      {/* ── Generate Debtor Reminders ──────────────────────────────────── */}
      <section style={{ marginTop: "2rem", paddingTop: "1.5rem", borderTop: "1px solid #e5e7eb" }}>
        <h3 style={{ marginBottom: "0.75rem" }}>Generate Debtor Reminders</h3>
        <p style={{ fontSize: "0.85rem", color: "#6b7280", marginBottom: "1rem" }}>
          Identifies all merchant accounts with outstanding balances and flags them.
          The next time a flagged merchant logs in, they will see a warning banner
          showing the amount they owe.
        </p>
        <button
          type="button"
          onClick={runDebtorReminders}
          disabled={debtLoading}
          className="submit-btn"
        >
          {debtLoading ? "Generating…" : "Generate Reminders"}
        </button>

        {debtError && (
          <p className="status-message error" style={{ color: "#dc2626", marginTop: "0.75rem" }}>
            {debtError}
          </p>
        )}

        {debtResult && (
          <div style={{ marginTop: "1rem" }}>
            <p className="status-message success" style={{ marginBottom: "0.75rem" }}>
              {debtResult.merchantsFlagged === 0
                ? "No merchants have outstanding balances. All reminders have been cleared."
                : `${debtResult.merchantsFlagged} merchant(s) flagged with outstanding balances.`}
            </p>

            {debtResult.merchants && debtResult.merchants.length > 0 && (
              <table className="product-table">
                <thead>
                  <tr>
                    <th>Merchant ID</th>
                    <th>Name</th>
                    <th>Username</th>
                    <th>Outstanding Balance</th>
                  </tr>
                </thead>
                <tbody>
                  {debtResult.merchants.map((m) => (
                    <tr key={m.merchantId}>
                      <td>{m.merchantId}</td>
                      <td>{m.name ?? "—"}</td>
                      <td>{m.username ?? "—"}</td>
                      <td style={{ color: "#dc2626", fontWeight: 600 }}>
                        {formatMoney(m.outstanding)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>
    </div>
  );
}

export default ReportingPlaceholder;
