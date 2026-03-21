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
