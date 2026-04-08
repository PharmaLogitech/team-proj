/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A centralized API service module.                                    ║
 * ║                                                                              ║
 * ║  WHY:  Instead of writing fetch() calls scattered across every component,   ║
 * ║        we put ALL backend communication in one file.  This gives us:        ║
 * ║          1. A single place to change the base URL or add auth headers.      ║
 * ║          2. Consistent error handling across the app.                        ║
 * ║          3. Components that are easier to read (they just call api.xxx()).   ║
 * ║                                                                              ║
 * ║  AUTHENTICATION:                                                             ║
 * ║        All functions use fetchWithAuth() from AuthContext.jsx, which         ║
 * ║        automatically:                                                       ║
 * ║          - Includes session cookies (credentials: "include").              ║
 * ║          - Adds the CSRF token header for mutating requests.               ║
 * ║        This means components don't need to worry about auth headers or      ║
 * ║        CSRF — it's all handled centrally.                                   ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add new functions for each endpoint (e.g., api.updateProduct()).   ║
 * ║        - All new functions should use fetchWithAuth() for consistency.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

import { fetchWithAuth } from "./auth/AuthContext.jsx";

const API_BASE = "/api";

async function parseResponseBody(response) {
  const text = await response.text();
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

function errorMessageFromBody(body, response) {
  if (body && typeof body === "object") {
    if (typeof body.message === "string" && body.message) return body.message;
    if (typeof body.error === "string" && body.error) return body.error;
    if (Array.isArray(body.errors) && body.errors.length > 0) {
      const first = body.errors[0];
      if (typeof first === "string") return first;
      if (first?.defaultMessage) return first.defaultMessage;
      if (first?.message) return first.message;
    }
  }
  return response.statusText || "Request failed";
}

/* ── Products ─────────────────────────────────────────────────────────────── */

/**
 * Fetch ALL products from the backend catalogue.
 * GET /api/products → returns JSON array of CatalogueProductDto objects.
 * Response shape varies by role (CAT-US6):
 *   ADMIN/MANAGER: { id, productCode, description, price, availabilityCount, availabilityStatus }
 *   MERCHANT:      { id, productCode, description, price, availabilityStatus }
 *                  (availabilityCount is omitted from JSON)
 *
 * ACCESS: All authenticated users (MERCHANT, MANAGER, ADMIN).
 */
export async function getProducts() {
  const response = await fetchWithAuth(`${API_BASE}/products`);
  if (!response.ok) {
    throw new Error("Failed to fetch products");
  }
  return response.json();
}

/**
 * Search products with optional combined filters (CAT-US5/US6).
 * GET /api/products/search?productCode=&q=&minPrice=&maxPrice=
 * All params are optional; omitted params apply no filter. AND logic.
 * Returns same CatalogueProductDto shape as getProducts().
 *
 * ACCESS: All authenticated users (search available to all roles).
 */
export async function searchProducts({ productCode, q, minPrice, maxPrice } = {}) {
  const params = new URLSearchParams();
  if (productCode) params.set("productCode", productCode);
  if (q) params.set("q", q);
  if (minPrice != null && minPrice !== "") params.set("minPrice", String(minPrice));
  if (maxPrice != null && maxPrice !== "") params.set("maxPrice", String(maxPrice));
  const qs = params.toString();
  const url = qs ? `${API_BASE}/products/search?${qs}` : `${API_BASE}/products/search`;
  const response = await fetchWithAuth(url);
  if (!response.ok) {
    const body = await parseResponseBody(response);
    throw new Error(errorMessageFromBody(body, response));
  }
  return response.json();
}

/**
 * Create a new product.
 * POST /api/products with a JSON body (CAT-US2, CAT-US8).
 *
 * ACCESS: ADMIN only.
 */
export async function createProduct(product) {
  const payload = {
    productCode: product.productCode,
    description: product.description,
    price: product.price,
    availabilityCount: product.availabilityCount,
  };
  // CAT-US8: only send minStockThreshold when provided (null = no threshold)
  if (product.minStockThreshold != null && product.minStockThreshold !== "") {
    payload.minStockThreshold = Number(product.minStockThreshold);
  }
  const response = await fetchWithAuth(`${API_BASE}/products`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Update an existing product (CAT-US4, CAT-US8).
 * PUT /api/products/{id} with description, price, availabilityCount, minStockThreshold.
 * Pass minStockThreshold=null to clear an existing threshold.
 *
 * ACCESS: ADMIN only.
 */
export async function updateProduct(id, product) {
  const payload = {
    description: product.description,
    price: product.price,
    availabilityCount: product.availabilityCount,
    // CAT-US8: null explicitly clears the threshold; number sets it.
    minStockThreshold:
      product.minStockThreshold != null && product.minStockThreshold !== ""
        ? Number(product.minStockThreshold)
        : null,
  };
  const response = await fetchWithAuth(`${API_BASE}/products/${id}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Record a stock delivery for a product (CAT-US7).
 * POST /api/products/{productId}/deliveries with deliveryDate, quantityReceived,
 * and optional supplierReference.
 * Returns a StockDeliveryResponse including the updated stock count.
 *
 * ACCESS: ADMIN only.
 */
export async function recordDelivery(productId, { deliveryDate, quantityReceived, supplierReference } = {}) {
  const payload = { deliveryDate, quantityReceived };
  if (supplierReference && supplierReference.trim()) {
    payload.supplierReference = supplierReference.trim();
  }
  const response = await fetchWithAuth(`${API_BASE}/products/${productId}/deliveries`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Delete a product (CAT-US3).
 * DELETE /api/products/{id}.
 *
 * ACCESS: ADMIN only.
 */
export async function deleteProduct(id) {
  const response = await fetchWithAuth(`${API_BASE}/products/${id}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    const body = await parseResponseBody(response);
    throw new Error(errorMessageFromBody(body, response));
  }
}

/* ── Reports (IPOS-SA-RPRT) ───────────────────────────────────────────────── */

/**
 * Fetch the low-stock report (CAT-US10).
 * GET /api/reports/low-stock → returns JSON array of LowStockProductDto objects.
 * Each row: { id, productCode, description, availabilityCount, minStockThreshold }.
 *
 * Real-time: the backend query runs on every call (no caching).
 *
 * ACCESS: MANAGER, ADMIN.
 */
export async function getLowStockReport() {
  const response = await fetchWithAuth(`${API_BASE}/reports/low-stock`);
  if (!response.ok) {
    const body = await parseResponseBody(response);
    throw new Error(errorMessageFromBody(body, response));
  }
  return response.json();
}

/**
 * Sales turnover for a date range (RPT-US1).
 * GET /api/reports/sales-turnover?start=YYYY-MM-DD&end=YYYY-MM-DD
 * → { totalQuantitySold, totalRevenue, currency }
 *
 * ACCESS: MANAGER, ADMIN.
 */
export async function getSalesTurnoverReport({ start, end }) {
  const params = new URLSearchParams({ start, end });
  const response = await fetchWithAuth(`${API_BASE}/reports/sales-turnover?${params}`);
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Global invoice monitoring for a date range (RPT-US4).
 * GET /api/reports/invoices?start=&end=
 * → { rows: [{ merchantId, merchantUsername, merchantName, invoiceId, invoiceNumber, issuedAt, amount, paymentStatus }] }
 *
 * ACCESS: MANAGER, ADMIN.
 */
export async function getGlobalInvoiceReport({ start, end }) {
  const params = new URLSearchParams({ start, end });
  const response = await fetchWithAuth(`${API_BASE}/reports/invoices?${params}`);
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Stock turnover: quantities sold vs received per product (RPT-US5).
 * GET /api/reports/stock-turnover?start=&end=
 * → { rows: [{ productId, productCode, quantitySold, quantityReceived }] }
 *
 * ACCESS: MANAGER, ADMIN.
 */
export async function getStockTurnoverReport({ start, end }) {
  const params = new URLSearchParams({ start, end });
  const response = await fetchWithAuth(`${API_BASE}/reports/stock-turnover?${params}`);
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Merchant order history for a date range (RPT-US2).
 * GET /api/reports/merchants/{merchantId}/order-history?start=&end=
 * → { rows: [...], periodTotalValue }
 *
 * ACCESS: MANAGER, ADMIN.
 */
export async function getMerchantOrderHistory(merchantId, { start, end }) {
  const params = new URLSearchParams({ start, end });
  const response = await fetchWithAuth(
    `${API_BASE}/reports/merchants/${merchantId}/order-history?${params}`
  );
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/**
 * Detailed merchant activity with line items (RPT-US3).
 * GET /api/reports/merchants/{merchantId}/activity?start=&end=
 * → { header, orders: [...] }
 *
 * ACCESS: MANAGER, ADMIN.
 */
export async function getMerchantActivityReport(merchantId, { start, end }) {
  const params = new URLSearchParams({ start, end });
  const response = await fetchWithAuth(
    `${API_BASE}/reports/merchants/${merchantId}/activity?${params}`
  );
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/* ── Catalogue (CAT-US1) ─────────────────────────────────────────────────── */

/**
 * GET /api/catalogue/status — whether the catalogue has been registered.
 * ACCESS: ADMIN only.
 */
export async function getCatalogueStatus() {
  const response = await fetchWithAuth(`${API_BASE}/catalogue/status`);
  if (!response.ok) {
    throw new Error("Failed to fetch catalogue status");
  }
  return response.json();
}

/**
 * POST /api/catalogue/initialize — register the catalogue once.
 * ACCESS: ADMIN only.
 */
export async function initializeCatalogue() {
  const response = await fetchWithAuth(`${API_BASE}/catalogue/initialize`, {
    method: "POST",
  });
  const body = await parseResponseBody(response);
  if (!response.ok) {
    throw new Error(errorMessageFromBody(body, response));
  }
  return body;
}

/* ── Users ────────────────────────────────────────────────────────────────── */

/**
 * Fetch ALL users.
 * GET /api/users → returns JSON array of UserResponse DTOs.
 *
 * ACCESS: ADMIN only (IPOS-SA-ACC).
 */
export async function getUsers() {
  const response = await fetchWithAuth(`${API_BASE}/users`);
  if (!response.ok) {
    throw new Error("Failed to fetch users");
  }
  return response.json();
}

/**
 * Create a new staff user (ADMIN or MANAGER only — NOT MERCHANT).
 * POST /api/users with JSON body.
 *
 * ACCESS: ADMIN only (IPOS-SA-ACC).
 *
 * NOTE: Merchant accounts must be created via createMerchantAccount() below,
 *       which includes mandatory profile fields (ACC-US1).
 */
export async function createUser(user) {
  const response = await fetchWithAuth(`${API_BASE}/users`, {
    method: "POST",
    body: JSON.stringify(user),
  });
  if (!response.ok) {
    throw new Error("Failed to create user");
  }
  return response.json();
}

/* ── Merchant Accounts (ACC-US1) ──────────────────────────────────────────── */

/**
 * Create a new Merchant Account (user + profile) atomically.
 * POST /api/merchant-accounts with JSON body containing all mandatory fields.
 *
 * ACCESS: ADMIN only.
 *
 * If any required field is missing, the backend returns 400 and no account
 * is created (brief: "the account will not be created").
 */
export async function createMerchantAccount(data) {
  const response = await fetchWithAuth(`${API_BASE}/merchant-accounts`, {
    method: "POST",
    body: JSON.stringify(data),
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || "Failed to create merchant account");
  }
  return result;
}

/* ── Merchant Profiles (ACC-US6, brief §iii) ──────────────────────────────── */

/**
 * Fetch all merchant profiles.
 * GET /api/merchant-profiles → returns JSON array of MerchantProfileResponse.
 *
 * ACCESS: MANAGER or ADMIN.
 */
export async function getMerchantProfiles() {
  const response = await fetchWithAuth(`${API_BASE}/merchant-profiles`);
  if (!response.ok) {
    throw new Error("Failed to fetch merchant profiles");
  }
  return response.json();
}

/**
 * Update a merchant's profile (credit limit, discount plan, standing).
 * PUT /api/merchant-profiles/{userId} with JSON body of changed fields.
 *
 * ACCESS: MANAGER or ADMIN.
 */
export async function updateMerchantProfile(userId, data) {
  const response = await fetchWithAuth(`${API_BASE}/merchant-profiles/${userId}`, {
    method: "PUT",
    body: JSON.stringify(data),
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || "Failed to update merchant profile");
  }
  return result;
}

/**
 * Trigger month-close flexible discount settlement.
 * POST /api/merchant-profiles/close-month with { yearMonth, settlementMode }.
 *
 * ACCESS: MANAGER or ADMIN.
 */
export async function closeMonth(yearMonth, settlementMode) {
  const response = await fetchWithAuth(`${API_BASE}/merchant-profiles/close-month`, {
    method: "POST",
    body: JSON.stringify({ yearMonth, settlementMode }),
  });
  const result = await response.json();
  if (!response.ok) {
    throw new Error(result.error || "Failed to close month");
  }
  return result;
}

/* ── Orders ───────────────────────────────────────────────────────────────── */

/**
 * Place a new order.
 * POST /api/orders with JSON body.
 *
 * ACCESS: All authenticated users (IPOS-SA-ORD).
 * ORD-US1: Merchants are forced to their own ID by the backend.
 */
export async function placeOrder(merchantId, items) {
  const response = await fetchWithAuth(`${API_BASE}/orders`, {
    method: "POST",
    body: JSON.stringify({ merchantId, items }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || "Failed to place order");
  }

  return data;
}

/**
 * Fetch orders (role-scoped by backend).
 * GET /api/orders → MERCHANT sees own orders; MANAGER/ADMIN see all (ORD-US2).
 *
 * ACCESS: All authenticated users (IPOS-SA-ORD).
 */
export async function getOrders() {
  const response = await fetchWithAuth(`${API_BASE}/orders`);
  if (!response.ok) {
    throw new Error("Failed to fetch orders");
  }
  return response.json();
}

/**
 * Update an order's status.
 * PUT /api/orders/{id}/status with JSON body { status }.
 *
 * ACCESS: MANAGER / ADMIN only (ORD-US2).
 */
export async function updateOrderStatus(orderId, status) {
  const response = await fetchWithAuth(`${API_BASE}/orders/${orderId}/status`, {
    method: "PUT",
    body: JSON.stringify({ status }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || "Failed to update order status");
  }

  return data;
}

/* ── Invoices (ORD-US5) ────────────────────────────────────────────────── */

/**
 * Fetch invoices (role-scoped by backend).
 * MERCHANT sees own invoices; MANAGER/ADMIN see all.
 */
export async function getInvoices() {
  const response = await fetchWithAuth(`${API_BASE}/invoices`);
  if (!response.ok) {
    throw new Error("Failed to fetch invoices");
  }
  return response.json();
}

/**
 * Fetch a single invoice by ID (with lines and payment details).
 */
export async function getInvoiceDetail(invoiceId) {
  const response = await fetchWithAuth(`${API_BASE}/invoices/${invoiceId}`);
  if (!response.ok) {
    throw new Error("Failed to fetch invoice detail");
  }
  return response.json();
}

/* ── Payments (ORD-US6) ────────────────────────────────────────────────── */

/**
 * Record a payment against an invoice.
 * POST /api/invoices/{id}/payments. ADMIN only.
 */
export async function recordPayment(invoiceId, amount, method) {
  const response = await fetchWithAuth(`${API_BASE}/invoices/${invoiceId}/payments`, {
    method: "POST",
    body: JSON.stringify({ amount, method }),
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(data.error || "Failed to record payment");
  }

  return data;
}

/* ── Merchant Financials (ORD-US3) ─────────────────────────────────────── */

/**
 * Fetch the logged-in merchant's outstanding balance.
 * GET /api/merchant-financials/balance. MERCHANT only.
 */
export async function getMerchantBalance() {
  const response = await fetchWithAuth(`${API_BASE}/merchant-financials/balance`);
  if (!response.ok) {
    throw new Error("Failed to fetch balance");
  }
  return response.json();
}
