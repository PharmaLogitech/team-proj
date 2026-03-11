/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A simple Login page component.                                       ║
 * ║                                                                              ║
 * ║  WHY:  Phase 1 has no real authentication (no passwords).  This screen       ║
 * ║        lets the user "pick who they are" from the list of users in the       ║
 * ║        database.  That identity is then passed up to App and used for        ║
 * ║        the rest of the session (e.g. placing orders as that merchant).      ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a password field and a real login API call when you add auth.   ║
 * ║        - Add "Remember me" by storing user id in localStorage.              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { getUsers } from "./api.js";

/*
 * ── PROPS ────────────────────────────────────────────────────────────────────
 *
 * onLogin: A callback function.  When the user clicks "Log in", we call
 *          onLogin(selectedUser) so the parent (App) can store the current
 *          user and switch to the main app.  This is "lifting state up" —
 *          the parent owns the "who is logged in" state.
 */
function Login({ onLogin }) {
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  /*
   * selectedId: The id of the user currently chosen in the dropdown.
   *             We store id (number) so we can find the full user object
   *             when the form is submitted.
   */
  const [selectedId, setSelectedId] = useState("");

  /*
   * Fetch the list of users when this component mounts.
   * Same pattern as Catalogue and OrderForm: useEffect + getUsers().
   */
  useEffect(() => {
    async function load() {
      try {
        const data = await getUsers();
        setUsers(data);
        if (data.length > 0) setSelectedId(data[0].id);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, []);

  /*
   * When the user clicks "Log in", find the full user object by id
   * and pass it to the parent.  The parent will then hide Login and
   * show the main app (Catalogue, OrderForm).
   */
  const handleSubmit = (e) => {
    e.preventDefault();
    const user = users.find((u) => u.id === Number(selectedId));
    if (user && onLogin) onLogin(user);
  };

  if (loading) return <p className="status-message">Loading users...</p>;
  if (error) return <p className="status-message error">Error: {error}</p>;
  if (users.length === 0) {
    return (
      <p className="status-message">
        No users in the system. Create a user via the API (POST /api/users) first.
      </p>
    );
  }

  return (
    <div className="login-page">
      <h2>Log in</h2>
      <p className="login-hint">
        Choose your user. (Phase 1 has no password — this is for learning.)
      </p>

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="user">User:</label>
          <select
            id="user"
            value={selectedId}
            onChange={(e) => setSelectedId(e.target.value)}
          >
            {users.map((u) => (
              <option key={u.id} value={u.id}>
                {u.name} — {u.role}
              </option>
            ))}
          </select>
        </div>
        <button type="submit" className="submit-btn">
          Log in
        </button>
      </form>
    </div>
  );
}

export default Login;
