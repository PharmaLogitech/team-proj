import { useState, useEffect, useCallback } from "react";
import {
  getCommercialApplications,
  getCommercialApplication,
  approveCommercialApplication,
  rejectCommercialApplication,
} from "./api.js";

const DEMO_ID = -1;

const DEMO_LIST_ITEM = {
  id: DEMO_ID,
  externalReferenceId: "DEMO-PU-2026-001",
  status: "PENDING",
  createdAt: "2026-04-14T12:00:00Z",
};

const DEMO_DETAIL = {
  id: DEMO_ID,
  externalReferenceId: "DEMO-PU-2026-001",
  status: "PENDING",
  payloadJson: JSON.stringify(
    {
      companyName: "Demo Pharmacy Ltd",
      contactName: "Alex Example",
      contactEmail: "alex.example@demopharm.co.uk",
      contactPhone: "+44 20 7946 0958",
      summary:
        "Demo commercial application payload from IPOS-PU. This screen is shown when the list API fails or returns no rows, so admins can still preview the workflow.",
    },
    null,
    2
  ),
  generatedEmailBody: null,
  rejectionReason: null,
  createdAt: "2026-04-14T12:00:00Z",
  decidedAt: null,
  decidedByUsername: null,
  callbackUrl: null,
};

function formatPayloadPreview(payloadJson) {
  if (!payloadJson) return "(empty)";
  try {
    return JSON.stringify(JSON.parse(payloadJson), null, 2);
  } catch {
    return payloadJson;
  }
}

function CommercialApplication() {
  const [list, setList] = useState([]);
  const [statusFilter, setStatusFilter] = useState("");
  const [loading, setLoading] = useState(true);
  const [demoMode, setDemoMode] = useState(false);
  const [demoReason, setDemoReason] = useState(null);

  const [selectedId, setSelectedId] = useState(null);
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);

  const [approveOpen, setApproveOpen] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [actionBusy, setActionBusy] = useState(false);

  const loadList = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const data = await getCommercialApplications({
        status: statusFilter || undefined,
      });
      if (!data || data.length === 0) {
        setList([DEMO_LIST_ITEM]);
        setDemoMode(true);
        setDemoReason("No applications returned — showing demo example.");
        setSelectedId(DEMO_ID);
        setDetail(DEMO_DETAIL);
      } else {
        setList(data);
        setDemoMode(false);
        setDemoReason(null);
        setSelectedId(null);
        setDetail(null);
      }
    } catch (err) {
      setList([DEMO_LIST_ITEM]);
      setDemoMode(true);
      setDemoReason(`Could not load applications (${err.message}). Showing demo example.`);
      setSelectedId(DEMO_ID);
      setDetail(DEMO_DETAIL);
      setIsError(false);
    } finally {
      setLoading(false);
    }
  }, [statusFilter]);

  useEffect(() => {
    loadList();
  }, [loadList]);

  const selectRow = async (row) => {
    setSelectedId(row.id);
    setMessage(null);
    if (demoMode && row.id === DEMO_ID) {
      setDetail(DEMO_DETAIL);
      return;
    }
    setDetailLoading(true);
    try {
      const d = await getCommercialApplication(row.id);
      setDetail(d);
    } catch (err) {
      setMessage("Failed to load detail: " + err.message);
      setIsError(true);
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  };

  const handleApprove = async () => {
    if (demoMode && selectedId === DEMO_ID) {
      setMessage("Demo data only — configure IPOS-PU inbound integration to process real applications.");
      setIsError(false);
      setApproveOpen(false);
      return;
    }
    setActionBusy(true);
    setMessage(null);
    try {
      const result = await approveCommercialApplication(selectedId, {});
      setMessage(
        `Approved. Webhook to IPOS-PU: ${result.puWebhookStatus}` +
          (result.puWebhookError ? ` (${result.puWebhookError})` : "")
      );
      setIsError(false);
      setApproveOpen(false);
      await loadList();
      if (selectedId != null) {
        const d = await getCommercialApplication(selectedId);
        setDetail(d);
      }
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    } finally {
      setActionBusy(false);
    }
  };

  const handleReject = async () => {
    if (!rejectReason.trim()) {
      setMessage("Please provide a reason for rejection.");
      setIsError(true);
      return;
    }
    if (demoMode && selectedId === DEMO_ID) {
      setMessage("Demo data only — configure IPOS-PU inbound integration to process real applications.");
      setIsError(false);
      setRejectOpen(false);
      setRejectReason("");
      return;
    }
    setActionBusy(true);
    setMessage(null);
    try {
      const result = await rejectCommercialApplication(selectedId, rejectReason.trim());
      setMessage(
        `Rejected. Webhook to IPOS-PU: ${result.puWebhookStatus}` +
          (result.puWebhookError ? ` (${result.puWebhookError})` : "")
      );
      setIsError(false);
      setRejectOpen(false);
      setRejectReason("");
      await loadList();
      if (selectedId != null) {
        const d = await getCommercialApplication(selectedId);
        setDetail(d);
      }
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    } finally {
      setActionBusy(false);
    }
  };

  const pendingDetail = detail && detail.status === "PENDING";

  return (
    <div className="commercial-application-page">
      <h2>Commercial Application (IPOS-PU)</h2>
      <p className="subtitle" style={{ marginTop: "-0.5rem", marginBottom: "1rem" }}>
        Review applications submitted from IPOS-PU. Approve to generate email text for IPOS-PU, or reject
        with an explanation.
      </p>

      {demoMode && (
        <div
          className="status-message"
          style={{
            background: "#fffbeb",
            border: "1px solid #fbbf24",
            color: "#92400e",
            marginBottom: "1rem",
          }}
        >
          <strong>Demo data (integration unavailable)</strong>
          {demoReason ? ` — ${demoReason}` : ""}
        </div>
      )}

      {message && (
        <p className={`status-message ${isError ? "error" : "success"}`} style={{ marginBottom: "1rem" }}>
          {message}
        </p>
      )}

      <div className="form-group" style={{ marginBottom: "1rem" }}>
        <label htmlFor="ca-status">Status filter </label>
        <select
          id="ca-status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          style={{ marginLeft: "0.5rem" }}
        >
          <option value="">All</option>
          <option value="PENDING">Pending</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
        </select>
        <button type="button" className="submit-btn" style={{ marginLeft: "1rem" }} onClick={() => loadList()}>
          Refresh
        </button>
      </div>

      {loading ? (
        <p className="status-message">Loading…</p>
      ) : (
        <table className="product-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>External ref (IPOS-PU)</th>
              <th>Status</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {list.map((row) => (
              <tr
                key={row.id}
                onClick={() => selectRow(row)}
                style={{
                  cursor: "pointer",
                  background: selectedId === row.id ? "#e0f2fe" : undefined,
                }}
              >
                <td>{row.id}</td>
                <td>{row.externalReferenceId}</td>
                <td>{row.status}</td>
                <td>{row.createdAt ? new Date(row.createdAt).toLocaleString() : "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {detailLoading && <p className="status-message">Loading detail…</p>}

      {detail && !detailLoading && (
        <section style={{ marginTop: "1.5rem" }}>
          <h3>Application detail</h3>
          <dl style={{ display: "grid", gridTemplateColumns: "160px 1fr", gap: "0.35rem 1rem" }}>
            <dt>Internal ID</dt>
            <dd>{detail.id}</dd>
            <dt>External ref</dt>
            <dd>{detail.externalReferenceId}</dd>
            <dt>Status</dt>
            <dd>{detail.status}</dd>
            <dt>Callback URL</dt>
            <dd>{detail.callbackUrl || "—"}</dd>
            <dt>Decided</dt>
            <dd>
              {detail.decidedAt
                ? `${new Date(detail.decidedAt).toLocaleString()} (${detail.decidedByUsername || "—"})`
                : "—"}
            </dd>
          </dl>

          <h4 style={{ marginTop: "1rem" }}>Payload from IPOS-PU</h4>
          <pre
            style={{
              background: "#f8fafc",
              border: "1px solid #e2e8f0",
              padding: "0.75rem",
              overflow: "auto",
              maxHeight: "280px",
              fontSize: "0.85rem",
            }}
          >
            {formatPayloadPreview(detail.payloadJson)}
          </pre>

          {detail.generatedEmailBody && (
            <>
              <h4>Generated email body (for IPOS-PU)</h4>
              <pre
                style={{
                  background: "#f0fdf4",
                  border: "1px solid #86efac",
                  padding: "0.75rem",
                  whiteSpace: "pre-wrap",
                  fontSize: "0.85rem",
                }}
              >
                {detail.generatedEmailBody}
              </pre>
            </>
          )}

          {detail.rejectionReason && (
            <>
              <h4>Rejection reason sent to IPOS-PU</h4>
              <pre
                style={{
                  background: "#fef2f2",
                  border: "1px solid #fecaca",
                  padding: "0.75rem",
                  whiteSpace: "pre-wrap",
                  fontSize: "0.85rem",
                }}
              >
                {detail.rejectionReason}
              </pre>
            </>
          )}

          {pendingDetail && (
            <div style={{ marginTop: "1rem", display: "flex", gap: "0.75rem", flexWrap: "wrap" }}>
              <button type="button" className="submit-btn" onClick={() => setApproveOpen(true)}>
                Approve
              </button>
              <button
                type="button"
                className="logout-btn"
                onClick={() => setRejectOpen(true)}
                style={{ background: "#b91c1c", color: "#fff", borderColor: "#991b1b" }}
              >
                Reject
              </button>
            </div>
          )}
          {pendingDetail && demoMode && selectedId === DEMO_ID && (
            <p className="status-message" style={{ marginTop: "0.75rem" }}>
              Demo only — actions above will not call the server for this row.
            </p>
          )}
        </section>
      )}

      {approveOpen && (
        <div className="modal-overlay" style={modalOverlayStyle}>
          <div className="modal-content" style={modalBoxStyle}>
            <h3>Approve application</h3>
            <p>The server will generate the email body from the application payload (unless your API is extended for overrides).</p>
            <p>Confirm approval and notify IPOS-PU if a webhook URL is configured.</p>
            <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end", marginTop: "1rem" }}>
              <button type="button" className="logout-btn" onClick={() => setApproveOpen(false)} disabled={actionBusy}>
                Cancel
              </button>
              <button type="button" className="submit-btn" onClick={handleApprove} disabled={actionBusy}>
                {actionBusy ? "Working…" : "Confirm approve"}
              </button>
            </div>
          </div>
        </div>
      )}

      {rejectOpen && (
        <div className="modal-overlay" style={modalOverlayStyle}>
          <div className="modal-content" style={modalBoxStyle}>
            <h3>Reject application</h3>
            <p>Provide a reason. It will be stored and sent to IPOS-PU via webhook when configured.</p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              rows={5}
              style={{ width: "100%", marginTop: "0.5rem", boxSizing: "border-box" }}
              placeholder="Reason for rejection (required)"
            />
            <div style={{ display: "flex", gap: "0.5rem", justifyContent: "flex-end", marginTop: "1rem" }}>
              <button
                type="button"
                className="logout-btn"
                onClick={() => {
                  setRejectOpen(false);
                  setRejectReason("");
                }}
                disabled={actionBusy}
              >
                Cancel
              </button>
              <button type="button" className="submit-btn" onClick={handleReject} disabled={actionBusy}>
                {actionBusy ? "Working…" : "Confirm reject"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

const modalOverlayStyle = {
  position: "fixed",
  inset: 0,
  background: "rgba(0,0,0,0.4)",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  zIndex: 1000,
};

const modalBoxStyle = {
  background: "#fff",
  padding: "1.25rem",
  borderRadius: "8px",
  maxWidth: "480px",
  width: "90%",
  boxShadow: "0 10px 40px rgba(0,0,0,0.2)",
};

export default CommercialApplication;
