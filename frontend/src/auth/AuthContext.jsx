/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: React Context for authentication state management.                   ║
 * ║                                                                              ║
 * ║  WHY:  Multiple components need to know "who is logged in" and "what role   ║
 * ║        do they have."  Instead of passing user data through props at every  ║
 * ║        level (prop drilling), we use React Context to make auth state       ║
 * ║        available to ANY component in the tree.                              ║
 * ║                                                                              ║
 * ║  HOW IT WORKS:                                                               ║
 * ║        1. <AuthProvider> wraps the entire app in main.jsx.                  ║
 * ║        2. On mount, it calls GET /api/auth/me to check if the user has     ║
 * ║           a valid session (JSESSIONID cookie).                              ║
 * ║        3. If yes → user state is populated, app shows main content.         ║
 * ║        4. If no (401) → user is null, app shows login screen.              ║
 * ║        5. Components call useAuth() to access: user, login(), logout().    ║
 * ║                                                                              ║
 * ║  SESSION PERSISTENCE:                                                        ║
 * ║        The JSESSIONID cookie persists across page refreshes (it's a        ║
 * ║        browser session cookie).  When the user refreshes the page:          ║
 * ║          1. React app re-mounts → AuthProvider re-runs.                    ║
 * ║          2. GET /api/auth/me is called with the existing cookie.           ║
 * ║          3. Backend validates the session and returns user data.            ║
 * ║          4. User is restored — no re-login needed.                         ║
 * ║                                                                              ║
 * ║  CREDENTIALS AND CSRF:                                                       ║
 * ║        All fetch calls include { credentials: "include" } which tells the  ║
 * ║        browser to send cookies (JSESSIONID, XSRF-TOKEN) with every         ║
 * ║        request.  Without this, the session cookie would be silently         ║
 * ║        dropped by the browser on cross-origin requests.                     ║
 * ║                                                                              ║
 * ║        For POST/PUT/DELETE requests, we also send the X-XSRF-TOKEN header  ║
 * ║        (read from the XSRF-TOKEN cookie) for CSRF protection.              ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a changePassword() function for password updates.             ║
 * ║        - Add a register() function if self-registration is needed.         ║
 * ║        - Add session expiry detection (e.g., periodic /me checks).         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { createContext, useContext, useState, useEffect, useCallback } from "react";

/*
 * ── React Context ────────────────────────────────────────────────────────────
 *
 * createContext() creates a "channel" for passing data through the component
 * tree without explicit props.  It has two parts:
 *   1. A Provider component (<AuthContext.Provider value={...}>) that wraps
 *      the tree and supplies the data.
 *   2. A Consumer hook (useContext(AuthContext)) that any child component
 *      uses to READ the data.
 *
 * We initialise with null — the actual value comes from <AuthProvider>.
 */
const AuthContext = createContext(null);

/*
 * ── CSRF Helper ──────────────────────────────────────────────────────────────
 *
 * Reads the XSRF-TOKEN cookie value.  Spring Security sets this cookie
 * after the first authenticated request.  We need to send it back as an
 * X-XSRF-TOKEN header on every state-changing request (POST, PUT, DELETE).
 *
 * HOW IT WORKS:
 *   1. Spring Security sets:  Set-Cookie: XSRF-TOKEN=<random-value>
 *   2. JavaScript reads this cookie using document.cookie parsing.
 *   3. JavaScript sends:  X-XSRF-TOKEN: <random-value>  as a header.
 *   4. Spring Security compares the cookie and header — if they match,
 *      the request is legitimate (not a CSRF attack).
 *
 * WHY THIS IS SAFE:
 *   A malicious site cannot read our cookies (Same-Origin Policy).
 *   So only JavaScript running on OUR origin can read XSRF-TOKEN
 *   and send it as a header.  An attacker's site can send the cookie
 *   (browsers do that automatically) but cannot send the header.
 *
 * @returns {string|null}  The CSRF token value, or null if not set yet.
 */
function getCsrfToken() {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
  return match ? decodeURIComponent(match[1]) : null;
}

/*
 * ── fetchWithAuth ────────────────────────────────────────────────────────────
 *
 * A wrapper around fetch() that automatically:
 *   1. Includes credentials (cookies) on every request.
 *   2. Adds the CSRF token header for mutating methods (POST, PUT, DELETE, PATCH).
 *   3. Sets Content-Type to JSON for requests with a body.
 *
 * Every API call in the app should use this function instead of raw fetch().
 * This ensures authentication and CSRF protection are always applied.
 *
 * @param {string} url      The API endpoint (e.g., "/api/products").
 * @param {object} options  Standard fetch options (method, body, headers, etc.).
 * @returns {Promise<Response>}  The fetch Response object.
 */
export async function fetchWithAuth(url, options = {}) {
  const method = (options.method || "GET").toUpperCase();

  /*
   * Start with any headers the caller provided, then add our own.
   * We use a new Headers object to avoid mutating the caller's options.
   */
  const headers = new Headers(options.headers || {});

  /*
   * For mutating methods, add the CSRF token.
   * GET and HEAD requests don't need CSRF protection because they
   * should never change server state (they're "safe" methods).
   */
  if (method !== "GET" && method !== "HEAD") {
    const csrfToken = getCsrfToken();
    if (csrfToken) {
      headers.set("X-XSRF-TOKEN", csrfToken);
    }
  }

  /*
   * If there's a body and no Content-Type is set, default to JSON.
   * This is the most common case for our API calls.
   */
  if (options.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  return fetch(url, {
    ...options,
    headers,
    /*
     * credentials: "include" tells the browser to send cookies
     * (JSESSIONID, XSRF-TOKEN) with this request, even if it's
     * cross-origin.  This is REQUIRED for session-based auth to work
     * with the Vite proxy during development.
     *
     * Without this, the browser silently drops the session cookie
     * and every request appears unauthenticated to Spring Security.
     */
    credentials: "include",
  });
}

/*
 * ── AuthProvider ─────────────────────────────────────────────────────────────
 *
 * This component wraps the entire app and provides authentication state
 * to all child components via React Context.
 *
 * STATE:
 *   user    — The authenticated user object { id, name, username, role }
 *             or null if not authenticated.
 *   loading — true while checking the session on initial load.
 *             While loading, we show a loading screen (not login, not app).
 *   error   — Error message if session check or login fails.
 *
 * METHODS:
 *   login(username, password)  — Authenticate with the backend.
 *   logout()                   — Invalidate the session.
 *
 * @param {object} props
 * @param {React.ReactNode} props.children  The app tree to wrap.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  /*
   * ── Session Restoration (runs on mount) ────────────────────────────────
   *
   * When the app loads (or the user refreshes the page), we call
   * GET /api/auth/me to check if there's an active session.
   *
   * - 200 → Session is valid.  Parse the user data and set state.
   * - 401 → No valid session.  User needs to log in.  This is NOT an error;
   *          it's the normal state for a new visitor.
   * - Other → Unexpected error (network issue, server down).
   *
   * The empty dependency array [] means this runs ONCE on mount.
   */
  useEffect(() => {
    async function checkSession() {
      try {
        const response = await fetchWithAuth("/api/auth/me");

        if (response.ok) {
          const data = await response.json();
          setUser(data);
        }
        /* 401 is expected for unauthenticated users — do nothing. */
      } catch (err) {
        console.error("Session check failed:", err);
      } finally {
        setLoading(false);
      }
    }

    checkSession();
  }, []);

  /*
   * ── login ──────────────────────────────────────────────────────────────
   *
   * Sends credentials to POST /api/auth/login.
   * On success: sets user state → triggers re-render → app shows main content.
   * On failure: sets error message → login form shows the error.
   *
   * useCallback() memoises the function so it doesn't change identity on
   * every render.  This prevents unnecessary re-renders of child components
   * that receive `login` as a prop or dependency.
   *
   * @param {string} username  The username entered in the login form.
   * @param {string} password  The password entered in the login form.
   */
  const login = useCallback(async (username, password) => {
    setError(null);
    try {
      const response = await fetchWithAuth("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });

      if (response.ok) {
        const data = await response.json();
        setUser(data);
        return true;
      }

      /* Parse the error message from the backend (JSON body). */
      const errorData = await response.json().catch(() => null);
      setError(errorData?.error || "Login failed. Please check your credentials.");
      return false;

    } catch (err) {
      setError("Cannot reach the server. Is the backend running?");
      return false;
    }
  }, []);

  /*
   * ── logout ─────────────────────────────────────────────────────────────
   *
   * Calls POST /api/auth/logout to invalidate the server-side session.
   * Then clears the client-side user state → triggers re-render → login screen.
   *
   * Even if the server call fails (e.g., network error), we still clear
   * the local state so the user sees the login screen.  The server-side
   * session will eventually expire on its own.
   */
  const logout = useCallback(async () => {
    try {
      await fetchWithAuth("/api/auth/logout", { method: "POST" });
    } catch (err) {
      console.error("Logout request failed:", err);
    } finally {
      setUser(null);
      setError(null);
    }
  }, []);

  /*
   * The value prop of the Provider makes these available to any component
   * that calls useAuth().  When any of these values change, all consumers
   * of the context are re-rendered.
   */
  return (
    <AuthContext.Provider value={{ user, loading, error, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

/*
 * ── useAuth (custom hook) ────────────────────────────────────────────────────
 *
 * Convenience hook for components to access the auth context.
 * Instead of:  const { user, login } = useContext(AuthContext);
 * Components write:  const { user, login } = useAuth();
 *
 * The error check ensures that useAuth() is only called inside an
 * <AuthProvider> tree.  If someone forgets to wrap the app, they get
 * a clear error message instead of a cryptic "cannot read property of null."
 *
 * @returns {{ user, loading, error, login, logout }}
 */
export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error(
      "useAuth() must be used within an <AuthProvider>. " +
      "Wrap your app with <AuthProvider> in main.jsx."
    );
  }
  return context;
}
