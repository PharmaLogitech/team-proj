/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: React component for invoices, payments, and balance (ORD-US3/5/6). ║
 * ║                                                                              ║
 * ║  ORD-US3 (Financial Balance Oversight):                                     ║
 * ║        MERCHANT sees a balance summary at the top: outstanding total,      ║
 * ║        oldest unpaid due date, and days elapsed since payment was due.     ║
 * ║                                                                              ║
 * ║  ORD-US5 (Invoices):                                                        ║
 * ║        Invoice listing table. MERCHANT sees own invoices; staff see all.   ║
 * ║        Clicking an invoice shows detail with line items.                   ║
 * ║                                                                              ║
 * ║  ORD-US6 (Payments):                                                        ║
 * ║        ADMIN can record payments against invoices using a form that        ║
 * ║        appears when "Record Payment" is clicked. Supports Bank Transfer,   ║
 * ║        Card, and Cheque methods.                                            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect, useCallback } from "react";
import { useAuth } from "./auth/AuthContext.jsx";
import {
  getInvoices,
  getInvoiceDetail,
  recordPayment,
  getMerchantBalance,
} from "./api.js";

/* UK standard VAT rate. Displayed values treat Total Due as VAT-inclusive:
   net = totalDue / (1 + VAT_RATE), vat = totalDue - net. */
const VAT_RATE = 0.20;

/** Sum of all payments recorded against an invoice. */
function sumPayments(invoice) {
  if (!invoice?.payments) return 0;
  return invoice.payments.reduce((acc, p) => acc + Number(p.amount || 0), 0);
}

/** Remaining balance = Total Due - sum(payments), floored at zero. */
function remainingBalance(invoice) {
  const remaining = Number(invoice.totalDue || 0) - sumPayments(invoice);
  return remaining > 0 ? remaining : 0;
}

/** VAT breakdown computed from Total Due (VAT-inclusive). */
function vatBreakdown(totalDue) {
  const total = Number(totalDue || 0);
  const net = total / (1 + VAT_RATE);
  const vat = total - net;
  return { net, vat };
}

function Invoices() {
  const { user } = useAuth();
  const isMerchant = user?.role === "MERCHANT";
  const isAdmin = user?.role === "ADMIN";

  const [invoices, setInvoices] = useState([]);
  const [balance, setBalance] = useState(null);
  const [selectedInvoice, setSelectedInvoice] = useState(null);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  /* Payment form state (ADMIN only). */
  const [payingInvoiceId, setPayingInvoiceId] = useState(null);
  const [payAmount, setPayAmount] = useState("");
  const [payMethod, setPayMethod] = useState("BANK_TRANSFER");

  const refreshInvoices = useCallback(async () => {
    try {
      const data = await getInvoices();
      setInvoices(data);
    } catch (e) {
      setError(e.message);
    }
  }, []);

  const refreshBalance = useCallback(async () => {
    if (!isMerchant) return;
    try {
      const data = await getMerchantBalance();
      setBalance(data);
    } catch {
      /* Balance is supplementary — don't block the page. */
    }
  }, [isMerchant]);

  useEffect(() => {
    refreshInvoices();
    refreshBalance();
  }, [refreshInvoices, refreshBalance]);

  const handleViewDetail = async (invoiceId) => {
    if (selectedInvoice?.id === invoiceId) {
      setSelectedInvoice(null);
      return;
    }
    try {
      const data = await getInvoiceDetail(invoiceId);
      setSelectedInvoice(data);
    } catch (e) {
      setError(e.message);
    }
  };

  const handleRecordPayment = async (e) => {
    e.preventDefault();
    setError("");
    setSuccess("");
    try {
      await recordPayment(payingInvoiceId, parseFloat(payAmount), payMethod);
      setSuccess("Payment recorded successfully.");
      setPayingInvoiceId(null);
      setPayAmount("");
      setPayMethod("BANK_TRANSFER");
      refreshInvoices();
      refreshBalance();
    } catch (err) {
      setError(err.message);
    }
  };

  return (
    <div>
      <h2>{isMerchant ? "My Invoices & Balance" : "All Invoices"}</h2>

      {error && (
        <p style={{ color: "#dc2626", marginBottom: "0.75rem" }}>{error}</p>
      )}
      {success && (
        <p style={{ color: "#16a34a", marginBottom: "0.75rem" }}>{success}</p>
      )}

      {/* ── Balance Summary (MERCHANT only, ORD-US3) ─────────────────── */}
      {isMerchant && balance && (
        <div
          style={{
            background: "#f0f9ff",
            border: "1px solid #bae6fd",
            borderRadius: "8px",
            padding: "1rem 1.5rem",
            marginBottom: "1.5rem",
          }}
        >
          <h3 style={{ margin: "0 0 0.5rem 0", color: "#0c4a6e" }}>
            Outstanding Balance
          </h3>
          <p style={{ fontSize: "1.75rem", fontWeight: 700, margin: "0 0 0.25rem 0" }}>
            {balance.currency === "GBP" ? "£" : balance.currency}
            {Number(balance.outstandingTotal).toFixed(2)}
          </p>
          {balance.oldestUnpaidDueDate && (
            <p style={{ fontSize: "0.9rem", color: "#64748b", margin: 0 }}>
              {balance.daysElapsedSinceDue > 0
                ? `Oldest unpaid invoice was due ${balance.daysElapsedSinceDue} day${
                    balance.daysElapsedSinceDue !== 1 ? "s" : ""
                  } ago (${balance.oldestUnpaidDueDate})`
                : `Next payment due: ${balance.oldestUnpaidDueDate}`}
            </p>
          )}
          {!balance.oldestUnpaidDueDate && balance.outstandingTotal === 0 && (
            <p style={{ fontSize: "0.9rem", color: "#16a34a", margin: 0 }}>
              All invoices are fully paid.
            </p>
          )}
        </div>
      )}

      {/* ── Invoice List Table ────────────────────────────────────────── */}
      {invoices.length === 0 ? (
        <p style={{ color: "#64748b" }}>No invoices found.</p>
      ) : (
        <table className="product-table" style={{ width: "100%" }}>
          <thead>
            <tr>
              <th>Invoice #</th>
              {!isMerchant && <th>Merchant</th>}
              <th>Issued</th>
              <th>Due Date</th>
              <th style={{ textAlign: "right" }}>Total Due</th>
              <th style={{ textAlign: "right" }}>Paid</th>
              <th style={{ textAlign: "right" }}>Remaining</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {invoices.map((inv) => {
              const paid = sumPayments(inv);
              const remaining = remainingBalance(inv);
              const fullyPaid = remaining === 0 && paid > 0;
              return (
              <tr key={inv.id}>
                <td style={{ fontFamily: "monospace" }}>{inv.invoiceNumber}</td>
                {!isMerchant && <td>{inv.merchantName}</td>}
                <td>{inv.issuedAt ? new Date(inv.issuedAt).toLocaleDateString() : "—"}</td>
                <td>{inv.dueDate || "—"}</td>
                <td style={{ textAlign: "right", fontWeight: 600 }}>
                  £{Number(inv.totalDue).toFixed(2)}
                </td>
                <td style={{ textAlign: "right" }}>£{paid.toFixed(2)}</td>
                <td style={{ textAlign: "right", fontWeight: 600, color: fullyPaid ? "#16a34a" : "#dc2626" }}>
                  {fullyPaid ? "PAID" : `£${remaining.toFixed(2)}`}
                </td>
                <td>
                  <button
                    type="button"
                    onClick={() => handleViewDetail(inv.id)}
                    style={{ marginRight: "0.5rem", cursor: "pointer" }}
                  >
                    {selectedInvoice?.id === inv.id ? "Hide" : "View"}
                  </button>
                  {isAdmin && (
                    <button
                      type="button"
                      onClick={() => {
                        setPayingInvoiceId(
                          payingInvoiceId === inv.id ? null : inv.id
                        );
                        setPayAmount("");
                        setError("");
                        setSuccess("");
                      }}
                      style={{ cursor: "pointer" }}
                    >
                      Record Payment
                    </button>
                  )}
                </td>
              </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {/* ── Invoice Detail Panel ──────────────────────────────────────── */}
      {selectedInvoice && (
        <div
          style={{
            marginTop: "1.5rem",
            border: "1px solid #e2e8f0",
            borderRadius: "8px",
            padding: "1.25rem",
            background: "#fafafa",
          }}
        >
          <h3 style={{ marginTop: 0 }}>
            Invoice {selectedInvoice.invoiceNumber}
          </h3>
          <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "0.5rem", fontSize: "0.9rem", marginBottom: "1rem" }}>
            <div><strong>Merchant:</strong> {selectedInvoice.merchantName}</div>
            <div><strong>Email:</strong> {selectedInvoice.merchantEmail}</div>
            <div><strong>Address:</strong> {selectedInvoice.merchantAddress}</div>
            <div><strong>Phone:</strong> {selectedInvoice.merchantPhone}</div>
            {selectedInvoice.merchantVat && (
              <div><strong>VAT Reg #:</strong> {selectedInvoice.merchantVat}</div>
            )}
            <div><strong>Due Date:</strong> {selectedInvoice.dueDate}</div>
          </div>

          <table className="product-table" style={{ width: "100%", fontSize: "0.9rem" }}>
            <thead>
              <tr>
                <th>#</th>
                <th>Description</th>
                <th style={{ textAlign: "right" }}>Qty</th>
                <th style={{ textAlign: "right" }}>Unit Price</th>
                <th style={{ textAlign: "right" }}>Line Total</th>
              </tr>
            </thead>
            <tbody>
              {(selectedInvoice.lines || []).map((line) => (
                <tr key={line.id}>
                  <td>{line.lineNumber}</td>
                  <td>{line.description}</td>
                  <td style={{ textAlign: "right" }}>{line.quantity}</td>
                  <td style={{ textAlign: "right" }}>£{Number(line.unitPrice).toFixed(2)}</td>
                  <td style={{ textAlign: "right" }}>£{Number(line.lineTotal).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>

          {(() => {
            const { net, vat } = vatBreakdown(selectedInvoice.totalDue);
            const paid = sumPayments(selectedInvoice);
            const remaining = remainingBalance(selectedInvoice);
            const fullyPaid = remaining === 0 && paid > 0;
            return (
              <div style={{ marginTop: "0.75rem", fontSize: "0.9rem" }}>
                <div><strong>Gross Total:</strong> £{Number(selectedInvoice.grossTotal).toFixed(2)}</div>
                {Number(selectedInvoice.fixedDiscountAmount) > 0 && (
                  <div><strong>Fixed Discount:</strong> -£{Number(selectedInvoice.fixedDiscountAmount).toFixed(2)}</div>
                )}
                {Number(selectedInvoice.flexibleCreditApplied) > 0 && (
                  <div><strong>Flexible Credit:</strong> -£{Number(selectedInvoice.flexibleCreditApplied).toFixed(2)}</div>
                )}
                <div style={{ marginTop: "0.5rem", paddingTop: "0.5rem", borderTop: "1px dashed #cbd5e1" }}>
                  <div><strong>Net (excl. VAT):</strong> £{net.toFixed(2)}</div>
                  <div><strong>VAT ({(VAT_RATE * 100).toFixed(0)}%):</strong> £{vat.toFixed(2)}</div>
                </div>
                <div style={{ fontWeight: 700, fontSize: "1rem", marginTop: "0.25rem" }}>
                  <strong>Total Due:</strong> £{Number(selectedInvoice.totalDue).toFixed(2)}
                </div>
                <div style={{ marginTop: "0.5rem", paddingTop: "0.5rem", borderTop: "1px solid #e2e8f0" }}>
                  <div><strong>Paid to Date:</strong> £{paid.toFixed(2)}</div>
                  <div style={{ fontWeight: 700, fontSize: "1rem", color: fullyPaid ? "#16a34a" : "#dc2626" }}>
                    <strong>Remaining Balance:</strong> {fullyPaid ? "PAID IN FULL" : `£${remaining.toFixed(2)}`}
                  </div>
                </div>
              </div>
            );
          })()}

          {/* Show existing payments. */}
          {selectedInvoice.payments && selectedInvoice.payments.length > 0 && (
            <div style={{ marginTop: "1rem" }}>
              <h4 style={{ marginBottom: "0.5rem" }}>Payments</h4>
              <table className="product-table" style={{ width: "100%", fontSize: "0.85rem" }}>
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>Method</th>
                    <th style={{ textAlign: "right" }}>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {selectedInvoice.payments.map((p) => (
                    <tr key={p.id}>
                      <td>{new Date(p.recordedAt).toLocaleDateString()}</td>
                      <td>{p.method.replace("_", " ")}</td>
                      <td style={{ textAlign: "right" }}>£{Number(p.amount).toFixed(2)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* ── Payment Recording Form (ADMIN only, ORD-US6) ─────────────── */}
      {isAdmin && payingInvoiceId && (
        <div
          style={{
            marginTop: "1.5rem",
            border: "1px solid #d1d5db",
            borderRadius: "8px",
            padding: "1.25rem",
            background: "#f9fafb",
          }}
        >
          <h3 style={{ marginTop: 0 }}>
            Record Payment — Invoice #{invoices.find((i) => i.id === payingInvoiceId)?.invoiceNumber}
          </h3>
          <form onSubmit={handleRecordPayment}>
            <div style={{ marginBottom: "0.75rem" }}>
              <label htmlFor="pay-amount" style={{ display: "block", marginBottom: "0.25rem", fontWeight: 600 }}>
                Amount (£)
              </label>
              <input
                id="pay-amount"
                type="number"
                step="0.01"
                min="0.01"
                value={payAmount}
                onChange={(e) => setPayAmount(e.target.value)}
                required
                style={{ padding: "0.4rem", width: "200px" }}
              />
            </div>
            <div style={{ marginBottom: "0.75rem" }}>
              <label htmlFor="pay-method" style={{ display: "block", marginBottom: "0.25rem", fontWeight: 600 }}>
                Method
              </label>
              <select
                id="pay-method"
                value={payMethod}
                onChange={(e) => setPayMethod(e.target.value)}
                style={{ padding: "0.4rem" }}
              >
                <option value="BANK_TRANSFER">Bank Transfer</option>
                <option value="CARD">Card</option>
                <option value="CHEQUE">Cheque</option>
              </select>
            </div>
            <button type="submit" style={{ padding: "0.5rem 1.5rem", cursor: "pointer" }}>
              Submit Payment
            </button>
            <button
              type="button"
              onClick={() => setPayingInvoiceId(null)}
              style={{ marginLeft: "0.5rem", padding: "0.5rem 1rem", cursor: "pointer" }}
            >
              Cancel
            </button>
          </form>
        </div>
      )}

      <div style={{ marginTop: "1rem" }}>
        <button
          type="button"
          onClick={() => { refreshInvoices(); refreshBalance(); }}
          style={{ cursor: "pointer", padding: "0.4rem 1rem" }}
        >
          Refresh
        </button>
      </div>
    </div>
  );
}

export default Invoices;
