/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A React component for placing an order (IPOS-SA-ORD).               ║
 * ║                                                                              ║
 * ║  WHY:  This demonstrates:                                                   ║
 * ║        1. A form with controlled inputs (value tied to state).              ║
 * ║        2. Fetching data on mount (products for the dropdown).              ║
 * ║        3. Building a request payload and POSTing it to the backend.         ║
 * ║        4. Handling success and error responses from the API.                ║
 * ║        5. Displaying the financial breakdown (gross, discounts, totalDue)   ║
 * ║           after a successful order placement.                               ║
 * ║                                                                              ║
 * ║  AUTHENTICATION:                                                             ║
 * ║        The currentUser prop comes from AuthContext (via App.jsx).           ║
 * ║        Orders are placed using the logged-in user's ID as merchantId.      ║
 * ║        ORD-US1: The backend forces merchantId to the caller's own ID       ║
 * ║        for MERCHANT users — merchants cannot order for others.             ║
 * ║                                                                              ║
 * ║  DISCOUNT DISPLAY (brief §i):                                                ║
 * ║        After a successful order, the component displays:                    ║
 * ║          - Gross Total: sum of line items before discount.                  ║
 * ║          - Fixed Discount: amount deducted (FIXED plan only).              ║
 * ║          - Flexible Credit Applied: credit consumed (FLEXIBLE plan only).  ║
 * ║          - Total Due: final amount owed.                                   ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        This page is only accessible to roles with IPOS-SA-ORD access.      ║
 * ║        Nav guard in App.jsx + rbac.js hides it from unauthorised roles.    ║
 * ║        Backend: /api/orders/** → authenticated (SecurityConfig.java).      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect } from "react";
import { getProducts, placeOrder } from "./api.js";

function OrderForm({ onOrderPlaced, currentUser }) {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);

  const [productId, setProductId] = useState("");
  const [quantity, setQuantity] = useState(1);

  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [isError, setIsError] = useState(false);

  /* Stores the order response after a successful placement. */
  const [orderResult, setOrderResult] = useState(null);

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

  const handleSubmit = async (e) => {
    e.preventDefault();

    setSubmitting(true);
    setMessage(null);
    setOrderResult(null);

    try {
      const result = await placeOrder(currentUser.id, [
        { productId: Number(productId), quantity: Number(quantity) },
      ]);

      setOrderResult(result);
      setMessage("Order placed successfully!");
      setIsError(false);

      setTimeout(() => {
        if (onOrderPlaced) onOrderPlaced();
      }, 3000);
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

      <p style={{ color: "#6b7280", fontSize: "0.9rem", marginBottom: "1rem" }}>
        Ordering as: <strong>{currentUser.name}</strong> ({currentUser.role})
      </p>

      <form onSubmit={handleSubmit}>
        <div className="form-group">
          <label htmlFor="product">Product:</label>
          <select
            id="product"
            value={productId}
            onChange={(e) => setProductId(e.target.value)}
          >
            {products.map((product) => (
              <option key={product.id} value={product.id}>
                {product.description} — £{Number(product.price).toFixed(2)} (
                {product.availabilityCount} in stock)
              </option>
            ))}
          </select>
        </div>

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

      {/*
       * ── Order Financial Breakdown (brief §i) ──────────────────────────
       * Displayed after a successful order to show how discounts were applied.
       */}
      {orderResult && orderResult.grossTotal != null && (
        <div className="order-breakdown">
          <h3>Order Summary</h3>
          <table className="data-table">
            <tbody>
              <tr>
                <td>Gross Total</td>
                <td>£{Number(orderResult.grossTotal).toFixed(2)}</td>
              </tr>
              {Number(orderResult.fixedDiscountAmount) > 0 && (
                <tr>
                  <td>Fixed Discount</td>
                  <td>-£{Number(orderResult.fixedDiscountAmount).toFixed(2)}</td>
                </tr>
              )}
              {Number(orderResult.flexibleCreditApplied) > 0 && (
                <tr>
                  <td>Flexible Credit Applied</td>
                  <td>-£{Number(orderResult.flexibleCreditApplied).toFixed(2)}</td>
                </tr>
              )}
              <tr className="total-row">
                <td><strong>Total Due</strong></td>
                <td><strong>£{Number(orderResult.totalDue).toFixed(2)}</strong></td>
              </tr>
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default OrderForm;
