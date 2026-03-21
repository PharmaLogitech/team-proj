/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Admin-only form for creating new Merchant Accounts (ACC-US1).        ║
 * ║                                                                              ║
 * ║  WHY:  The brief says: "Once a new account is set up as a merchant account  ║
 * ║        the system will ask for contact details to be provided, credit limit ║
 * ║        and discount plan to be set up for the new merchant before the       ║
 * ║        account is activated."                                               ║
 * ║                                                                              ║
 * ║        This component collects ALL mandatory fields in a single form.       ║
 * ║        On submit, it calls POST /api/merchant-accounts.  If any field is    ║
 * ║        missing, the backend rejects the request (400) and no account is     ║
 * ║        created.                                                             ║
 * ║                                                                              ║
 * ║  DISCOUNT PLAN UI (brief §i):                                                ║
 * ║        - FIXED: Shows a single "Discount %" input.                         ║
 * ║        - FLEXIBLE: Shows a tier editor where the admin can add/remove      ║
 * ║          tiers.  Each tier has an optional "Max exclusive (£)" threshold    ║
 * ║          and a required "Percent (%)".  The last tier has no threshold      ║
 * ║          (catch-all).                                                       ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL:                                                             ║
 * ║        Visible to: ADMIN only (IPOS-SA-ACC package).                       ║
 * ║        Enforced by: rbac.js + App.jsx nav guards.                          ║
 * ║        Backend: POST /api/merchant-accounts → hasRole("ADMIN").            ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState } from "react";
import { createMerchantAccount } from "./api.js";

function MerchantCreate() {
  /* ── Form state ─────────────────────────────────────────────────────────── */
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [contactEmail, setContactEmail] = useState("");
  const [contactPhone, setContactPhone] = useState("");
  const [addressLine, setAddressLine] = useState("");
  const [creditLimit, setCreditLimit] = useState("");
  const [planType, setPlanType] = useState("FIXED");
  const [fixedPercent, setFixedPercent] = useState("");

  /*
   * Flexible tier editor state.
   * Each tier is { maxExclusive: string, percent: string }.
   * The last tier's maxExclusive is ignored (catch-all).
   */
  const [tiers, setTiers] = useState([
    { maxExclusive: "1000", percent: "1" },
    { maxExclusive: "2000", percent: "2" },
    { maxExclusive: "", percent: "3" },
  ]);

  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);

  /* ── Tier editor helpers ────────────────────────────────────────────────── */

  const updateTier = (index, field, value) => {
    const updated = [...tiers];
    updated[index] = { ...updated[index], [field]: value };
    setTiers(updated);
  };

  const addTier = () => {
    setTiers([...tiers, { maxExclusive: "", percent: "" }]);
  };

  const removeTier = (index) => {
    if (tiers.length <= 1) return;
    setTiers(tiers.filter((_, i) => i !== index));
  };

  /*
   * Build the JSON tiers array for the API.
   * The last tier omits maxExclusive (catch-all per brief §i).
   */
  const buildTiersJson = () => {
    return tiers.map((tier, i) => {
      const obj = { percent: Number(tier.percent) };
      if (i < tiers.length - 1 && tier.maxExclusive) {
        obj.maxExclusive = Number(tier.maxExclusive);
      }
      return obj;
    });
  };

  /* ── Form submission ────────────────────────────────────────────────────── */

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setMessage(null);

    try {
      const payload = {
        name,
        username,
        password,
        contactEmail,
        contactPhone,
        addressLine,
        creditLimit: Number(creditLimit),
        planType,
      };

      if (planType === "FIXED") {
        payload.fixedDiscountPercent = Number(fixedPercent);
      } else {
        payload.flexibleTiersJson = JSON.stringify(buildTiersJson());
      }

      await createMerchantAccount(payload);
      setMessage("Merchant account created successfully!");
      setIsError(false);

      /* Reset form. */
      setName("");
      setUsername("");
      setPassword("");
      setContactEmail("");
      setContactPhone("");
      setAddressLine("");
      setCreditLimit("");
      setFixedPercent("");
      setTiers([
        { maxExclusive: "1000", percent: "1" },
        { maxExclusive: "2000", percent: "2" },
        { maxExclusive: "", percent: "3" },
      ]);
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div>
      <h2>Create Merchant Account</h2>
      <p style={{ color: "#6b7280", fontSize: "0.9rem", marginBottom: "1.5rem" }}>
        All fields are required. The account will not be created if any detail is missing.
      </p>

      <form onSubmit={handleSubmit} className="merchant-create-form">
        {/* ── Credentials ─────────────────────────────────────────────────── */}
        <fieldset className="form-section">
          <legend>Login Credentials</legend>
          <div className="form-group">
            <label htmlFor="mc-name">Display Name</label>
            <input id="mc-name" type="text" value={name} onChange={(e) => setName(e.target.value)} required />
          </div>
          <div className="form-group">
            <label htmlFor="mc-username">Username</label>
            <input id="mc-username" type="text" value={username} onChange={(e) => setUsername(e.target.value)} required />
          </div>
          <div className="form-group">
            <label htmlFor="mc-password">Password</label>
            <input id="mc-password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </div>
        </fieldset>

        {/* ── Contact Details ──────────────────────────────────────────────── */}
        <fieldset className="form-section">
          <legend>Contact Details</legend>
          <div className="form-group">
            <label htmlFor="mc-email">Email</label>
            <input id="mc-email" type="email" value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} required />
          </div>
          <div className="form-group">
            <label htmlFor="mc-phone">Phone</label>
            <input id="mc-phone" type="tel" value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} required />
          </div>
          <div className="form-group">
            <label htmlFor="mc-address">Address</label>
            <input id="mc-address" type="text" value={addressLine} onChange={(e) => setAddressLine(e.target.value)} required />
          </div>
        </fieldset>

        {/* ── Financial Details ─────────────────────────────────────────────── */}
        <fieldset className="form-section">
          <legend>Financial Details</legend>
          <div className="form-group">
            <label htmlFor="mc-credit">Credit Limit (£)</label>
            <input id="mc-credit" type="number" min="0.01" step="0.01" value={creditLimit} onChange={(e) => setCreditLimit(e.target.value)} required />
          </div>
        </fieldset>

        {/* ── Discount Plan ────────────────────────────────────────────────── */}
        <fieldset className="form-section">
          <legend>Discount Plan</legend>
          <div className="form-group">
            <label htmlFor="mc-plantype">Plan Type</label>
            <select id="mc-plantype" value={planType} onChange={(e) => setPlanType(e.target.value)}>
              <option value="FIXED">Fixed — Same % on every order</option>
              <option value="FLEXIBLE">Flexible — Tiered by monthly spend</option>
            </select>
          </div>

          {planType === "FIXED" && (
            <div className="form-group">
              <label htmlFor="mc-fixedpct">Discount Percent (%)</label>
              <input id="mc-fixedpct" type="number" min="0" max="100" step="0.01" value={fixedPercent} onChange={(e) => setFixedPercent(e.target.value)} required />
            </div>
          )}

          {planType === "FLEXIBLE" && (
            <div className="tier-editor">
              <p style={{ fontSize: "0.85rem", color: "#6b7280", marginBottom: "0.5rem" }}>
                Define spending tiers. The last tier is the catch-all (no upper limit).
              </p>
              {tiers.map((tier, i) => (
                <div key={i} className="tier-row">
                  {i < tiers.length - 1 ? (
                    <input
                      type="number" min="0" step="0.01"
                      placeholder="Max £ (exclusive)"
                      value={tier.maxExclusive}
                      onChange={(e) => updateTier(i, "maxExclusive", e.target.value)}
                    />
                  ) : (
                    <span className="tier-catchall">Above previous</span>
                  )}
                  <input
                    type="number" min="0" max="100" step="0.01"
                    placeholder="Discount %"
                    value={tier.percent}
                    onChange={(e) => updateTier(i, "percent", e.target.value)}
                    required
                  />
                  {tiers.length > 1 && (
                    <button type="button" className="tier-remove-btn" onClick={() => removeTier(i)}>
                      Remove
                    </button>
                  )}
                </div>
              ))}
              <button type="button" className="tier-add-btn" onClick={addTier}>
                + Add Tier
              </button>
            </div>
          )}
        </fieldset>

        <button type="submit" disabled={submitting} className="submit-btn">
          {submitting ? "Creating..." : "Create Merchant Account"}
        </button>
      </form>

      {message && (
        <p className={`status-message ${isError ? "error" : "success"}`}>
          {message}
        </p>
      )}
    </div>
  );
}

export default MerchantCreate;
