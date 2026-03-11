/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: The React application entry point.                                   ║
 * ║                                                                              ║
 * ║  WHY:  This file bridges the HTML page and the React component tree.        ║
 * ║        It finds the <div id="root"> in index.html and tells React to        ║
 * ║        render our <App /> component inside it.                              ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Wrap <App /> with context providers (e.g., ThemeProvider).         ║
 * ║        - Add a Router here if using React Router.                           ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
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
 */
createRoot(document.getElementById("root")).render(
  <StrictMode>
    <App />
  </StrictMode>
);
