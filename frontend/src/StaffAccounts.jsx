import { useState, useEffect, useCallback } from "react";
import { useAuth } from "./auth/AuthContext.jsx";
import { createUser, getUsers, updateUserRole } from "./api.js";

function StaffAccounts() {
  const { user: currentUser } = useAuth();

  /* ── Create Account form state ──────────────────────────────────────────── */
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState("ADMIN");
  const [submitting, setSubmitting] = useState(false);
  const [createMsg, setCreateMsg] = useState(null);
  const [createIsError, setCreateIsError] = useState(false);

  /* ── Promote / Demote state ─────────────────────────────────────────────── */
  const [users, setUsers] = useState([]);
  const [loadingUsers, setLoadingUsers] = useState(true);
  const [roleEdits, setRoleEdits] = useState({});
  const [roleMsg, setRoleMsg] = useState(null);
  const [roleIsError, setRoleIsError] = useState(false);
  const [updatingId, setUpdatingId] = useState(null);

  /* ── Fetch users ────────────────────────────────────────────────────────── */
  const fetchUsers = useCallback(async () => {
    try {
      const data = await getUsers();
      setUsers(data);
      setRoleEdits({});
    } catch {
      setUsers([]);
    } finally {
      setLoadingUsers(false);
    }
  }, []);

  useEffect(() => {
    fetchUsers();
  }, [fetchUsers]);

  /* ── Create Account submission ──────────────────────────────────────────── */
  const handleCreate = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setCreateMsg(null);

    try {
      await createUser({ name, username, password, role });
      setCreateMsg(`${role} account "${username}" created successfully!`);
      setCreateIsError(false);
      setName("");
      setUsername("");
      setPassword("");
      setRole("ADMIN");
      fetchUsers();
    } catch (err) {
      setCreateMsg(err.message);
      setCreateIsError(true);
    } finally {
      setSubmitting(false);
    }
  };

  /* ── Role change handler ────────────────────────────────────────────────── */
  const handleRoleChange = async (userId, newRole) => {
    if (currentUser && userId === currentUser.id) {
      const confirmed = window.confirm(
        "You are about to change your own role. You may lose access to this page. Continue?"
      );
      if (!confirmed) return;
    }

    setUpdatingId(userId);
    setRoleMsg(null);

    try {
      await updateUserRole(userId, newRole);
      setRoleMsg("Role updated successfully.");
      setRoleIsError(false);
      fetchUsers();
    } catch (err) {
      setRoleMsg(err.message);
      setRoleIsError(true);
    } finally {
      setUpdatingId(null);
    }
  };

  const setEditRole = (userId, value) => {
    setRoleEdits((prev) => ({ ...prev, [userId]: value }));
  };

  return (
    <div>
      <h2>Staff Accounts</h2>

      {/* ── Section 1: Create Privileged Account ──────────────────────────── */}
      <form onSubmit={handleCreate} className="merchant-create-form">
        <fieldset className="form-section">
          <legend>Create Privileged Account</legend>
          <p style={{ color: "#6b7280", fontSize: "0.9rem", marginBottom: "0.5rem" }}>
            Create a new Administrator or Manager account. All fields are required.
          </p>

          <div className="form-group">
            <label htmlFor="sa-name">Display Name</label>
            <input
              id="sa-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="sa-username">Username</label>
            <input
              id="sa-username"
              type="text"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="sa-password">Password</label>
            <input
              id="sa-password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <div className="form-group">
            <label htmlFor="sa-role">Role</label>
            <select
              id="sa-role"
              value={role}
              onChange={(e) => setRole(e.target.value)}
            >
              <option value="ADMIN">Administrator</option>
              <option value="MANAGER">Manager</option>
            </select>
          </div>
        </fieldset>

        <button type="submit" disabled={submitting} className="submit-btn">
          {submitting ? "Creating..." : "Create Account"}
        </button>
      </form>

      {createMsg && (
        <p className={`status-message ${createIsError ? "error" : "success"}`}>
          {createMsg}
        </p>
      )}

      {/* ── Section 2: Promote / Demote Accounts ─────────────────────────── */}
      <div style={{ marginTop: "2.5rem", paddingTop: "2rem", borderTop: "2px solid #f0f2f5" }}>
        <h3 style={{ fontSize: "1.15rem", color: "#16213e", marginBottom: "1rem" }}>
          Promote / Demote Accounts
        </h3>
        <p style={{ color: "#6b7280", fontSize: "0.9rem", marginBottom: "1rem" }}>
          Change staff roles between Administrator and Manager. Merchant accounts
          are managed separately via the Accounts and Merchants pages.
        </p>

        {loadingUsers ? (
          <p className="status-message">Loading accounts...</p>
        ) : users.length === 0 ? (
          <p className="status-message">No accounts found.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Name</th>
                <th>Username</th>
                <th>Current Role</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => {
                const isMerchant = u.role === "MERCHANT";
                const selectedRole = roleEdits[u.id] || u.role;
                const changed = selectedRole !== u.role;

                return (
                  <tr key={u.id}>
                    <td>{u.id}</td>
                    <td>
                      {u.name}
                      {currentUser && u.id === currentUser.id && (
                        <span style={{ fontSize: "0.75rem", color: "#6b7280", marginLeft: "0.4rem" }}>
                          (you)
                        </span>
                      )}
                    </td>
                    <td>{u.username}</td>
                    <td>
                      <span className={`role-badge role-${u.role.toLowerCase()}`}>
                        {u.role}
                      </span>
                    </td>
                    <td>
                      {isMerchant ? (
                        <span style={{ fontSize: "0.8rem", color: "#6b7280", fontStyle: "italic" }}>
                          Manage via Merchant Management
                        </span>
                      ) : (
                        <span style={{ display: "flex", alignItems: "center", gap: "0.4rem" }}>
                          <select
                            value={selectedRole}
                            onChange={(e) => setEditRole(u.id, e.target.value)}
                            style={{
                              padding: "0.3rem 0.5rem",
                              borderRadius: "6px",
                              border: "2px solid #d1d5db",
                              fontSize: "0.85rem",
                            }}
                          >
                            <option value="ADMIN">Administrator</option>
                            <option value="MANAGER">Manager</option>
                          </select>
                          <button
                            type="button"
                            className="action-btn save"
                            disabled={!changed || updatingId === u.id}
                            onClick={() => handleRoleChange(u.id, selectedRole)}
                          >
                            {updatingId === u.id ? "Updating..." : "Update Role"}
                          </button>
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}

        {roleMsg && (
          <p className={`status-message ${roleIsError ? "error" : "success"}`}>
            {roleMsg}
          </p>
        )}
      </div>
    </div>
  );
}

export default StaffAccounts;
