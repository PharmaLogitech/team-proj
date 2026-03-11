/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A React component that displays the product catalogue.               ║
 * ║                                                                              ║
 * ║  WHY:  This is the main "read" view.  It fetches all products from the      ║
 * ║        Spring Boot backend on load and renders them as a table.             ║
 * ║        It demonstrates the core React data-fetching pattern:                ║
 * ║          1. Component mounts → useEffect fires → fetch data from API.       ║
 * ║          2. Data arrives → setState → component re-renders with data.       ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a search/filter input above the table.                         ║
 * ║        - Add a "Add Product" form for admins.                               ║
 * ║        - Add pagination for large catalogues.                               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { getProducts } from "./api.js";

/*
 * ── FUNCTIONAL COMPONENT ─────────────────────────────────────────────────────
 *
 * Catalogue is a function that returns JSX.  It takes no props (input
 * parameters) because it fetches its own data.  In more complex apps,
 * you might pass a "category" or "searchQuery" prop from a parent.
 *
 * ── PROPS ────────────────────────────────────────────────────────────────────
 *
 * Props (short for "properties") are how parent components pass data DOWN
 * to child components.  They flow ONE DIRECTION: parent → child.
 * A child can never modify its own props.
 *
 * Example:  <Catalogue category="painkillers" />
 *           Inside Catalogue: function Catalogue({ category }) { … }
 *
 * For Phase 1, this component has no props.  See OrderForm for a prop example.
 */
function Catalogue() {
  /*
   * ── useState: Managing Local Component State ─────────────────────────────
   *
   * products:  The array of product objects fetched from the backend.
   *            Starts as an empty array [].  Once the API responds,
   *            we call setProducts(data) which triggers a re-render
   *            and the table fills with rows.
   *
   * loading:   A boolean flag.  While the fetch is in progress, we show
   *            a "Loading…" message instead of an empty table.
   *
   * error:     If the fetch fails, we store the error message here and
   *            display it to the user.
   */
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  /*
   * ── useEffect: Fetching Data on Component Mount ─────────────────────────
   *
   * useEffect(callback, dependencyArray) runs SIDE EFFECTS — operations
   * that reach outside the component (API calls, timers, DOM manipulation).
   *
   * How it works:
   *   1. React renders the component for the first time.
   *   2. AFTER the render is painted to the screen, React runs the callback.
   *   3. The dependency array [] controls WHEN the effect re-runs:
   *        []            → Run ONCE after the first render only (mount).
   *        [someVar]     → Run on mount AND whenever someVar changes.
   *        (no array)    → Run after EVERY render (rarely what you want).
   *
   * We pass [] because we only want to fetch products once, when the
   * component first appears.  If we later needed real-time updates,
   * we could add a polling interval or WebSocket.
   *
   * IMPORTANT: useEffect callbacks cannot be async directly.
   * So we define an async function INSIDE and call it immediately.
   */
  useEffect(() => {
    async function fetchProducts() {
      try {
        setLoading(true);
        const data = await getProducts();
        setProducts(data);
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }

    fetchProducts();
  }, []);

  if (loading) return <p className="status-message">Loading catalogue...</p>;
  if (error) return <p className="status-message error">Error: {error}</p>;

  return (
    <div className="catalogue">
      <h2>Product Catalogue</h2>

      {products.length === 0 ? (
        <p className="status-message">
          No products yet. Add some via the API or database!
        </p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Description</th>
              <th>Price</th>
              <th>In Stock</th>
            </tr>
          </thead>
          <tbody>
            {/*
             * ── RENDERING LISTS ──────────────────────────────────────────────
             *
             * .map() transforms each element of an array into JSX.
             * React requires a unique "key" prop on each element so it
             * can efficiently update the DOM when items change.
             *
             * Using the database id as the key is ideal because it's
             * guaranteed unique and stable.  Avoid using array index
             * as key — it causes bugs when items are reordered or deleted.
             */}
            {products.map((product) => (
              <tr key={product.id}>
                <td>{product.id}</td>
                <td>{product.description}</td>
                <td>${Number(product.price).toFixed(2)}</td>
                <td>{product.availabilityCount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

export default Catalogue;
