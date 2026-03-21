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
 * ║        - Switch from fetch() to axios if the team prefers it.               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

/*
 * ── HOW THE FRONTEND TALKS TO THE BACKEND ─────────────────────────────────────
 *
 * fetchWithAuth() wraps the browser's built-in fetch() and adds:
 *   1. credentials: "include" — sends cookies (JSESSIONID, XSRF-TOKEN).
 *   2. X-XSRF-TOKEN header — for CSRF protection on mutating requests.
 *   3. Content-Type: application/json — for requests with a JSON body.
 *
 * We use relative paths like "/api/products" because Vite's proxy
 * (configured in vite.config.js) forwards /api/* requests to
 * http://localhost:8080 automatically.
 *
 * fetch() returns a Promise.  We use async/await syntax to work with it:
 *   const response = await fetchWithAuth("/api/products");
 *   const data = await response.json();
 *
 * Why we check response.ok:
 *   fetch() does NOT throw on HTTP errors (404, 500, etc.).
 *   It only throws on NETWORK errors (server unreachable).
 *   So we manually check response.ok (true if status is 200–299)
 *   and throw if it's false, so calling code can catch errors uniformly.
 */
import { fetchWithAuth } from "./auth/AuthContext.jsx";

const API_BASE = "/api";

/* ── Products ─────────────────────────────────────────────────────────────── */

/**
 * Fetch ALL products from the backend catalogue.
 * GET /api/products → returns JSON array of product objects.
 *
 * ACCESS: All authenticated users (MERCHANT, MANAGER, ADMIN).
 * Merchants see this as read-only catalogue browsing (CAT-US6).
 */
export async function getProducts() {
  const response = await fetchWithAuth(`${API_BASE}/products`);
  if (!response.ok) {
    throw new Error("Failed to fetch products");
  }
  return response.json();
}

/**
 * Create a new product.
 * POST /api/products with a JSON body.
 *
 * ACCESS: ADMIN only (CAT-US2 — Product Creation).
 * Non-admin users will receive 403 Forbidden from the backend.
 */
export async function createProduct(product) {
  const response = await fetchWithAuth(`${API_BASE}/products`, {
    method: "POST",
    body: JSON.stringify(product),
  });
  if (!response.ok) {
    throw new Error("Failed to create product");
  }
  return response.json();
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
 * Create a new user.
 * POST /api/users with JSON body:
 *   { "name": "Alice", "username": "alice", "password": "pass123", "role": "MERCHANT" }
 *
 * ACCESS: ADMIN only (IPOS-SA-ACC).
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

/* ── Orders ───────────────────────────────────────────────────────────────── */

/**
 * Place a new order.
 * POST /api/orders with JSON body:
 *   { "merchantId": 1, "items": [{ "productId": 10, "quantity": 3 }] }
 *
 * ACCESS: All authenticated users (IPOS-SA-ORD).
 * FUTURE: ORD-US1 will restrict merchants to their own orders.
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
 * Fetch ALL orders.
 * GET /api/orders → returns JSON array of order objects.
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
