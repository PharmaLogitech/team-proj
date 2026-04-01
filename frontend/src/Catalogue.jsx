/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Product catalogue — list, search, CAT-US1 initialize,                ║
 * ║        CAT-US2 admin create, CAT-US3 admin delete (Yes/No modal),           ║
 * ║        CAT-US4 admin edit, CAT-US5 multi-criteria search,                   ║
 * ║        CAT-US6 merchant stock masking.                                      ║
 * ║                                                                              ║
 * ║  WHY:  Single catalogue page for all roles. ADMIN sees admin tools (init,   ║
 * ║        create, edit, delete) and full stock counts. MERCHANT sees read-only  ║
 * ║        table with Available/Out of Stock labels instead of numeric counts.   ║
 * ║        All roles can search by Product ID, description, and price range.     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
import { useState, useEffect, useCallback } from "react";
import {
  getProducts,
  searchProducts,
  createProduct,
  updateProduct,
  deleteProduct,
  getCatalogueStatus,
  initializeCatalogue,
} from "./api.js";
import { useAuth } from "./auth/AuthContext.jsx";

/**
 * Formats the availability column based on role (CAT-US6).
 * MERCHANT sees "Available" or "Out of Stock"; others see the numeric count.
 */
function formatAvailability(product, role) {
  if (role === "MERCHANT") {
    return product.availabilityStatus === "AVAILABLE" ? "Available" : "Out of Stock";
  }
  return product.availabilityCount ?? "\u2014";
}

function Catalogue() {
  const { user } = useAuth();
  const isAdmin = user?.role === "ADMIN";
  const isMerchant = user?.role === "MERCHANT";

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

  // CAT-US4: Edit state
  const [editingProduct, setEditingProduct] = useState(null);
  const [editForm, setEditForm] = useState({ description: "", price: "", availabilityCount: "" });
  const [editBusy, setEditBusy] = useState(false);
  const [editMessage, setEditMessage] = useState(null);

  // CAT-US3: Delete confirmation modal state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteMessage, setDeleteMessage] = useState(null);

  // CAT-US5/US6: Search state
  const [searchForm, setSearchForm] = useState({
    productCode: "",
    q: "",
    minPrice: "",
    maxPrice: "",
  });
  const [searchActive, setSearchActive] = useState(false);
  const [searchBusy, setSearchBusy] = useState(false);
  const [searchError, setSearchError] = useState(null);

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

  // CAT-US4: Start editing a product row
  const startEdit = (product) => {
    setEditingProduct(product);
    setEditForm({
      description: product.description || "",
      price: product.price != null ? String(product.price) : "",
      availabilityCount: product.availabilityCount != null ? String(product.availabilityCount) : "0",
    });
    setEditMessage(null);
  };

  const cancelEdit = () => {
    setEditingProduct(null);
    setEditMessage(null);
  };

  const handleUpdateProduct = async (e) => {
    e.preventDefault();
    setEditBusy(true);
    setEditMessage(null);
    try {
      await updateProduct(editingProduct.id, {
        description: editForm.description.trim(),
        price: Number(editForm.price),
        availabilityCount: parseInt(editForm.availabilityCount, 10),
      });
      setEditingProduct(null);
      setEditMessage(null);
      await loadProducts();
    } catch (err) {
      setEditMessage(err.message);
    } finally {
      setEditBusy(false);
    }
  };

  // CAT-US3: Delete with Yes/No confirmation
  const confirmDelete = (product) => {
    setDeleteTarget(product);
    setDeleteMessage(null);
  };

  const cancelDelete = () => {
    setDeleteTarget(null);
    setDeleteMessage(null);
  };

  const handleDeleteProduct = async () => {
    setDeleteBusy(true);
    setDeleteMessage(null);
    try {
      await deleteProduct(deleteTarget.id);
      setDeleteTarget(null);
      await loadProducts();
    } catch (err) {
      setDeleteMessage(err.message);
    } finally {
      setDeleteBusy(false);
    }
  };

  // CAT-US5: Search handler
  const handleSearch = async (e) => {
    e.preventDefault();
    setSearchBusy(true);
    setSearchError(null);
    try {
      const results = await searchProducts({
        productCode: searchForm.productCode.trim() || undefined,
        q: searchForm.q.trim() || undefined,
        minPrice: searchForm.minPrice || undefined,
        maxPrice: searchForm.maxPrice || undefined,
      });
      setProducts(results);
      setSearchActive(true);
    } catch (err) {
      setSearchError(err.message);
    } finally {
      setSearchBusy(false);
    }
  };

  // CAT-US5: Clear search and reload full catalogue
  const handleClearSearch = async () => {
    setSearchForm({ productCode: "", q: "", minPrice: "", maxPrice: "" });
    setSearchActive(false);
    setSearchError(null);
    try {
      await loadProducts();
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) return <p className="status-message">Loading catalogue...</p>;
  if (error) return <p className="status-message error">Error: {error}</p>;

  return (
    <div className="catalogue">
      <h2>Product Catalogue</h2>

      {/* ── Admin tools (init, create) ────────────────────────────────── */}
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

      {/* ── CAT-US5/US6: Search bar (all roles) ──────────────────────── */}
      <div
        style={{
          marginBottom: "1.5rem",
          padding: "1rem",
          background: "#f8fafc",
          borderRadius: "8px",
          border: "1px solid #e2e8f0",
        }}
      >
        <h3 style={{ marginTop: 0, fontSize: "1.1rem" }}>Search products</h3>
        <form onSubmit={handleSearch}>
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))",
              gap: "0.75rem",
              alignItems: "end",
            }}
          >
            {!isMerchant && (
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="search-productCode">Product ID</label>
                <input
                  id="search-productCode"
                  value={searchForm.productCode}
                  onChange={(e) => setSearchForm((f) => ({ ...f, productCode: e.target.value }))}
                  placeholder="e.g. PARA"
                  autoComplete="off"
                />
              </div>
            )}
            <div className="form-group" style={{ marginBottom: 0 }}>
              <label htmlFor="search-q">Description</label>
              <input
                id="search-q"
                value={searchForm.q}
                onChange={(e) => setSearchForm((f) => ({ ...f, q: e.target.value }))}
                placeholder="e.g. paracetamol"
              />
            </div>
            {!isMerchant && (
              <>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label htmlFor="search-minPrice">Min price</label>
                  <input
                    id="search-minPrice"
                    type="number"
                    step="0.01"
                    min="0"
                    value={searchForm.minPrice}
                    onChange={(e) => setSearchForm((f) => ({ ...f, minPrice: e.target.value }))}
                    placeholder="0.00"
                  />
                </div>
                <div className="form-group" style={{ marginBottom: 0 }}>
                  <label htmlFor="search-maxPrice">Max price</label>
                  <input
                    id="search-maxPrice"
                    type="number"
                    step="0.01"
                    min="0"
                    value={searchForm.maxPrice}
                    onChange={(e) => setSearchForm((f) => ({ ...f, maxPrice: e.target.value }))}
                    placeholder="100.00"
                  />
                </div>
              </>
            )}
            <div style={{ display: "flex", gap: "0.5rem" }}>
              <button type="submit" className="submit-btn" disabled={searchBusy}>
                {searchBusy ? "Searching..." : "Search"}
              </button>
              {searchActive && (
                <button
                  type="button"
                  className="submit-btn"
                  style={{ background: "#64748b" }}
                  onClick={handleClearSearch}
                >
                  Clear
                </button>
              )}
            </div>
          </div>
          {searchError && (
            <p className="status-message error" style={{ marginTop: "0.75rem", marginBottom: 0 }}>
              {searchError}
            </p>
          )}
        </form>
      </div>

      {/* ── Product table ─────────────────────────────────────────────── */}
      {products.length === 0 ? (
        <p className="status-message">
          {searchActive
            ? "No products found."
            : `No products yet.${isAdmin ? " Use the form above to add products." : " An administrator must add products first."}`}
        </p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              {!isMerchant && <th>Product ID</th>}
              <th>Description</th>
              <th>Price</th>
              <th>{isMerchant ? "Availability" : "In Stock"}</th>
              {isAdmin && <th>Actions</th>}
            </tr>
          </thead>
          <tbody>
            {products.map((product) => (
              <tr key={product.id}>
                {!isMerchant && <td>{product.productCode ?? "\u2014"}</td>}
                <td>{product.description}</td>
                <td>&pound;{Number(product.price).toFixed(2)}</td>
                <td>{formatAvailability(product, user?.role)}</td>
                {isAdmin && (
                  <td>
                    <button
                      type="button"
                      className="submit-btn"
                      style={{ marginRight: "0.5rem", padding: "0.25rem 0.75rem", fontSize: "0.85rem" }}
                      onClick={() => startEdit(product)}
                    >
                      Edit
                    </button>
                    <button
                      type="button"
                      className="submit-btn"
                      style={{ padding: "0.25rem 0.75rem", fontSize: "0.85rem", background: "#dc2626" }}
                      onClick={() => confirmDelete(product)}
                    >
                      Delete
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {/* CAT-US4: Edit product modal */}
      {editingProduct && (
        <div
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: "rgba(0,0,0,0.4)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
          }}
          onClick={cancelEdit}
        >
          <div
            style={{
              background: "#fff",
              borderRadius: "8px",
              padding: "1.5rem",
              minWidth: "340px",
              maxWidth: "480px",
              boxShadow: "0 4px 24px rgba(0,0,0,0.2)",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginTop: 0 }}>
              Edit Product: {editingProduct.productCode ?? editingProduct.id}
            </h3>
            <form onSubmit={handleUpdateProduct}>
              <div className="form-group">
                <label htmlFor="edit-description">Description</label>
                <input
                  id="edit-description"
                  value={editForm.description}
                  onChange={(e) => setEditForm((f) => ({ ...f, description: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="edit-price">Unit price</label>
                <input
                  id="edit-price"
                  type="number"
                  step="0.01"
                  min="0.01"
                  value={editForm.price}
                  onChange={(e) => setEditForm((f) => ({ ...f, price: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="edit-availability">Availability</label>
                <input
                  id="edit-availability"
                  type="number"
                  min="0"
                  value={editForm.availabilityCount}
                  onChange={(e) => setEditForm((f) => ({ ...f, availabilityCount: e.target.value }))}
                  required
                />
              </div>
              {editMessage && (
                <p className="status-message error" style={{ marginBottom: "0.75rem" }}>
                  {editMessage}
                </p>
              )}
              <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end" }}>
                <button type="button" className="submit-btn" style={{ background: "#64748b" }} onClick={cancelEdit}>
                  Cancel
                </button>
                <button type="submit" className="submit-btn" disabled={editBusy}>
                  {editBusy ? "Saving..." : "Save changes"}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* CAT-US3: Delete confirmation modal (Yes / No) */}
      {deleteTarget && (
        <div
          style={{
            position: "fixed",
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            background: "rgba(0,0,0,0.4)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            zIndex: 1000,
          }}
          onClick={cancelDelete}
        >
          <div
            style={{
              background: "#fff",
              borderRadius: "8px",
              padding: "1.5rem",
              minWidth: "320px",
              maxWidth: "440px",
              boxShadow: "0 4px 24px rgba(0,0,0,0.2)",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginTop: 0 }}>Confirm Deletion</h3>
            <p>
              Are you sure you want to delete product{" "}
              <strong>{deleteTarget.productCode ?? deleteTarget.id}</strong>
              {deleteTarget.description ? ` (${deleteTarget.description})` : ""}?
            </p>
            {deleteMessage && (
              <p className="status-message error" style={{ marginBottom: "0.75rem" }}>
                {deleteMessage}
              </p>
            )}
            <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end" }}>
              <button
                type="button"
                className="submit-btn"
                style={{ background: "#64748b" }}
                onClick={cancelDelete}
                disabled={deleteBusy}
              >
                No
              </button>
              <button
                type="button"
                className="submit-btn"
                style={{ background: "#dc2626" }}
                onClick={handleDeleteProduct}
                disabled={deleteBusy}
              >
                {deleteBusy ? "Deleting..." : "Yes"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default Catalogue;
