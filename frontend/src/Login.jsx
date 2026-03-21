/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Login page component with username + password authentication.        ║
 * ║                                                                              ║
 * ║  WHY:  This is the ENTRY POINT for all users.  Before accessing any         ║
 * ║        part of the IPOS-SA system, users must authenticate with valid       ║
 * ║        credentials.  The login flow is:                                     ║
 * ║          1. User enters username and password.                              ║
 * ║          2. Form calls AuthContext.login() which POSTs to /api/auth/login. ║
 * ║          3. Backend verifies credentials against BCrypt hash.              ║
 * ║          4. On success: session cookie is set, user state is updated,      ║
 * ║             App.jsx re-renders and shows the main application.             ║
 * ║          5. On failure: error message is shown inline.                     ║
 * ║                                                                              ║
 * ║  REPLACES:                                                                   ║
 * ║        The old Phase 1 "pick a user" dropdown.  That was for learning       ║
 * ║        only — it had no real authentication.  This form uses proper         ║
 * ║        server-verified credentials with BCrypt password hashing.            ║
 * ║                                                                              ║
 * ║  ACCESSIBILITY:                                                              ║
 * ║        - Labels are associated with inputs via htmlFor/id.                 ║
 * ║        - type="password" hides the password as the user types.             ║
 * ║        - autoComplete attributes help password managers fill credentials.  ║
 * ║        - The submit button is disabled during the request to prevent        ║
 * ║          double-submission.                                                 ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a "Remember me" checkbox that uses localStorage.              ║
 * ║        - Add a "Forgot password" link when password reset is implemented.  ║
 * ║        - Add rate-limiting feedback (e.g., "Too many attempts, wait 30s"). ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState } from "react";
import { useAuth } from "./auth/AuthContext.jsx";

function Login() {
  /*
   * ── Form State ─────────────────────────────────────────────────────────
   *
   * username, password:  Controlled input values.  React state is the
   *   single source of truth — the input's displayed value always matches
   *   the state variable.
   *
   * submitting:  True while the login request is in flight.  Used to
   *   disable the button and show "Logging in..." text.
   */
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [submitting, setSubmitting] = useState(false);

  /*
   * login() and error come from AuthContext.
   *   login(username, password) → calls POST /api/auth/login.
   *   error → set by AuthContext if login fails (e.g., "Invalid credentials").
   */
  const { login, error } = useAuth();

  /*
   * ── Form Submission ────────────────────────────────────────────────────
   *
   * e.preventDefault() stops the browser from doing a full page reload
   * (which is the default behavior for HTML form submission).
   *
   * We call login() from AuthContext, which handles:
   *   - Sending credentials to the backend.
   *   - Setting the user state on success.
   *   - Setting the error message on failure.
   */
  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    await login(username, password);
    setSubmitting(false);
  };

  return (
    <div className="login-page">
      <h2>Log in</h2>

      <p className="login-hint">
        Enter your credentials to access the system.
      </p>

      {/* Dev-mode help text pointing to default bootstrap credentials. */}
      <p className="login-hint" style={{ fontSize: "0.8rem", marginTop: "0.5rem" }}>
        Default accounts: admin/admin123, manager/manager123, merchant/merchant123
      </p>

      <form onSubmit={handleSubmit}>
        {/* ── Username Field ───────────────────────────────────────────── */}
        <div className="form-group">
          <label htmlFor="username">Username:</label>
          <input
            id="username"
            type="text"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            /*
             * autoComplete="username" tells the browser's password manager
             * that this field contains a username.  The password manager can
             * then offer to auto-fill saved credentials.
             */
            autoComplete="username"
            required
            placeholder="Enter your username"
          />
        </div>

        {/* ── Password Field ───────────────────────────────────────────── */}
        <div className="form-group">
          <label htmlFor="password">Password:</label>
          <input
            id="password"
            /*
             * type="password" masks the input with dots/asterisks.
             * This prevents shoulder-surfing (someone looking at the screen).
             */
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            /*
             * autoComplete="current-password" helps the browser's password
             * manager distinguish between "enter existing password" vs
             * "create new password" (which uses "new-password").
             */
            autoComplete="current-password"
            required
            placeholder="Enter your password"
          />
        </div>

        {/* ── Submit Button ────────────────────────────────────────────── */}
        <button
          type="submit"
          className="submit-btn"
          disabled={submitting}
        >
          {submitting ? "Logging in..." : "Log in"}
        </button>
      </form>

      {/*
       * ── Error Message ──────────────────────────────────────────────────
       *
       * Shown when AuthContext.login() sets the error state.
       * Examples: "Invalid username or password." or "Cannot reach the server."
       *
       * We use the existing .status-message.error CSS class for consistent
       * styling with error messages elsewhere in the app.
       */}
      {error && (
        <p className="status-message error">{error}</p>
      )}
    </div>
  );
}

export default Login;
