/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Role-Based Access Control (RBAC) configuration for the frontend.     ║
 * ║                                                                              ║
 * ║  WHY:  The IPOS-SA system has three user roles (ACC-US4) with different     ║
 * ║        levels of access.  This module is the SINGLE SOURCE OF TRUTH for     ║
 * ║        which roles can access which parts of the application.               ║
 * ║                                                                              ║
 * ║        Instead of scattering role checks across components (which leads to  ║
 * ║        inconsistencies and security gaps), every component that needs to    ║
 * ║        check permissions imports from THIS file.                            ║
 * ║                                                                              ║
 * ║  PACKAGES (from the IPOS-SA specification):                                  ║
 * ║        ┌─────────────┬──────────────────────────────────────────────┐        ║
 * ║        │ Package ID  │ Description                                 │        ║
 * ║        ├─────────────┼──────────────────────────────────────────────┤        ║
 * ║        │ IPOS-SA-ACC │ Account Management (users, roles, settings) │        ║
 * ║        │ IPOS-SA-CAT │ Electronic Catalogue & Inventory Control    │        ║
 * ║        │ IPOS-SA-ORD │ Order Fulfillment & Financial Tracking      │        ║
 * ║        │ IPOS-SA-RPRT│ Operational & Financial Reporting           │        ║
 * ║        │ IPOS-SA-MER │ Merchant Profile Management (ACC-US6)      │        ║
 * ║        └─────────────┴──────────────────────────────────────────────┘        ║
 * ║                                                                              ║
 * ║  ROLE × PACKAGE MATRIX (ACC-US4 + brief §iii):                               ║
 * ║        ┌───────────┬─────┬─────┬─────┬──────┬─────┐                         ║
 * ║        │ Role      │ ACC │ CAT │ ORD │ RPRT │ MER │                         ║
 * ║        ├───────────┼─────┼─────┼─────┼──────┼─────┤                         ║
 * ║        │ MERCHANT  │  ✗  │  ✓  │  ✓  │  ✗   │  ✗  │                         ║
 * ║        │ MANAGER   │  ✗  │  ✓  │  ✓  │  ✓   │  ✓  │                         ║
 * ║        │ ADMIN     │  ✓  │  ✓  │  ✓  │  ✓   │  ✓  │                         ║
 * ║        └───────────┴─────┴─────┴─────┴──────┴─────┘                         ║
 * ║                                                                              ║
 * ║  HOW TO ADD A NEW SCREEN (3 steps):                                          ║
 * ║        1. Add a new PACKAGE constant below (if it's a new package).         ║
 * ║        2. Add the package to the ACCESS_MATRIX for each role that should    ║
 * ║           have access.                                                      ║
 * ║        3. Add a route entry in ROUTE_PACKAGES mapping the URL path to       ║
 * ║           the package.                                                      ║
 * ║        That's it — the nav and route guards in App.jsx will pick it up      ║
 * ║        automatically.                                                       ║
 * ║                                                                              ║
 * ║  HOW TO ADD A NEW ROLE:                                                      ║
 * ║        1. Add the role to User.Role enum in the backend (User.java).        ║
 * ║        2. Add a new entry in ACCESS_MATRIX below with the allowed packages. ║
 * ║        3. Update SecurityConfig.java on the backend with URL-level rules.   ║
 * ║        4. Update RBAC.md (project root) with the new role's permissions.   ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */

/* ── Package Constants ────────────────────────────────────────────────────────
 *
 * These string constants identify each logical package in the IPOS-SA system.
 * We use constants (not magic strings) so that:
 *   1. Typos are caught by editors/linters (no silent failures).
 *   2. Renaming a package means changing ONE place, not every component.
 *   3. Imports make dependencies explicit and searchable.
 */
export const PACKAGE_ACC = "IPOS-SA-ACC";   // Account Management (admin-only)
export const PACKAGE_CAT = "IPOS-SA-CAT";   // Catalogue & Inventory
export const PACKAGE_ORD = "IPOS-SA-ORD";   // Orders & Fulfillment
export const PACKAGE_RPRT = "IPOS-SA-RPRT"; // Reporting

/*
 * PACKAGE_MER — Merchant Profile Management (brief §iii).
 * Separate from PACKAGE_ACC because MANAGERS need access to merchant
 * profiles (to alter credit limits, discount plans, and standing) but
 * should NOT see the full Account Management screen (user CRUD).
 */
export const PACKAGE_MER = "IPOS-SA-MER";   // Merchant Profiles (manager + admin)

/* ── Access Matrix ────────────────────────────────────────────────────────────
 *
 * Maps each role to the set of packages they can access.
 * This is the JavaScript equivalent of the ACC-US4 acceptance criteria
 * extended with the brief §iii manager capabilities.
 *
 * Using a Set for O(1) lookups.
 *
 * NOTE: If a role is not in this map, they have NO access to anything.
 *       This is a fail-closed design (deny by default).
 */
const ACCESS_MATRIX = {
  /*
   * MERCHANT (ACC-US4):
   * "Merchant accounts must only have access to IPOS-SA-CAT and IPOS-SA-ORD."
   */
  MERCHANT: new Set([PACKAGE_CAT, PACKAGE_ORD]),

  /*
   * MANAGER (ACC-US4 + brief §iii):
   * Reporting access + merchant account management (credit limits,
   * discount plans, standing transitions).
   * Also gets CAT and ORD for oversight.
   */
  MANAGER: new Set([PACKAGE_CAT, PACKAGE_ORD, PACKAGE_RPRT, PACKAGE_MER]),

  /*
   * ADMIN (ACC-US4):
   * Full access to all packages, including account management and
   * merchant profile management.
   */
  ADMIN: new Set([PACKAGE_ACC, PACKAGE_CAT, PACKAGE_ORD, PACKAGE_RPRT, PACKAGE_MER]),
};

/* ── Route → Package Mapping ──────────────────────────────────────────────────
 *
 * Maps each frontend route/page identifier to the package it belongs to.
 * App.jsx uses this to determine whether to show a nav item and whether
 * to render the page component.
 */
const ROUTE_PACKAGES = {
  catalogue: PACKAGE_CAT,
  order: PACKAGE_ORD,
  reporting: PACKAGE_RPRT,
  accounts: PACKAGE_ACC,
  merchants: PACKAGE_MER,
};

/*
 * ── roleCanAccessPackage ─────────────────────────────────────────────────────
 *
 * Checks whether a given role has access to a given package.
 *
 * FAIL-CLOSED: If the role is not in the ACCESS_MATRIX, returns false.
 */
export function roleCanAccessPackage(role, packageId) {
  const allowedPackages = ACCESS_MATRIX[role];
  if (!allowedPackages) return false;
  return allowedPackages.has(packageId);
}

/*
 * ── roleCanAccessRoute ───────────────────────────────────────────────────────
 *
 * Checks whether a given role can access a given frontend route/page.
 * Convenience wrapper: route → package → role check.
 */
export function roleCanAccessRoute(role, route) {
  const packageId = ROUTE_PACKAGES[route];
  if (!packageId) return false;
  return roleCanAccessPackage(role, packageId);
}

/*
 * ── getAccessibleRoutes ──────────────────────────────────────────────────────
 *
 * Returns all route identifiers that a given role can access.
 * Used by App.jsx to build the navigation menu dynamically.
 *
 * Example:
 *   getAccessibleRoutes("MERCHANT")  → ["catalogue", "order"]
 *   getAccessibleRoutes("MANAGER")   → ["catalogue", "order", "reporting", "merchants"]
 *   getAccessibleRoutes("ADMIN")     → ["catalogue", "order", "reporting", "accounts", "merchants"]
 */
export function getAccessibleRoutes(role) {
  return Object.keys(ROUTE_PACKAGES).filter(
    (route) => roleCanAccessRoute(role, route)
  );
}
