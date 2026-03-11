/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: The root React component — the top of the component tree.            ║
 * ║                                                                              ║
 * ║  WHY:  App.jsx is the single entry component rendered by main.jsx.          ║
 * ║        It holds "who is logged in" (currentUser).  If nobody is logged in,  ║
 * ║        we show the Login page.  Otherwise we show the main app (Catalogue,  ║
 * ║        OrderForm) and a Log out button.                                      ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add React Router for multi-page navigation.                        ║
 * ║        - Persist currentUser in localStorage so login survives refresh.     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState } from "react";
import Login from "./Login.jsx";
import Catalogue from "./Catalogue.jsx";
import OrderForm from "./OrderForm.jsx";

/*
 * ── FUNCTIONAL COMPONENTS ────────────────────────────────────────────────────
 *
 * In React, a "component" is just a JavaScript function that returns JSX
 * (HTML-like syntax).  This function is called every time the component
 * needs to re-render (e.g., when its state changes).
 *
 * The JSX returned by the function describes WHAT the UI should look like.
 * React takes care of efficiently updating the actual DOM to match.
 *
 * Components can contain other components, forming a tree:
 *   <App>
 *     ├── <Catalogue />
 *     └── <OrderForm />
 */
function App() {
  /*
   * ── currentUser: "Who is logged in?" ─────────────────────────────────────
   *
   * null       → Nobody logged in.  We show the <Login /> page.
   * { id, name, role } → A user has logged in.  We show the main app and pass
   *                      this to OrderForm so it can default the merchant.
   *
   * The Login component calls onLogin(user) with the selected user;
   * we pass setCurrentUser so that updates this state and re-renders.
   */
  const [currentUser, setCurrentUser] = useState(null);

  const [currentPage, setCurrentPage] = useState("catalogue");
  const [refreshKey, setRefreshKey] = useState(0);

  const handleOrderPlaced = () => {
    setRefreshKey((prev) => prev + 1);
    setCurrentPage("catalogue");
  };

  /*
   * ── GUARD: Show Login when not logged in ──────────────────────────────────
   *
   * If there is no currentUser, we only render the Login screen.
   * The rest of the app (header, nav, catalogue, order form) is hidden.
   * When the user submits the login form, Login calls setCurrentUser(user),
   * which updates state and causes App to re-render and show the main app.
   */
  if (!currentUser) {
    return (
      <div className="app">
        <header className="app-header">
          <h1>IPOS-SA</h1>
          <p className="subtitle">Pharmaceutical Stock & Order Management</p>
        </header>
        <main className="app-main">
          <Login onLogin={setCurrentUser} />
        </main>
      </div>
    );
  }

  /*
   * ── MAIN APP: Shown only when currentUser is set ───────────────────────────
   */
  return (
    <div className="app">
      <header className="app-header">
        <h1>IPOS-SA</h1>
        <p className="subtitle">Pharmaceutical Stock & Order Management</p>

        <div className="app-header-actions">
          <span className="current-user">Logged in as {currentUser.name} ({currentUser.role})</span>
          <button
            type="button"
            className="logout-btn"
            onClick={() => setCurrentUser(null)}
          >
            Log out
          </button>
        </div>

        <nav className="app-nav">
          <button
            className={currentPage === "catalogue" ? "active" : ""}
            onClick={() => setCurrentPage("catalogue")}
          >
            Catalogue
          </button>
          <button
            className={currentPage === "order" ? "active" : ""}
            onClick={() => setCurrentPage("order")}
          >
            Place Order
          </button>
        </nav>
      </header>

      <main className="app-main">
        {currentPage === "catalogue" ? (
          <Catalogue key={refreshKey} />
        ) : (
          <OrderForm
            onOrderPlaced={handleOrderPlaced}
            currentUser={currentUser}
          />
        )}
      </main>
    </div>
  );
}

/*
 * "export default" makes this the main export of the file.
 * Other files import it as:  import App from "./App.jsx";
 */
export default App;
