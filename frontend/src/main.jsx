/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: The React application entry point.                                   ║
 * ║                                                                              ║
 * ║  WHY:  This file bridges the HTML page and the React component tree.        ║
 * ║        It finds the <div id="root"> in index.html and tells React to        ║
 * ║        render our <App /> component inside it.                              ║
 * ║                                                                              ║
 * ║  PROVIDER HIERARCHY:                                                         ║
 * ║        <StrictMode>        — React development warnings (dev only).        ║
 * ║          <AuthProvider>    — Authentication context (session, login/logout).║
 * ║            <App />         — The main application component.               ║
 * ║          </AuthProvider>                                                    ║
 * ║        </StrictMode>                                                        ║
 * ║                                                                              ║
 * ║        AuthProvider MUST wrap App because App.jsx (and its children) use    ║
 * ║        the useAuth() hook to read authentication state and call login().   ║
 * ║        If AuthProvider were inside App, the Login component couldn't        ║
 * ║        access the auth context.                                            ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add more providers here (e.g., ThemeProvider, NotificationProvider)║
 * ║        - Add a Router here if using React Router.                           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { AuthProvider } from "./auth/AuthContext.jsx";
import App from "./App.jsx";
import "./App.css";

/*
 * createRoot() is the React 18+ way to mount a React app into the DOM.
 * (The older way was ReactDOM.render(), which is now deprecated.)
 *
 * StrictMode is a development-only wrapper that:
 *   - Warns about unsafe lifecycle methods.
 *   - Detects unexpected side effects by intentionally double-invoking
 *     certain functions (like component bodies and useEffect).
 *   - It does NOT affect production builds.
 *
 * AuthProvider initialises the authentication state:
 *   - On mount, it calls GET /api/auth/me to check for an existing session.
 *   - If a valid session exists, the user is automatically restored.
 *   - If no session exists, user remains null and Login is shown.
 */
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </StrictMode>
);
