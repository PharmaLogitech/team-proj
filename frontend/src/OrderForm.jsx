/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: React component for order placement and tracking (IPOS-SA-ORD).    ║
 * ║                                                                              ║
 * ║  ORD-US1 (Place a New Order):                                               ║
 * ║        Merchants can add one or multiple line items (product + quantity)    ║
 * ║        and submit them as a single order. The backend validates stock,     ║
 * ║        applies discounts, and records the order with status ACCEPTED.      ║
 * ║                                                                              ║
 * ║  ORD-US2 (Real-time Order Tracking):                                       ║
 * ║        All users see an orders table below the form.                       ║
 * ║        - MERCHANT: "My Orders" — own orders only (backend-scoped).        ║
 * ║        - MANAGER / ADMIN: "All Orders" with merchant name column.         ║
 * ║        Staff see status controls to advance the lifecycle:                 ║
 * ║          ACCEPTED → PROCESSING → DISPATCHED (forward-only).               ║
 * ║          ACCEPTED | PROCESSING → CANCELLED.                               ║
 * ║        The table auto-refreshes via polling every 15 seconds plus a       ║
 * ║        manual Refresh button.                                             ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        Page restricted via rbac.js to roles with IPOS-SA-ORD access.      ║
 * ║        Backend: PUT /api/orders/{id}/status → MANAGER/ADMIN only.         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect, useCallback } from "react";
import { getProducts, placeOrder, getOrders, updateOrderStatus } from "./api.js";

/* ── Status display helpers ───────────────────────────────────────────────── */

const STATUS_LABELS = {
  ACCEPTED: "Accepted",
  PROCESSING: "Processing",
  DISPATCHED: "Dispatched",
  CANCELLED: "Cancelled",
  PENDING: "Accepted",
  CONFIRMED: "Accepted",
};

const STATUS_COLORS = {
  ACCEPTED: "#2563eb",
  PROCESSING: "#d97706",
  DISPATCHED: "#16a34a",
  CANCELLED: "#dc2626",
  PENDING: "#2563eb",
  CONFIRMED: "#2563eb",
};

function formatStatus(status) {
  return STATUS_LABELS[status] || status;
}

/* ── Valid next-status transitions (mirrors backend VALID_TRANSITIONS) ───── */

const NEXT_STATUSES = {
  ACCEPTED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["DISPATCHED", "CANCELLED"],
};

/* ═══════════════════════════════════════════════════════════════════════════ */

function OrderForm({ onOrderPlaced, currentUser }) {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  /* ── Multi-line order items (ORD-US1) ────────────────────────────────── */
  const [lineItems, setLineItems] = useState([{ productId: "", quantity: 1 }]);

  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);
  const [orderResult, setOrderResult] = useState(null);

  /* ── Orders table (ORD-US2) ──────────────────────────────────────────── */
  const [orders, setOrders] = useState([]);
  const [ordersLoading, setOrdersLoading] = useState(true);

  const isStaff =
    currentUser?.role === "MANAGER" || currentUser?.role === "ADMIN";

  /* Load products for the dropdown */
  useEffect(() => {
    async function fetchProducts() {
      try {
        const data = await getProducts();
        setProducts(data);
        if (data.length > 0) {
          setLineItems([{ productId: data[0].id, quantity: 1 }]);
        }
      } catch (err) {
        setMessage("Failed to load products: " + err.message);
        setIsError(true);
      } finally {
        setLoading(false);
      }
    }
    fetchProducts();
  }, []);

  /* Fetch orders (role-scoped by backend) */
  const refreshOrders = useCallback(async () => {
    try {
      const data = await getOrders();
      setOrders(data);
    } catch {
      /* silent — orders table will remain stale until next poll */
    } finally {
      setOrdersLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshOrders();
  }, [refreshOrders]);

  /* Polling: refetch orders every 15 seconds while the component is mounted */
  useEffect(() => {
    const interval = setInterval(refreshOrders, 15000);
    return () => clearInterval(interval);
  }, [refreshOrders]);

  /* ── Line-item management ────────────────────────────────────────────── */

  const updateLineItem = (index, field, value) => {
    setLineItems((prev) =>
      prev.map((item, i) =>
        i === index ? { ...item, [field]: value } : item
      )
    );
  };

  const addLineItem = () => {
    const defaultPid = products.length > 0 ? products[0].id : "";
    setLineItems((prev) => [...prev, { productId: defaultPid, quantity: 1 }]);
  };

  const removeLineItem = (index) => {
    if (lineItems.length <= 1) return;
    setLineItems((prev) => prev.filter((_, i) => i !== index));
  };

  /* ── Place order ─────────────────────────────────────────────────────── */

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setMessage(null);
    setOrderResult(null);

    const items = lineItems.map((li) => ({
      productId: Number(li.productId),
      quantity: Number(li.quantity),
    }));

    try {
      const result = await placeOrder(currentUser.id, items);
      setOrderResult(result);
      setMessage("Order placed successfully!");
      setIsError(false);
      refreshOrders();
      setTimeout(() => {
        if (onOrderPlaced) onOrderPlaced();
      }, 3000);
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    } finally {
      setSubmitting(false);
    }
  };

  /* ── Status update (staff only) ──────────────────────────────────────── */

  const handleStatusChange = async (orderId, newStatus) => {
    try {
      await updateOrderStatus(orderId, newStatus);
      refreshOrders();
    } catch (err) {
      alert("Status update failed: " + err.message);
    }
  };

  /* ── Render ──────────────────────────────────────────────────────────── */

  if (loading) return <p className="status-message">Loading form data...</p>;

  if (products.length === 0) {
    return (
      <div className="order-form">
        <h2>Place an Order</h2>
        <p className="status-message">
          No products found. An administrator must add products first.
        </p>
      </div>
    );
  }

  return (
    <div className="order-form">
      {/* ── Order Placement Form (ORD-US1) ──────────────────────────────── */}
      <h2>Place an Order</h2>

      <p style={{ color: "#6b7280", fontSize: "0.9rem", marginBottom: "1rem" }}>
        Ordering as: <strong>{currentUser.name}</strong> ({currentUser.role})
      </p>

      <form onSubmit={handleSubmit}>
        {lineItems.map((li, index) => (
          <div
            key={index}
            className="form-group"
            style={{
              display: "flex",
              gap: "0.5rem",
              alignItems: "flex-end",
              marginBottom: "0.5rem",
            }}
          >
            <div style={{ flex: 3 }}>
              {index === 0 && <label>Product</label>}
              <select
                value={li.productId}
                onChange={(e) =>
                  updateLineItem(index, "productId", e.target.value)
                }
                style={{ width: "100%" }}
              >
                {products.map((product) => {
                  const stockLabel =
                    currentUser?.role === "MERCHANT"
                      ? product.availabilityStatus === "AVAILABLE"
                        ? "Available"
                        : "Out of Stock"
                      : `${product.availabilityCount} in stock`;
                  return (
                    <option key={product.id} value={product.id}>
                      {product.productCode
                        ? `[${product.productCode}] `
                        : ""}
                      {product.description} — £
                      {Number(product.price).toFixed(2)} ({stockLabel})
                    </option>
                  );
                })}
              </select>
            </div>

            <div style={{ flex: 1 }}>
              {index === 0 && <label>Qty</label>}
              <input
                type="number"
                min="1"
                value={li.quantity}
                onChange={(e) =>
                  updateLineItem(index, "quantity", e.target.value)
                }
                style={{ width: "100%" }}
              />
            </div>

            <button
              type="button"
              onClick={() => removeLineItem(index)}
              disabled={lineItems.length <= 1}
              className="submit-btn"
              style={{
                backgroundColor: lineItems.length <= 1 ? "#d1d5db" : "#dc2626",
                padding: "0.4rem 0.75rem",
                minWidth: "auto",
              }}
            >
              ✕
            </button>
          </div>
        ))}

        <div style={{ display: "flex", gap: "0.5rem", marginTop: "0.5rem" }}>
          <button
            type="button"
            onClick={addLineItem}
            className="submit-btn"
            style={{ backgroundColor: "#6b7280" }}
          >
            + Add Item
          </button>
          <button type="submit" disabled={submitting} className="submit-btn">
            {submitting ? "Placing Order..." : "Place Order"}
          </button>
        </div>
      </form>

      {message && (
        <p className={`status-message ${isError ? "error" : "success"}`}>
          {message}
        </p>
      )}

      {/* ── Order Financial Breakdown (brief §i) ────────────────────────── */}
      {orderResult && orderResult.grossTotal != null && (
        <div className="order-breakdown">
          <h3>Order Summary</h3>
          <table className="data-table">
            <tbody>
              <tr>
                <td>Gross Total</td>
                <td>£{Number(orderResult.grossTotal).toFixed(2)}</td>
              </tr>
              {Number(orderResult.fixedDiscountAmount) > 0 && (
                <tr>
                  <td>Fixed Discount</td>
                  <td>
                    -£{Number(orderResult.fixedDiscountAmount).toFixed(2)}
                  </td>
                </tr>
              )}
              {Number(orderResult.flexibleCreditApplied) > 0 && (
                <tr>
                  <td>Flexible Credit Applied</td>
                  <td>
                    -£{Number(orderResult.flexibleCreditApplied).toFixed(2)}
                  </td>
                </tr>
              )}
              <tr className="total-row">
                <td>
                  <strong>Total Due</strong>
                </td>
                <td>
                  <strong>£{Number(orderResult.totalDue).toFixed(2)}</strong>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {/* ── Orders Table (ORD-US2) ──────────────────────────────────────── */}
      <hr style={{ margin: "2rem 0" }} />
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <h2>{isStaff ? "All Orders" : "My Orders"}</h2>
        <button
          onClick={refreshOrders}
          className="submit-btn"
          style={{ backgroundColor: "#6b7280", padding: "0.4rem 1rem" }}
        >
          Refresh
        </button>
      </div>

      {ordersLoading ? (
        <p className="status-message">Loading orders...</p>
      ) : orders.length === 0 ? (
        <p className="status-message">No orders found.</p>
      ) : (
        <table className="data-table" style={{ marginTop: "0.5rem" }}>
          <thead>
            <tr>
              <th>Order #</th>
              {isStaff && <th>Merchant</th>}
              <th>Date</th>
              <th>Items</th>
              <th>Total Due</th>
              <th>Status</th>
              {isStaff && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => {
              const nextStatuses = NEXT_STATUSES[order.status] || [];
              return (
                <tr key={order.id}>
                  <td>{order.id}</td>
                  {isStaff && (
                    <td>{order.merchant?.username || "—"}</td>
                  )}
                  <td>
                    {order.placedAt
                      ? new Date(order.placedAt).toLocaleString()
                      : "—"}
                  </td>
                  <td>
                    {order.items
                      ?.map(
                        (it) =>
                          `${it.product?.description || "Product #" + it.product?.id} ×${it.quantity}`
                      )
                      .join(", ") || "—"}
                  </td>
                  <td>
                    {order.totalDue != null
                      ? `£${Number(order.totalDue).toFixed(2)}`
                      : "—"}
                  </td>
                  <td>
                    <span
                      style={{
                        color: STATUS_COLORS[order.status] || "#374151",
                        fontWeight: 600,
                      }}
                    >
                      {formatStatus(order.status)}
                    </span>
                  </td>
                  {isStaff && (
                    <td>
                      {nextStatuses.length > 0 ? (
                        <div style={{ display: "flex", gap: "0.3rem" }}>
                          {nextStatuses.map((ns) => (
                            <button
                              key={ns}
                              onClick={() => handleStatusChange(order.id, ns)}
                              className="submit-btn"
                              style={{
                                fontSize: "0.75rem",
                                padding: "0.25rem 0.5rem",
                                minWidth: "auto",
                                backgroundColor:
                                  ns === "CANCELLED" ? "#dc2626" : "#2563eb",
                              }}
                            >
                              {formatStatus(ns)}
                            </button>
                          ))}
                        </div>
                      ) : (
                        "—"
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default OrderForm;
