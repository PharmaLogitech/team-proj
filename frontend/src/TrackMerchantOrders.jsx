import { useState, useEffect, useCallback } from "react";
import { getOrders, updateOrderStatus } from "./api.js";

const STATUS_LABELS = {
  ACCEPTED: "Accepted",
  PROCESSING: "Processing",
  DISPATCHED: "Dispatched",
  DELIVERED: "Delivered",
  CANCELLED: "Cancelled",
  PENDING: "Accepted",
  CONFIRMED: "Accepted",
};

const STATUS_COLORS = {
  ACCEPTED: "#2563eb",
  PROCESSING: "#d97706",
  DISPATCHED: "#16a34a",
  DELIVERED: "#059669",
  CANCELLED: "#dc2626",
  PENDING: "#2563eb",
  CONFIRMED: "#2563eb",
};

function formatStatus(status) {
  return STATUS_LABELS[status] || status;
}

const NEXT_STATUSES = {
  ACCEPTED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["DISPATCHED", "CANCELLED"],
  DISPATCHED: ["DELIVERED"],
};

function hasShippingInfo(order) {
  return (
    order.courierName ||
    order.courierReference ||
    order.dispatchDate ||
    order.expectedDeliveryDate
  );
}

/* ── Dispatch modal: collect shipping details before marking DISPATCHED ── */

function DispatchModal({ orderId, onConfirm, onCancel }) {
  const [courierName, setCourierName] = useState("");
  const [courierReference, setCourierReference] = useState("");
  const [dispatchDate, setDispatchDate] = useState(
    new Date().toISOString().slice(0, 10)
  );
  const [expectedDeliveryDate, setExpectedDeliveryDate] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    await onConfirm({
      courierName: courierName.trim() || undefined,
      courierReference: courierReference.trim() || undefined,
      dispatchDate: dispatchDate || undefined,
      expectedDeliveryDate: expectedDeliveryDate || undefined,
    });
    setSubmitting(false);
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        backgroundColor: "rgba(0,0,0,0.45)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1000,
      }}
    >
      <div
        style={{
          background: "#fff",
          borderRadius: "0.5rem",
          padding: "1.5rem",
          width: "min(450px, 90vw)",
          boxShadow: "0 4px 24px rgba(0,0,0,0.18)",
        }}
      >
        <h3 style={{ marginTop: 0 }}>
          Dispatch Order #{orderId} — Shipping Details
        </h3>
        <form onSubmit={handleSubmit}>
          <div className="form-group" style={{ marginBottom: "0.75rem" }}>
            <label>Courier Name</label>
            <input
              type="text"
              value={courierName}
              onChange={(e) => setCourierName(e.target.value)}
              placeholder="e.g. Royal Mail, DPD, DHL"
              style={{ width: "100%" }}
            />
          </div>
          <div className="form-group" style={{ marginBottom: "0.75rem" }}>
            <label>Courier Reference / Tracking #</label>
            <input
              type="text"
              value={courierReference}
              onChange={(e) => setCourierReference(e.target.value)}
              placeholder="e.g. RM123456789GB"
              style={{ width: "100%" }}
            />
          </div>
          <div
            style={{
              display: "flex",
              gap: "0.75rem",
              marginBottom: "0.75rem",
            }}
          >
            <div className="form-group" style={{ flex: 1 }}>
              <label>Dispatch Date</label>
              <input
                type="date"
                value={dispatchDate}
                onChange={(e) => setDispatchDate(e.target.value)}
                style={{ width: "100%" }}
              />
            </div>
            <div className="form-group" style={{ flex: 1 }}>
              <label>Expected Delivery</label>
              <input
                type="date"
                value={expectedDeliveryDate}
                onChange={(e) => setExpectedDeliveryDate(e.target.value)}
                style={{ width: "100%" }}
              />
            </div>
          </div>
          <div
            style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end" }}
          >
            <button
              type="button"
              onClick={onCancel}
              className="submit-btn"
              style={{ backgroundColor: "#6b7280" }}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="submit-btn"
              disabled={submitting}
            >
              {submitting ? "Dispatching..." : "Confirm Dispatch"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/* ══════════════════════════════════════════════════════════════════════════ */

function TrackMerchantOrders({ currentUser }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);
  const [dispatchOrderId, setDispatchOrderId] = useState(null);

  const isStaff =
    currentUser?.role === "MANAGER" || currentUser?.role === "ADMIN";

  const refreshOrders = useCallback(async () => {
    try {
      const data = await getOrders();
      setOrders(data);
    } catch {
      /* silent — table stays stale until next poll */
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    refreshOrders();
  }, [refreshOrders]);

  useEffect(() => {
    const interval = setInterval(refreshOrders, 15000);
    return () => clearInterval(interval);
  }, [refreshOrders]);

  const handleStatusChange = async (orderId, newStatus) => {
    if (newStatus === "DISPATCHED") {
      setDispatchOrderId(orderId);
      return;
    }
    try {
      await updateOrderStatus(orderId, newStatus);
      refreshOrders();
    } catch (err) {
      alert("Status update failed: " + err.message);
    }
  };

  const handleDispatchConfirm = async (shippingDetails) => {
    try {
      await updateOrderStatus(dispatchOrderId, "DISPATCHED", shippingDetails);
      setDispatchOrderId(null);
      refreshOrders();
    } catch (err) {
      alert("Dispatch failed: " + err.message);
    }
  };

  return (
    <div>
      {dispatchOrderId != null && (
        <DispatchModal
          orderId={dispatchOrderId}
          onConfirm={handleDispatchConfirm}
          onCancel={() => setDispatchOrderId(null)}
        />
      )}

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
        }}
      >
        <h2>{isStaff ? "Track Merchant Orders" : "My Orders"}</h2>
        <button
          onClick={refreshOrders}
          className="submit-btn"
          style={{ backgroundColor: "#6b7280", padding: "0.4rem 1rem" }}
        >
          Refresh
        </button>
      </div>

      {loading ? (
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
              <th>Shipping</th>
              {isStaff && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {orders.map((order) => {
              const nextStatuses = NEXT_STATUSES[order.status] || [];
              const shipped = hasShippingInfo(order);
              return (
                <tr key={order.id}>
                  <td>{order.id}</td>
                  {isStaff && (
                    <td>{order.merchant?.username || "\u2014"}</td>
                  )}
                  <td>
                    {order.placedAt
                      ? new Date(order.placedAt).toLocaleString()
                      : "\u2014"}
                  </td>
                  <td>
                    {order.items
                      ?.map(
                        (it) =>
                          `${it.product?.description || "Product #" + it.product?.id} \u00d7${it.quantity}`
                      )
                      .join(", ") || "\u2014"}
                  </td>
                  <td>
                    {order.totalDue != null
                      ? `\u00a3${Number(order.totalDue).toFixed(2)}`
                      : "\u2014"}
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
                  <td style={{ fontSize: "0.82rem", lineHeight: 1.4 }}>
                    {shipped ? (
                      <div>
                        {order.courierName && (
                          <div>
                            <strong>Courier:</strong> {order.courierName}
                          </div>
                        )}
                        {order.courierReference && (
                          <div>
                            <strong>Ref:</strong> {order.courierReference}
                          </div>
                        )}
                        {order.dispatchDate && (
                          <div>
                            <strong>Shipped:</strong> {order.dispatchDate}
                          </div>
                        )}
                        {order.expectedDeliveryDate && (
                          <div>
                            <strong>ETA:</strong> {order.expectedDeliveryDate}
                          </div>
                        )}
                      </div>
                    ) : (
                      <span style={{ color: "#9ca3af" }}>{"\u2014"}</span>
                    )}
                  </td>
                  {isStaff && (
                    <td>
                      {nextStatuses.length > 0 ? (
                        <div style={{ display: "flex", gap: "0.3rem" }}>
                          {nextStatuses.map((ns) => (
                            <button
                              key={ns}
                              onClick={() =>
                                handleStatusChange(order.id, ns)
                              }
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
                        "\u2014"
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

export default TrackMerchantOrders;
