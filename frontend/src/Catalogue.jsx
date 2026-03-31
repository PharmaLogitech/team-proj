/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Product catalogue — list, CAT-US1 initialize, CAT-US2 admin create.   ║
 * ║                                                                              ║
 * ║  WHY:  Read-only table for all roles; ADMIN gets initialize + add product.   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect, useCallback } from "react";
import {
  getProducts,
  createProduct,
  getCatalogueStatus,
  initializeCatalogue,
} from "./api.js";
import { useAuth } from "./auth/AuthContext.jsx";

function Catalogue() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";

  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [catalogueInitialized, setCatalogueInitialized] = useState(null);
  const [initBusy, setInitBusy] = useState(false);
  const [initMessage, setInitMessage] = useState(null);

  const [form, setForm] = useState({
    productCode: "",
    description: "",
    price: "",
    availabilityCount: "0",
  });
  const [createBusy, setCreateBusy] = useState(false);
  const [createMessage, setCreateMessage] = useState(null);

  const loadProducts = useCallback(async () => {
    const data = await getProducts();
    setProducts(data);
  }, []);

  useEffect(() => {
    async function load() {
      try {
        setLoading(true);
        setError(null);
        await loadProducts();
        if (isAdmin) {
          const status = await getCatalogueStatus();
          setCatalogueInitialized(!!status.initialized);
        }
      } catch (err) {
        setError(err.message);
      } finally {
        setLoading(false);
      }
    }
    load();
  }, [isAdmin, loadProducts]);

  const handleInitialize = async () => {
    setInitBusy(true);
    setInitMessage(null);
    try {
      await initializeCatalogue();
      setCatalogueInitialized(true);
      setInitMessage("Catalogue initialized.");
    } catch (err) {
      setInitMessage(err.message);
    } finally {
      setInitBusy(false);
    }
  };

  const handleCreateProduct = async (e) => {
    e.preventDefault();
    setCreateBusy(true);
    setCreateMessage(null);
    try {
      const priceNum = Number(form.price);
      const availNum = parseInt(form.availabilityCount, 10);
      await createProduct({
        productCode: form.productCode.trim(),
        description: form.description.trim(),
        price: priceNum,
        availabilityCount: availNum,
      });
      setForm({
        productCode: "",
        description: "",
        price: "",
        availabilityCount: "0",
      });
      setCreateMessage("Product created.");
      await loadProducts();
      if (isAdmin) {
        const status = await getCatalogueStatus();
        setCatalogueInitialized(!!status.initialized);
      }
    } catch (err) {
      setCreateMessage(err.message);
    } finally {
      setCreateBusy(false);
    }
  };

  if (loading) return <p className="status-message">Loading catalogue...</p>;
  if (error) return <p className="status-message error">Error: {error}</p>;

  return (
    <div className="catalogue">
      <h2>Product Catalogue</h2>

      {isAdmin && (
        <div
          className="catalogue-admin"
          style={{
            marginBottom: "1.5rem",
            padding: "1rem",
            background: "#f8fafc",
            borderRadius: "8px",
            border: "1px solid #e2e8f0",
          }}
        >
          <h3 style={{ marginTop: 0, fontSize: "1.1rem" }}>Catalogue administration</h3>
          <p style={{ color: "#64748b", fontSize: "0.9rem", marginBottom: "0.75rem" }}>
            Initialize the catalogue once per system, then add products (unique Product ID, price
            &gt; 0, availability ≥ 0).
          </p>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem", alignItems: "center" }}>
            <button
              type="button"
              className="submit-btn"
              disabled={initBusy || catalogueInitialized === true}
              onClick={handleInitialize}
            >
              {initBusy ? "Initializing..." : "Initialize catalogue"}
            </button>
            {catalogueInitialized === false && (
              <span className="status-message" style={{ margin: 0 }}>
                Catalogue not yet registered.
              </span>
            )}
            {catalogueInitialized === true && (
              <span className="status-message success" style={{ margin: 0 }}>
                Catalogue registered.
              </span>
            )}
          </div>
          {initMessage && (
            <p
              className={`status-message ${initMessage.includes("already") || initMessage.includes("Conflict") ? "error" : ""}`}
              style={{ marginTop: "0.75rem", marginBottom: 0 }}
            >
              {initMessage}
            </p>
          )}

          <form onSubmit={handleCreateProduct} style={{ marginTop: "1.25rem" }}>
            <h4 style={{ marginBottom: "0.75rem" }}>Add product</h4>
            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))",
                gap: "0.75rem",
                alignItems: "end",
              }}
            >
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="productCode">Product ID</label>
                <input
                  id="productCode"
                  value={form.productCode}
                  onChange={(e) => setForm((f) => ({ ...f, productCode: e.target.value }))}
                  required
                  autoComplete="off"
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="description">Description</label>
                <input
                  id="description"
                  value={form.description}
                  onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="price">Unit price</label>
                <input
                  id="price"
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={form.price}
                  onChange={(e) => setForm((f) => ({ ...f, price: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="availabilityCount">Availability</label>
                <input
                  id="availabilityCount"
                  type="number"
                  min="0"
                  value={form.availabilityCount}
                  onChange={(e) => setForm((f) => ({ ...f, availabilityCount: e.target.value }))}
                  required
                />
              </div>
              <button type="submit" className="submit-btn" disabled={createBusy}>
                {createBusy ? "Saving..." : "Add product"}
              </button>
            </div>
            {createMessage && (
              <p
                className={`status-message ${createMessage.includes("created") ? "success" : "error"}`}
                style={{ marginTop: "0.75rem", marginBottom: 0 }}
              >
                {createMessage}
              </p>
            )}
          </form>
        </div>
      )}

      {products.length === 0 ? (
        <p className="status-message">
          No products yet.
          {isAdmin ? " Use the form above to add products." : " An administrator must add products first."}
        </p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Product ID</th>
              <th>Description</th>
              <th>Price</th>
              <th>In Stock</th>
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                <td>{product.productCode ?? "—"}</td>
                <td>{product.description}</td>
                <td>£{Number(product.price).toFixed(2)}</td>
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
