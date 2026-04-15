/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Product catalogue — list, search, CAT-US1 initialize,                ║
 * ║        CAT-US2 admin create, CAT-US3 admin delete (Yes/No modal),           ║
 * ║        CAT-US4 admin edit, CAT-US5 multi-criteria search,                   ║
 * ║        CAT-US6 merchant stock masking, CAT-US7 stock delivery modal,        ║
 * ║        CAT-US8 min stock threshold, CAT-US9 low-stock warning (strict <).   ║
 * ║                                                                              ║
 * ║  WHY:  Single catalogue page for all roles. ADMIN sees admin tools (init,   ║
 * ║        create, edit, delete, + stock), full counts, min-stock thresholds,   ║
 * ║        and low-stock warnings. MERCHANT sees read-only table with           ║
 * ║        Available/Out of Stock labels. All roles can search.                 ║
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
  recordDelivery,
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

function formatPdfCell(v) {
  if (v == null || v === "") return "\u2014";
  return v;
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
    itemIdRange: "",
    itemIdSuffix: "",
    description: "",
    packageType: "",
    unit: "",
    unitsPerPack: "",
    price: "",
    availabilityCount: "0",
    minStockThreshold: "",
  });
  const [createBusy, setCreateBusy] = useState(false);
  const [createMessage, setCreateMessage] = useState(null);

  // CAT-US4: Edit state
  const [editingProduct, setEditingProduct] = useState(null);
  const [editForm, setEditForm] = useState({
    description: "",
    packageType: "",
    unit: "",
    unitsPerPack: "",
    price: "",
    availabilityCount: "",
    minStockThreshold: "",
  });
  const [editBusy, setEditBusy] = useState(false);
  const [editMessage, setEditMessage] = useState(null);

  // CAT-US3: Delete confirmation modal state
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteMessage, setDeleteMessage] = useState(null);

  // CAT-US7: Record delivery modal state
  const [deliveryTarget, setDeliveryTarget] = useState(null);
  const [deliveryForm, setDeliveryForm] = useState({
    deliveryDate: "",
    quantityReceived: "1",
    supplierReference: "",
  });
  const [deliveryBusy, setDeliveryBusy] = useState(false);
  const [deliveryMessage, setDeliveryMessage] = useState(null);

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
      const upp = parseInt(form.unitsPerPack, 10);
      if (Number.isNaN(upp) || upp < 1) {
        throw new Error("Units in a pack must be at least 1.");
      }
      await createProduct({
        itemIdRange: form.itemIdRange.trim(),
        itemIdSuffix: form.itemIdSuffix.trim(),
        description: form.description.trim(),
        packageType: form.packageType.trim(),
        unit: form.unit.trim(),
        unitsPerPack: upp,
        price: priceNum,
        availabilityCount: availNum,
        minStockThreshold: form.minStockThreshold !== "" ? form.minStockThreshold : null,
      });
      setForm({
        itemIdRange: "",
        itemIdSuffix: "",
        description: "",
        packageType: "",
        unit: "",
        unitsPerPack: "",
        price: "",
        availabilityCount: "0",
        minStockThreshold: "",
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
      packageType: product.packageType || "",
      unit: product.unit != null ? String(product.unit) : "",
      unitsPerPack: product.unitsPerPack != null ? String(product.unitsPerPack) : "1",
      price: product.price != null ? String(product.price) : "",
      availabilityCount: product.availabilityCount != null ? String(product.availabilityCount) : "0",
      minStockThreshold: product.minStockThreshold != null ? String(product.minStockThreshold) : "",
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
      const upp = parseInt(editForm.unitsPerPack, 10);
      if (Number.isNaN(upp) || upp < 1) {
        throw new Error("Units in a pack must be at least 1.");
      }
      await updateProduct(editingProduct.id, {
        description: editForm.description.trim(),
        packageType: editForm.packageType.trim(),
        unit: editForm.unit.trim(),
        unitsPerPack: upp,
        price: Number(editForm.price),
        availabilityCount: parseInt(editForm.availabilityCount, 10),
        minStockThreshold: editForm.minStockThreshold !== "" ? editForm.minStockThreshold : null,
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

  // CAT-US7: Open delivery modal for a product
  const startDelivery = (product) => {
    setDeliveryTarget(product);
    setDeliveryForm({ deliveryDate: "", quantityReceived: "1", supplierReference: "" });
    setDeliveryMessage(null);
  };

  const cancelDelivery = () => {
    setDeliveryTarget(null);
    setDeliveryMessage(null);
  };

  const handleRecordDelivery = async (e) => {
    e.preventDefault();
    setDeliveryBusy(true);
    setDeliveryMessage(null);
    try {
      const resp = await recordDelivery(deliveryTarget.id, {
        deliveryDate: deliveryForm.deliveryDate,
        quantityReceived: parseInt(deliveryForm.quantityReceived, 10),
        supplierReference: deliveryForm.supplierReference || undefined,
      });
      setDeliveryMessage(`Delivery recorded. New stock: ${resp.newAvailabilityCount}`);
      await loadProducts();
    } catch (err) {
      setDeliveryMessage(err.message);
    } finally {
      setDeliveryBusy(false);
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
            Initialize the catalogue once per system, then add products (InfoPharma fields: Item ID in
            two parts, description, package type, units per pack, package cost, availability in packs).
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
                gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))",
                gap: "0.75rem",
                alignItems: "end",
              }}
            >
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="itemIdRange">Item ID — range</label>
                <input
                  id="itemIdRange"
                  value={form.itemIdRange}
                  onChange={(e) => setForm((f) => ({ ...f, itemIdRange: e.target.value }))}
                  required
                  placeholder="e.g. 100"
                  autoComplete="off"
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="itemIdSuffix">Item ID — no.</label>
                <input
                  id="itemIdSuffix"
                  value={form.itemIdSuffix}
                  onChange={(e) => setForm((f) => ({ ...f, itemIdSuffix: e.target.value }))}
                  required
                  placeholder="e.g. 00001"
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
                <label htmlFor="packageType">Package type</label>
                <input
                  id="packageType"
                  value={form.packageType}
                  onChange={(e) => setForm((f) => ({ ...f, packageType: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="unit">Unit (opt.)</label>
                <input
                  id="unit"
                  value={form.unit}
                  onChange={(e) => setForm((f) => ({ ...f, unit: e.target.value }))}
                  placeholder="e.g. ml"
                  autoComplete="off"
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="unitsPerPack">Units in a pack</label>
                <input
                  id="unitsPerPack"
                  type="number"
                  min="1"
                  value={form.unitsPerPack}
                  onChange={(e) => setForm((f) => ({ ...f, unitsPerPack: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="price">Package cost (£)</label>
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
                <label htmlFor="availabilityCount">Availability (packs)</label>
                <input
                  id="availabilityCount"
                  type="number"
                  min="0"
                  value={form.availabilityCount}
                  onChange={(e) => setForm((f) => ({ ...f, availabilityCount: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label htmlFor="minStockThreshold">Stock limit (packs, opt.)</label>
                <input
                  id="minStockThreshold"
                  type="number"
                  min="0"
                  placeholder="No threshold"
                  value={form.minStockThreshold}
                  onChange={(e) => setForm((f) => ({ ...f, minStockThreshold: e.target.value }))}
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
        <div style={{ overflowX: "auto" }}>
          <table className="data-table">
            <thead>
              <tr>
                <th title="Item ID">Range</th>
                <th title="Item ID">No.</th>
                <th>Description</th>
                <th>Package type</th>
                <th>Unit</th>
                <th>Units / pack</th>
                <th>Package cost (&pound;)</th>
                <th>{isMerchant ? "Availability" : "Availability (packs)"}</th>
                {!isMerchant && <th>Stock limit (packs)</th>}
                {isAdmin && <th>Actions</th>}
              </tr>
            </thead>
            <tbody>
              {products.map((product) => (
                <tr key={product.id}>
                  <td>{formatPdfCell(product.itemIdRange)}</td>
                  <td>{formatPdfCell(product.itemIdSuffix)}</td>
                  <td>{product.description}</td>
                  <td>{formatPdfCell(product.packageType)}</td>
                  <td>{formatPdfCell(product.unit)}</td>
                  <td>{product.unitsPerPack != null ? product.unitsPerPack : "\u2014"}</td>
                  <td>&pound;{Number(product.price).toFixed(2)}</td>
                  <td>{formatAvailability(product, user?.role)}</td>
                  {!isMerchant && (
                    <td>
                      {product.minStockThreshold != null ? (
                        product.availabilityCount != null &&
                        product.availabilityCount < product.minStockThreshold ? (
                          <span style={{ color: "#dc2626", fontWeight: 600 }}>
                            {product.minStockThreshold} &#9888;
                          </span>
                        ) : (
                          product.minStockThreshold
                        )
                      ) : (
                        <span style={{ color: "#94a3b8" }}>&mdash;</span>
                      )}
                    </td>
                  )}
                  {isAdmin && (
                    <td style={{ whiteSpace: "nowrap" }}>
                      <button
                        type="button"
                        className="submit-btn"
                        style={{ marginRight: "0.4rem", padding: "0.25rem 0.6rem", fontSize: "0.8rem" }}
                        onClick={() => startEdit(product)}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="submit-btn"
                        style={{
                          marginRight: "0.4rem",
                          padding: "0.25rem 0.6rem",
                          fontSize: "0.8rem",
                          background: "#0891b2",
                        }}
                        onClick={() => startDelivery(product)}
                      >
                        + Stock
                      </button>
                      <button
                        type="button"
                        className="submit-btn"
                        style={{ padding: "0.25rem 0.6rem", fontSize: "0.8rem", background: "#dc2626" }}
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
        </div>
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
              maxWidth: "560px",
              maxHeight: "90vh",
              overflowY: "auto",
              boxShadow: "0 4px 24px rgba(0,0,0,0.2)",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginTop: 0 }}>
              Edit: {editingProduct.itemIdRange != null && editingProduct.itemIdSuffix != null
                ? `${editingProduct.itemIdRange}-${editingProduct.itemIdSuffix}`
                : editingProduct.productCode ?? editingProduct.id}
            </h3>
            <p style={{ color: "#64748b", fontSize: "0.9rem", marginTop: "-0.5rem" }}>
              Item ID is fixed. Change package details, cost, and stock below.
            </p>
            <form onSubmit={handleUpdateProduct} style={{ marginTop: "0.5rem" }}>
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
                <label htmlFor="edit-packageType">Package type</label>
                <input
                  id="edit-packageType"
                  value={editForm.packageType}
                  onChange={(e) => setEditForm((f) => ({ ...f, packageType: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="edit-unit">Unit (opt.)</label>
                <input
                  id="edit-unit"
                  value={editForm.unit}
                  onChange={(e) => setEditForm((f) => ({ ...f, unit: e.target.value }))}
                  placeholder="Leave blank if none"
                />
              </div>
              <div className="form-group">
                <label htmlFor="edit-unitsPerPack">Units in a pack</label>
                <input
                  id="edit-unitsPerPack"
                  type="number"
                  min="1"
                  value={editForm.unitsPerPack}
                  onChange={(e) => setEditForm((f) => ({ ...f, unitsPerPack: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="edit-price">Package cost (&pound;)</label>
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
                <label htmlFor="edit-availability">Availability (packs)</label>
                <input
                  id="edit-availability"
                  type="number"
                  min="0"
                  value={editForm.availabilityCount}
                  onChange={(e) => setEditForm((f) => ({ ...f, availabilityCount: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="edit-minStockThreshold">Stock limit (packs, opt.)</label>
                <input
                  id="edit-minStockThreshold"
                  type="number"
                  min="0"
                  placeholder="Leave blank to remove"
                  value={editForm.minStockThreshold}
                  onChange={(e) => setEditForm((f) => ({ ...f, minStockThreshold: e.target.value }))}
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

      {/* CAT-US7: Record stock delivery modal */}
      {deliveryTarget && (
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
          onClick={cancelDelivery}
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
              Record Delivery: {deliveryTarget.productCode ?? deliveryTarget.id}
            </h3>
            <p style={{ color: "#64748b", fontSize: "0.9rem", marginBottom: "1rem" }}>
              Current stock: {deliveryTarget.availabilityCount ?? 0}
            </p>
            <form onSubmit={handleRecordDelivery}>
              <div className="form-group">
                <label htmlFor="delivery-date">Delivery date</label>
                <input
                  id="delivery-date"
                  type="date"
                  value={deliveryForm.deliveryDate}
                  onChange={(e) => setDeliveryForm((f) => ({ ...f, deliveryDate: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="delivery-qty">Quantity received</label>
                <input
                  id="delivery-qty"
                  type="number"
                  min="1"
                  value={deliveryForm.quantityReceived}
                  onChange={(e) => setDeliveryForm((f) => ({ ...f, quantityReceived: e.target.value }))}
                  required
                />
              </div>
              <div className="form-group">
                <label htmlFor="delivery-ref">Supplier reference (opt.)</label>
                <input
                  id="delivery-ref"
                  value={deliveryForm.supplierReference}
                  onChange={(e) => setDeliveryForm((f) => ({ ...f, supplierReference: e.target.value }))}
                  placeholder="e.g. PO-20260401"
                />
              </div>
              {deliveryMessage && (
                <p
                  className={`status-message ${deliveryMessage.startsWith("Delivery recorded") ? "success" : "error"}`}
                  style={{ marginBottom: "0.75rem" }}
                >
                  {deliveryMessage}
                </p>
              )}
              <div style={{ display: "flex", gap: "0.75rem", justifyContent: "flex-end" }}>
                <button
                  type="button"
                  className="submit-btn"
                  style={{ background: "#64748b" }}
                  onClick={cancelDelivery}
                >
                  Cancel
                </button>
                <button type="submit" className="submit-btn" disabled={deliveryBusy}>
                  {deliveryBusy ? "Recording..." : "Record delivery"}
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
