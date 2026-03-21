/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A React component for placing an order (IPOS-SA-ORD).               ║
 * ║                                                                              ║
 * ║  WHY:  This demonstrates:                                                   ║
 * ║        1. A form with controlled inputs (value tied to state).              ║
 * ║        2. Fetching data on mount (products for the dropdown).              ║
 * ║        3. Building a request payload and POSTing it to the backend.         ║
 * ║        4. Handling success and error responses from the API.                ║
 * ║        5. Props — the parent passes an onOrderPlaced callback.              ║
 * ║                                                                              ║
 * ║  AUTHENTICATION:                                                             ║
 * ║        The currentUser prop comes from AuthContext (via App.jsx).           ║
 * ║        Orders are placed using the logged-in user's ID as merchantId.      ║
 * ║        The old "choose a merchant" dropdown is no longer needed — the       ║
 * ║        authenticated user IS the merchant.                                  ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        This page is only accessible to roles with IPOS-SA-ORD access.      ║
 * ║        The nav guard in App.jsx + rbac.js hides the "Place Order" button   ║
 * ║        from roles that don't have order access.                             ║
 * ║        Backend: /api/orders/** → authenticated (SecurityConfig.java).      ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add quantity validation (min 1, max = availabilityCount).          ║
 * ║        - Add ability to order multiple different products at once           ║
 * ║          (ORD-US1 child ticket 2).                                         ║
 * ║        - Show an order summary/confirmation before submitting.              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { getProducts, placeOrder } from "./api.js";

/*
 * ── PROPS ────────────────────────────────────────────────────────────────────
 *
 * onOrderPlaced — A callback function from App.jsx.  When the order
 *                 succeeds, we call this to switch back to the Catalogue
 *                 and refresh it (so stock levels update).
 *
 * currentUser   — The authenticated user { id, name, username, role }.
 *                 We use currentUser.id as the merchantId when placing orders.
 */
function OrderForm({ onOrderPlaced, currentUser }) {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  /* Form state for the selected product and quantity. */
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState(1);

  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);

  /*
   * Load products when the component mounts.
   * We need the product list to populate the dropdown.
   */
  useEffect(() => {
    async function fetchData() {
      try {
        const productsData = await getProducts();
        setProducts(productsData);
        if (productsData.length > 0) setProductId(productsData[0].id);
      } catch (err) {
        setMessage("Failed to load products: " + err.message);
        setIsError(true);
      } finally {
        setLoading(false);
      }
    }

    fetchData();
  }, []);

  /*
   * ── FORM SUBMISSION ───────────────────────────────────────────────────
   *
   * Uses the authenticated user's ID as the merchantId.
   * No more manual merchant selection — you order as yourself.
   */
  const handleSubmit = async (e) => {
    e.preventDefault();

    setSubmitting(true);
    setMessage(null);

    try {
      await placeOrder(currentUser.id, [
        { productId: Number(productId), quantity: Number(quantity) },
      ]);

      setMessage("Order placed successfully!");
      setIsError(false);

      setTimeout(() => {
        if (onOrderPlaced) onOrderPlaced();
      }, 1500);
    } catch (err) {
      setMessage(err.message);
      setIsError(true);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <p className="status-message">Loading form data...</p>;

  if (products.length === 0) {
    return (
      <div className="order-form">
        <h2>Place an Order</h2>
        <p className="status-message">
          No products found. An administrator must add products first.
        </p>
      </div>
    );
  }

  return (
    <div className="order-form">
      <h2>Place an Order</h2>

      {/* Show who is placing the order (the authenticated user). */}
      <p style={{ color: "#6b7280", fontSize: "0.9rem", marginBottom: "1rem" }}>
        Ordering as: <strong>{currentUser.name}</strong> ({currentUser.role})
      </p>

      <form onSubmit={handleSubmit}>
        {/* Product select — populated from the products API */}
        <div className="form-group">
          <label htmlFor="product">Product:</label>
          <select
            id="product"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
          >
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.description} — ${Number(product.price).toFixed(2)} (
                {product.availabilityCount} in stock)
              </option>
            ))}
          </select>
        </div>

        {/* Quantity input */}
        <div className="form-group">
          <label htmlFor="quantity">Quantity:</label>
          <input
            id="quantity"
            type="number"
            min="1"
            value={quantity}
            onChange={(e) => setQuantity(e.target.value)}
          />
        </div>

        <button type="submit" disabled={submitting} className="submit-btn">
          {submitting ? "Placing Order..." : "Place Order"}
        </button>
      </form>

      {message && (
        <p className={`status-message ${isError ? "error" : "success"}`}>
          {message}
        </p>
      )}
    </div>
  );
}

export default OrderForm;
