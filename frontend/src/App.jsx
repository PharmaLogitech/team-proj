/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: The root React component — the top of the component tree.            ║
 * ║                                                                              ║
 * ║  WHY:  App.jsx is the single entry component rendered by main.jsx.          ║
 * ║        It controls:                                                         ║
 * ║          1. AUTHENTICATION GATE — If not logged in, show <Login />.         ║
 * ║          2. RBAC NAVIGATION — Only show nav items the user's role allows.  ║
 * ║          3. PAGE ROUTING — Render the correct page component based on       ║
 * ║             the current navigation state.                                   ║
 * ║                                                                              ║
 * ║  AUTHENTICATION:                                                             ║
 * ║        Uses AuthContext (see auth/AuthContext.jsx).  The <AuthProvider>     ║
 * ║        wrapper in main.jsx handles session restoration on page refresh.    ║
 * ║        App.jsx reads the auth state via useAuth() and decides:              ║
 * ║          - loading → show loading screen (session check in progress).      ║
 * ║          - user is null → show <Login />.                                  ║
 * ║          - user exists → show the main app with role-based navigation.     ║
 * ║                                                                              ║
 * ║  RBAC (ACC-US4 — Role-Based Access Control):                                ║
 * ║        Navigation items are generated dynamically from rbac.js.            ║
 * ║        Only routes that the current user's role can access are shown.       ║
 * ║        See auth/rbac.js for the full role × package access matrix.         ║
 * ║                                                                              ║
 * ║  HOW TO ADD A NEW PAGE:                                                      ║
 * ║        1. Create the page component (e.g., NewFeature.jsx).                ║
 * ║        2. Add its route to ROUTE_PACKAGES in auth/rbac.js.                 ║
 * ║        3. Add the route to NAV_LABELS and PAGE_COMPONENTS below.           ║
 * ║        4. Import the component at the top of this file.                    ║
 * ║        The nav and route guards will pick it up automatically.             ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Replace currentPage state with React Router for URL-based routing.║
 * ║        - Add breadcrumbs or a sidebar layout for more complex navigation.  ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { useAuth } from "./auth/AuthContext.jsx";
import { roleCanAccessRoute, getAccessibleRoutes } from "./auth/rbac.js";
import Login from "./Login.jsx";
import Catalogue from "./Catalogue.jsx";
import OrderForm from "./OrderForm.jsx";
import ReportingPlaceholder from "./ReportingPlaceholder.jsx";
import AccountsPlaceholder from "./AccountsPlaceholder.jsx";

/*
 * ── NAV_LABELS ───────────────────────────────────────────────────────────────
 *
 * Maps route identifiers to the text displayed in the navigation bar.
 * These must match the keys in ROUTE_PACKAGES (auth/rbac.js).
 *
 * To add a new page:
 *   1. Add the route key here with its display label.
 *   2. Add the route key to PAGE_COMPONENTS below.
 *   3. Add the route key to ROUTE_PACKAGES in rbac.js.
 */
const NAV_LABELS = {
  catalogue: "Catalogue",
  order: "Place Order",
  reporting: "Reporting",
  accounts: "Accounts",
};

/*
 * ── PAGE_COMPONENTS ──────────────────────────────────────────────────────────
 *
 * Maps route identifiers to the React component that renders that page.
 * App.jsx looks up the current page in this map to decide what to render.
 *
 * The value is a function that receives props and returns JSX.
 * This lazy pattern means components are only rendered when needed.
 *
 * NOTE: refreshKey and onOrderPlaced are only relevant to specific pages.
 *       Other pages simply ignore unused props.
 */
const PAGE_COMPONENTS = {
  catalogue: (props) => <Catalogue key={props.refreshKey} />,
  order: (props) => (
    <OrderForm
      onOrderPlaced={props.onOrderPlaced}
      currentUser={props.currentUser}
    />
  ),
  reporting: () => <ReportingPlaceholder />,
  accounts: () => <AccountsPlaceholder />,
};

function App() {
  /*
   * ── Auth State ─────────────────────────────────────────────────────────
   *
   * user    — The authenticated user { id, name, username, role } or null.
   * loading — True while the session check is in progress (page refresh).
   * logout  — Function to invalidate the session and return to login.
   */
  const { user, loading, logout } = useAuth();

  /*
   * ── Page Navigation State ──────────────────────────────────────────────
   *
   * currentPage tracks which page the user is viewing.
   * It must be a key that exists in both NAV_LABELS and PAGE_COMPONENTS.
   *
   * We default to null and set it once we know the user's role (see
   * useEffect below), because different roles have different default pages.
   */
  const [currentPage, setCurrentPage] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  /*
   * ── Set Default Page When User Changes ─────────────────────────────────
   *
   * When the user logs in (or the session is restored), we need to set
   * the initial page to the first accessible route for their role.
   *
   * This ensures:
   *   - A MERCHANT lands on "catalogue" (not "accounts" which they can't see).
   *   - An ADMIN lands on "catalogue" (the first route in the list).
   *   - If the user logs out and back in as a different role, the page resets.
   */
  useEffect(() => {
    if (user) {
      const accessibleRoutes = getAccessibleRoutes(user.role);
      if (accessibleRoutes.length > 0) {
        setCurrentPage(accessibleRoutes[0]);
      }
    } else {
      setCurrentPage(null);
    }
  }, [user]);

  /*
   * Called after a successful order placement.
   * Increments refreshKey to force the Catalogue to re-fetch product data
   * (so updated stock levels are visible), then switches to the catalogue page.
   */
  const handleOrderPlaced = () => {
    setRefreshKey((prev) => prev + 1);
    setCurrentPage("catalogue");
  };

  /*
   * ── LOADING STATE ──────────────────────────────────────────────────────
   *
   * While AuthContext is checking the session (GET /api/auth/me), we show
   * a loading indicator.  This prevents a flash of the login screen
   * when the user actually has a valid session.
   */
  if (loading) {
    return (
      <div className="app">
        <header className="app-header">
          <h1>IPOS-SA</h1>
          <p className="subtitle">Pharmaceutical Stock & Order Management</p>
        </header>
        <main className="app-main">
          <p className="status-message">Checking session...</p>
        </main>
      </div>
    );
  }

  /*
   * ── LOGIN GATE ─────────────────────────────────────────────────────────
   *
   * If no user is authenticated, show the login screen.
   * The Login component uses useAuth() to call login() on form submission.
   * When login succeeds, user state is set, App re-renders, and we
   * proceed to the main app below.
   */
  if (!user) {
    return (
      <div className="app">
        <header className="app-header">
          <h1>IPOS-SA</h1>
          <p className="subtitle">Pharmaceutical Stock & Order Management</p>
        </header>
        <main className="app-main">
          <Login />
        </main>
      </div>
    );
  }

  /*
   * ── MAIN APP (authenticated) ───────────────────────────────────────────
   *
   * Build the navigation bar dynamically based on the user's role.
   * Only routes the user can access (per rbac.js) get nav buttons.
   *
   * getAccessibleRoutes() returns route keys like ["catalogue", "order"]
   * for a MERCHANT, or ["catalogue", "order", "reporting", "accounts"]
   * for an ADMIN.
   */
  const accessibleRoutes = getAccessibleRoutes(user.role);

  /*
   * Render the page component for the current route.
   * If currentPage is not set yet (shouldn't happen after useEffect),
   * fall back to a message.
   */
  const renderPage = () => {
    if (!currentPage || !PAGE_COMPONENTS[currentPage]) {
      return <p className="status-message">Select a page from the navigation.</p>;
    }

    /*
     * RBAC ROUTE GUARD: Double-check that the user's role can access
     * the current page.  This prevents a stale currentPage from showing
     * a page the user shouldn't see (e.g., if role changed mid-session).
     */
    if (!roleCanAccessRoute(user.role, currentPage)) {
      return (
        <p className="status-message error">
          Access denied. Your role ({user.role}) does not have permission
          to view this page.
        </p>
      );
    }

    return PAGE_COMPONENTS[currentPage]({
      refreshKey,
      onOrderPlaced: handleOrderPlaced,
      currentUser: user,
    });
  };

  return (
    <div className="app">
      <header className="app-header">
        <h1>IPOS-SA</h1>
        <p className="subtitle">Pharmaceutical Stock & Order Management</p>

        {/* ── User Info & Logout ──────────────────────────────────────── */}
        <div className="app-header-actions">
          <span className="current-user">
            Logged in as {user.name} ({user.role})
          </span>
          <button
            type="button"
            className="logout-btn"
            onClick={logout}
          >
            Log out
          </button>
        </div>

        {/* ── Role-Based Navigation ───────────────────────────────────── */}
        <nav className="app-nav">
          {/*
           * Only render nav buttons for routes the user's role can access.
           * This implements the visual part of ACC-US4: merchants don't
           * see Reporting or Accounts buttons; managers don't see Accounts.
           */}
          {accessibleRoutes.map((route) => (
            <button
              key={route}
              className={currentPage === route ? "active" : ""}
              onClick={() => setCurrentPage(route)}
            >
              {NAV_LABELS[route] || route}
            </button>
          ))}
        </nav>
      </header>

      <main className="app-main">
        {renderPage()}
      </main>
    </div>
  );
}

export default App;
