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
 * ║          4. LOW-STOCK BANNER — Persistent warning for ADMIN (CAT-US9).     ║
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
import { useState, useEffect, useCallback } from "react";
import { useAuth } from "./auth/AuthContext.jsx";
import { roleCanAccessRoute, getAccessibleRoutes } from "./auth/rbac.js";
import { getLowStockReport } from "./api.js";
import Login from "./Login.jsx";
import Catalogue from "./Catalogue.jsx";
import OrderForm from "./OrderForm.jsx";
import ReportingPlaceholder from "./ReportingPlaceholder.jsx";
import MerchantCreate from "./MerchantCreate.jsx";
import MerchantManagement from "./MerchantManagement.jsx";

/*
 * ── NAV_LABELS ───────────────────────────────────────────────────────────────
 *
 * Maps route identifiers to the text displayed in the navigation bar.
 * These must match the keys in ROUTE_PACKAGES (auth/rbac.js).
 */
const NAV_LABELS = {
  catalogue: "Catalogue",
  order: "Place Order",
  reporting: "Reporting",
  accounts: "Accounts",
  merchants: "Merchants",
};

/*
 * ── PAGE_COMPONENTS ──────────────────────────────────────────────────────────
 *
 * Maps route identifiers to the React component that renders that page.
 *
 * accounts   → MerchantCreate (admin-only: create new merchant accounts)
 * merchants  → MerchantManagement (manager+admin: view/edit merchant profiles)
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
  accounts: () => <MerchantCreate />,
  merchants: () => <MerchantManagement />,
};

function App() {
  /*
   * ── Auth State ─────────────────────────────────────────────────────────
   */
  const { user, loading, logout } = useAuth();

  /*
   * ── Page Navigation State ──────────────────────────────────────────────
   */
  const [currentPage, setCurrentPage] = useState(null);
  const [refreshKey, setRefreshKey] = useState(0);

  /*
   * ── Low-Stock Banner State (CAT-US9) ──────────────────────────────────
   * Fetches low-stock products for ADMIN users so a persistent warning
   * banner can be shown below the navigation on every page.
   */
  const [lowStockItems, setLowStockItems] = useState([]);
  const [bannerExpanded, setBannerExpanded] = useState(false);

  const fetchLowStock = useCallback(async () => {
    if (!user || user.role !== "ADMIN") return;
    try {
      const data = await getLowStockReport();
      setLowStockItems(data);
    } catch {
      /* Silently ignore — banner is supplementary. */
    }
  }, [user]);

  useEffect(() => {
    fetchLowStock();
  }, [fetchLowStock, refreshKey]);

  /*
   * ── Set Default Page When User Changes ─────────────────────────────────
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

  const handleOrderPlaced = () => {
    setRefreshKey((prev) => prev + 1);
    setCurrentPage("catalogue");
  };

  /* ── LOADING STATE ──────────────────────────────────────────────────────── */
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

  /* ── LOGIN GATE ─────────────────────────────────────────────────────────── */
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

  /* ── MAIN APP (authenticated) ───────────────────────────────────────────── */
  const accessibleRoutes = getAccessibleRoutes(user.role);

  const renderPage = () => {
    if (!currentPage || !PAGE_COMPONENTS[currentPage]) {
      return <p className="status-message">Select a page from the navigation.</p>;
    }

    /* RBAC route guard: double-check role access. */
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

      {/* ── Low-Stock Warning Banner (CAT-US9) ────────────────────────── */}
      {user.role === "ADMIN" && lowStockItems.length > 0 && (
        <div
          style={{
            background: "#fef2f2",
            borderBottom: "2px solid #dc2626",
            padding: "0.5rem 1.5rem",
            fontSize: "0.9rem",
            color: "#991b1b",
          }}
        >
          <strong>⚠ Low-stock warning:</strong>{" "}
          {lowStockItems.length} product{lowStockItems.length !== 1 ? "s" : ""}{" "}
          below minimum threshold.
          <button
            type="button"
            onClick={() => setBannerExpanded((prev) => !prev)}
            style={{
              marginLeft: "0.75rem",
              fontSize: "0.8rem",
              padding: "0.15rem 0.5rem",
              cursor: "pointer",
              background: "#fecaca",
              border: "1px solid #f87171",
              borderRadius: "4px",
              color: "#991b1b",
            }}
          >
            {bannerExpanded ? "Hide details" : "Show details"}
          </button>

          {bannerExpanded && (
            <table
              className="product-table"
              style={{
                marginTop: "0.5rem",
                fontSize: "0.85rem",
                background: "#fff",
                width: "100%",
              }}
            >
              <thead>
                <tr>
                  <th>Product ID</th>
                  <th>Description</th>
                  <th>Current Stock</th>
                  <th>Threshold</th>
                </tr>
              </thead>
              <tbody>
                {lowStockItems.map((p) => (
                  <tr key={p.id}>
                    <td>{p.productCode ?? "—"}</td>
                    <td>{p.description}</td>
                    <td style={{ color: "#dc2626", fontWeight: 600 }}>
                      {p.availabilityCount}
                    </td>
                    <td>{p.minStockThreshold}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      <main className="app-main">
        {renderPage()}
      </main>
    </div>
  );
}

export default App;
