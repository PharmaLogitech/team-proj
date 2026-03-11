/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A React component for placing an order.                              ║
 * ║                                                                              ║
 * ║  WHY:  This demonstrates:                                                   ║
 * ║        1. A form with controlled inputs (value tied to state).              ║
 * ║        2. Fetching data on mount (products and users for dropdowns).        ║
 * ║        3. Building a request payload and POSTing it to the backend.         ║
 * ║        4. Handling success and error responses from the API.                ║
 * ║        5. Props — the parent passes an onOrderPlaced callback.              ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add quantity validation (min 1, max = availabilityCount).          ║
 * ║        - Add ability to order multiple different products at once.          ║
 * ║        - Show an order summary/confirmation before submitting.              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { getProducts, getUsers, placeOrder } from "./api.js";

/*
 * ── PROPS ────────────────────────────────────────────────────────────────────
 *
 * This component receives ONE prop from its parent (App.jsx):
 *
 *   onOrderPlaced — A callback function.  When the order succeeds, we call
 *                   onOrderPlaced() to notify the parent, which then
 *                   switches back to the Catalogue and refreshes it.
 *
 *   currentUser   — Optional.  The user who is logged in { id, name, role }.
 *                   If present and role is MERCHANT, we default the merchant
 *                   dropdown to this user so they order as themselves.
 *
 * Props are the standard way for child → parent communication in React.
 * The parent defines the function, passes it down as a prop, and the child
 * calls it when something important happens.
 *
 * DATA FLOW IN REACT:
 *   - Data flows DOWN via props (parent → child).
 *   - Events flow UP via callbacks (child → parent).
 *   This is called "one-way data flow" and keeps the code predictable.
 */
function OrderForm({ onOrderPlaced, currentUser }) {
  const [products, setProducts] = useState([]);
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);

  /*
   * Form state — these correspond to the form inputs.
   * When the user types or selects, we update state, which causes a
   * re-render, which updates the input's displayed value.  This is
   * called a "controlled component" pattern: React state is the
   * single source of truth for the input's value.
   */
  const [merchantId, setMerchantId] = useState("");
  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState(1);

  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);

  /*
   * Load products and users when the component mounts.
   * We need both to populate the dropdown menus.
   */
  useEffect(() => {
    async function fetchData() {
      try {
        const [productsData, usersData] = await Promise.all([
          getProducts(),
          getUsers(),
        ]);
        setProducts(productsData);
        setUsers(usersData);

        if (productsData.length > 0) setProductId(productsData[0].id);
        if (usersData.length > 0) {
          // If the logged-in user is a MERCHANT, default the dropdown to them.
          const defaultMerchantId =
            currentUser?.role === "MERCHANT"
              ? currentUser.id
              : usersData[0].id;
          setMerchantId(defaultMerchantId);
        }
      } catch (err) {
        setMessage("Failed to load form data: " + err.message);
        setIsError(true);
      } finally {
        setLoading(false);
      }
    }

    fetchData();
  }, [currentUser?.id, currentUser?.role]);

  /*
   * ── FORM SUBMISSION HANDLER ───────────────────────────────────────────────
   *
   * This is an async function that:
   *   1. Prevents the browser's default form submission (page reload).
   *   2. Calls our placeOrder() API function.
   *   3. Shows a success or error message.
   *   4. Notifies the parent via the onOrderPlaced callback on success.
   */
  const handleSubmit = async (e) => {
    e.preventDefault();

    setSubmitting(true);
    setMessage(null);

    try {
      await placeOrder(Number(merchantId), [
        { productId: Number(productId), quantity: Number(quantity) },
      ]);

      setMessage("Order placed successfully!");
      setIsError(false);

      /*
       * After a brief delay, call the parent's callback so the user
       * can see the success message before switching pages.
       */
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

  if (users.length === 0 || products.length === 0) {
    return (
      <div className="order-form">
        <h2>Place an Order</h2>
        <p className="status-message">
          {users.length === 0
            ? "No users found. Create a user first (POST /api/users)."
            : "No products found. Create products first (POST /api/products)."}
        </p>
      </div>
    );
  }

  return (
    <div className="order-form">
      <h2>Place an Order</h2>

      <form onSubmit={handleSubmit}>
        {/* Merchant select — populated from the users API */}
        <div className="form-group">
          <label htmlFor="merchant">Merchant:</label>
          <select
            id="merchant"
            value={merchantId}
            onChange={(e) => setMerchantId(e.target.value)}
          >
            {users
              .filter((u) => u.role === "MERCHANT")
              .map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name} (ID: {user.id})
                </option>
              ))}
          </select>
        </div>

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

      {/* Success or error message */}
      {message && (
        <p className={`status-message ${isError ? "error" : "success"}`}>
          {message}
        </p>
      )}
    </div>
  );
}

export default OrderForm;
