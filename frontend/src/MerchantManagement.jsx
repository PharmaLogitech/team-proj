/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Manager/Admin page for viewing and editing merchant profiles.        ║
 * ║                                                                              ║
 * ║  WHY (ACC-US6, brief §iii):                                                  ║
 * ║        "A manager's account … can also access the merchant accounts and     ║
 * ║        alter their credit limits, discount plans and change the state of    ║
 * ║        an 'in default' account to either 'normal' or 'suspended'."         ║
 * ║                                                                              ║
 * ║        This component provides:                                             ║
 * ║          1. A table listing all merchant profiles with key fields.          ║
 * ║          2. An "Edit" mode per merchant for changing credit limit,          ║
 * ║             discount plan type/params, and standing (with transition rules).║
 * ║          3. A "Month Close" section for triggering the flexible discount    ║
 * ║             settlement.                                                     ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL:                                                             ║
 * ║        Visible to: MANAGER and ADMIN (IPOS-SA-MER package).               ║
 * ║        Enforced by: rbac.js + App.jsx nav guards.                          ║
 * ║        Backend: /api/merchant-profiles/** → hasAnyRole(MANAGER, ADMIN).    ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { getMerchantProfiles, updateMerchantProfile, closeMonth } from "./api.js";

function MerchantManagement() {
  const [profiles, setProfiles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);

  /* Tracks which merchant (by userId) is currently being edited. */
  const [editingId, setEditingId] = useState(null);
  const [editForm, setEditForm] = useState({});

  /* Month-close form state. */
  const [closeYearMonth, setCloseYearMonth] = useState("");
  const [closeMode, setCloseMode] = useState("APPLY_CREDIT");
  const [closing, setClosing] = useState(false);

  /* ── Load merchant profiles on mount ────────────────────────────────────── */
  useEffect(() => {
    fetchProfiles();
  }, []);

  async function fetchProfiles() {
    try {
      const data = await getMerchantProfiles();
      setProfiles(data);
    } catch (err) {
      setMessage("Failed to load merchant profiles: " + err.message);
      setIsError(true);
    } finally {
      setLoading(false);
    }
  }

  /* ── Edit mode management ───────────────────────────────────────────────── */

  const startEdit = (profile) => {
    setEditingId(profile.userId);
    setEditForm({
      creditLimit: profile.creditLimit,
      discountPlanType: profile.discountPlanType,
      fixedDiscountPercent: profile.fixedDiscountPercent || "",
      flexibleTiersJson: profile.flexibleTiersJson || "",
      standing: profile.standing,
    });
    setMessage(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditForm({});
  };

  const saveEdit = async (userId) => {
    setMessage(null);
    try {
      const payload = {};

      /* Only include fields that changed. */
      const original = profiles.find((p) => p.userId === userId);

      if (String(editForm.creditLimit) !== String(original.creditLimit)) {
        payload.creditLimit = Number(editForm.creditLimit);
      }

      if (editForm.discountPlanType !== original.discountPlanType) {
        payload.discountPlanType = editForm.discountPlanType;
        if (editForm.discountPlanType === "FIXED") {
          payload.fixedDiscountPercent = Number(editForm.fixedDiscountPercent);
        } else {
          payload.flexibleTiersJson = editForm.flexibleTiersJson;
        }
      }

      if (editForm.standing !== original.standing) {
        payload.standing = editForm.standing;
      }

      if (Object.keys(payload).length === 0) {
        setMessage("No changes to save.");
        setIsError(false);
        cancelEdit();
        return;
      }

      await updateMerchantProfile(userId, payload);
      setMessage("Profile updated successfully.");
      setIsError(false);
      cancelEdit();
      fetchProfiles();
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    }
  };

  /* ── Month close handler ────────────────────────────────────────────────── */

  const handleCloseMonth = async (e) => {
    e.preventDefault();
    setClosing(true);
    setMessage(null);

    try {
      const result = await closeMonth(closeYearMonth, closeMode);
      setMessage(
        result.message +
        " Settlements created: " + result.settlementsCreated + "."
      );
      setIsError(false);
      fetchProfiles();
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    } finally {
      setClosing(false);
    }
  };

  /* ── Render ─────────────────────────────────────────────────────────────── */

  if (loading) return <p className="status-message">Loading merchant profiles...</p>;

  return (
    <div>
      <h2>Merchant Profiles</h2>

      {profiles.length === 0 ? (
        <p className="status-message">
          No merchant accounts found. An administrator can create them from the Accounts page.
        </p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Username</th>
              <th>Standing</th>
              <th>Credit Limit</th>
              <th>Plan</th>
              <th>Discount Credit</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {profiles.map((p) => (
              <tr key={p.userId}>
                {editingId === p.userId ? (
                  /* ── Edit row ─────────────────────────────────────────── */
                  <>
                    <td>{p.name}</td>
                    <td>{p.username}</td>
                    <td>
                      <select
                        value={editForm.standing}
                        onChange={(e) =>
                          setEditForm({ ...editForm, standing: e.target.value })
                        }
                      >
                        <option value="NORMAL">NORMAL</option>
                        <option value="IN_DEFAULT">IN_DEFAULT</option>
                        <option value="SUSPENDED">SUSPENDED</option>
                      </select>
                    </td>
                    <td>
                      <input
                        type="number" min="0.01" step="0.01"
                        value={editForm.creditLimit}
                        onChange={(e) =>
                          setEditForm({ ...editForm, creditLimit: e.target.value })
                        }
                        style={{ width: "100px" }}
                      />
                    </td>
                    <td>
                      <select
                        value={editForm.discountPlanType}
                        onChange={(e) =>
                          setEditForm({ ...editForm, discountPlanType: e.target.value })
                        }
                      >
                        <option value="FIXED">FIXED</option>
                        <option value="FLEXIBLE">FLEXIBLE</option>
                      </select>
                      {editForm.discountPlanType === "FIXED" && (
                        <input
                          type="number" min="0" max="100" step="0.01"
                          placeholder="% discount"
                          value={editForm.fixedDiscountPercent}
                          onChange={(e) =>
                            setEditForm({ ...editForm, fixedDiscountPercent: e.target.value })
                          }
                          style={{ width: "80px", marginLeft: "0.5rem" }}
                        />
                      )}
                      {editForm.discountPlanType === "FLEXIBLE" && (
                        <input
                          type="text"
                          placeholder="Tiers JSON"
                          value={editForm.flexibleTiersJson}
                          onChange={(e) =>
                            setEditForm({ ...editForm, flexibleTiersJson: e.target.value })
                          }
                          style={{ width: "200px", marginLeft: "0.5rem", fontSize: "0.8rem" }}
                        />
                      )}
                    </td>
                    <td>£{Number(p.flexibleDiscountCredit).toFixed(2)}</td>
                    <td>
                      <button className="action-btn save" onClick={() => saveEdit(p.userId)}>
                        Save
                      </button>
                      <button className="action-btn cancel" onClick={cancelEdit}>
                        Cancel
                      </button>
                    </td>
                  </>
                ) : (
                  /* ── Display row ──────────────────────────────────────── */
                  <>
                    <td>{p.name}</td>
                    <td>{p.username}</td>
                    <td>
                      <span className={`standing-badge standing-${p.standing.toLowerCase().replace("_", "-")}`}>
                        {p.standing}
                      </span>
                    </td>
                    <td>£{Number(p.creditLimit).toFixed(2)}</td>
                    <td>
                      {p.discountPlanType}
                      {p.discountPlanType === "FIXED" && p.fixedDiscountPercent != null && (
                        <span style={{ color: "#6b7280" }}> ({p.fixedDiscountPercent}%)</span>
                      )}
                    </td>
                    <td>£{Number(p.flexibleDiscountCredit).toFixed(2)}</td>
                    <td>
                      <button className="action-btn edit" onClick={() => startEdit(p)}>
                        Edit
                      </button>
                    </td>
                  </>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* ── Month Close Section ───────────────────────────────────────────── */}
      <div className="month-close-section">
        <h3>Flexible Discount — Month Close</h3>
        <p style={{ color: "#6b7280", fontSize: "0.85rem", marginBottom: "1rem" }}>
          Compute and disburse the flexible discount rebate for all FLEXIBLE-plan merchants
          for a given calendar month. Each merchant/month combination can only be settled once.
        </p>
        <form onSubmit={handleCloseMonth} className="month-close-form">
          <div className="form-group">
            <label htmlFor="close-ym">Year-Month</label>
            <input
              id="close-ym" type="month"
              value={closeYearMonth}
              onChange={(e) => setCloseYearMonth(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <label htmlFor="close-mode">Settlement Mode</label>
            <select id="close-mode" value={closeMode} onChange={(e) => setCloseMode(e.target.value)}>
              <option value="APPLY_CREDIT">Apply as credit (deduct from next orders)</option>
              <option value="CHEQUE">Record as cheque payout</option>
            </select>
          </div>
          <button type="submit" disabled={closing} className="submit-btn">
            {closing ? "Processing..." : "Close Month"}
          </button>
        </form>
      </div>

      {message && (
        <p className={`status-message ${isError ? "error" : "success"}`}>
          {message}
        </p>
      )}
    </div>
  );
}

export default MerchantManagement;
