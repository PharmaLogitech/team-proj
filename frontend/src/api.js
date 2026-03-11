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
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add new functions for each endpoint (e.g., api.updateProduct()).   ║
 * ║        - Add an auth token header once authentication is implemented.       ║
 * ║        - Switch from fetch() to axios if the team prefers it.               ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

/*
 * ── HOW THE FRONTEND TALKS TO THE BACKEND ─────────────────────────────────────
 *
 * The browser's built-in fetch() function sends HTTP requests.
 *
 *   fetch(url, options)
 *     - url: The endpoint path.  We use relative paths like "/api/products"
 *            because Vite's proxy (configured in vite.config.js) forwards
 *            /api/* requests to http://localhost:8080 automatically.
 *     - options: An object with method, headers, body, etc.
 *
 *   fetch() returns a Promise.  We use async/await syntax to work with it:
 *     const response = await fetch("/api/products");
 *     const data = await response.json();  // Parse the JSON response body.
 *
 *   Promises and async/await:
 *     - A Promise represents a value that isn't available yet (the server
 *       hasn't responded).
 *     - "await" pauses execution until the Promise resolves (response arrives).
 *     - The function containing "await" must be marked "async".
 *
 *   Why we check response.ok:
 *     fetch() does NOT throw on HTTP errors (404, 500, etc.).
 *     It only throws on NETWORK errors (server unreachable).
 *     So we manually check response.ok (true if status is 200–299)
 *     and throw if it's false, so calling code can catch errors uniformly.
 */

const API_BASE = "/api";

/* ── Products ─────────────────────────────────────────────────────────────── */

/**
 * Fetch ALL products from the backend catalogue.
 * GET /api/products → returns JSON array of product objects.
 */
export async function getProducts() {
  const response = await fetch(`${API_BASE}/products`);
  if (!response.ok) {
    throw new Error("Failed to fetch products");
  }
  return response.json();
}

/**
 * Create a new product.
 * POST /api/products with a JSON body.
 */
export async function createProduct(product) {
  const response = await fetch(`${API_BASE}/products`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
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
 * GET /api/users → returns JSON array of user objects.
 */
export async function getUsers() {
  const response = await fetch(`${API_BASE}/users`);
  if (!response.ok) {
    throw new Error("Failed to fetch users");
  }
  return response.json();
}

/**
 * Create a new user.
 * POST /api/users with a JSON body like { "name": "Alice", "role": "ADMIN" }.
 */
export async function createUser(user) {
  const response = await fetch(`${API_BASE}/users`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
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
 * Note: If the backend rejects the order (e.g., out of stock), it returns
 * HTTP 400 with an error message.  We parse and throw that message.
 */
export async function placeOrder(merchantId, items) {
  const response = await fetch(`${API_BASE}/orders`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
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
 */
export async function getOrders() {
  const response = await fetch(`${API_BASE}/orders`);
  if (!response.ok) {
    throw new Error("Failed to fetch orders");
  }
  return response.json();
}
