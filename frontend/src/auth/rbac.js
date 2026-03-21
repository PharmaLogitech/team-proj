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
 * ║        └─────────────┴──────────────────────────────────────────────┘        ║
 * ║                                                                              ║
 * ║  ROLE × PACKAGE MATRIX (ACC-US4 acceptance criteria):                        ║
 * ║        ┌───────────┬─────┬─────┬─────┬──────┐                               ║
 * ║        │ Role      │ ACC │ CAT │ ORD │ RPRT │                               ║
 * ║        ├───────────┼─────┼─────┼─────┼──────┤                               ║
 * ║        │ MERCHANT  │  ✗  │  ✓  │  ✓  │  ✗   │                               ║
 * ║        │ MANAGER   │  ✗  │  ✓  │  ✓  │  ✓   │                               ║
 * ║        │ ADMIN     │  ✓  │  ✓  │  ✓  │  ✓   │                               ║
 * ║        └───────────┴─────┴─────┴─────┴──────┘                               ║
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
 * ║        4. Update docs/RBAC.md with the new role's permissions.              ║
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
export const PACKAGE_ACC = "IPOS-SA-ACC";   // Account Management
export const PACKAGE_CAT = "IPOS-SA-CAT";   // Catalogue & Inventory
export const PACKAGE_ORD = "IPOS-SA-ORD";   // Orders & Fulfillment
export const PACKAGE_RPRT = "IPOS-SA-RPRT"; // Reporting

/* ── Access Matrix ────────────────────────────────────────────────────────────
 *
 * Maps each role to the set of packages they can access.
 * This is the JavaScript equivalent of the ACC-US4 acceptance criteria.
 *
 * Using a Set for O(1) lookups.  In a system with many packages, this is
 * more efficient than array.includes(), though for 4 packages it doesn't
 * matter — we use Set for correctness of the pattern.
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
   * MANAGER (ACC-US4):
   * "Manager accounts must have access to IPOS-SA-RPRT and Merchant account
   *  settings."
   *
   * Managers also get CAT and ORD for oversight (they need to see catalogue
   * and orders to manage merchants effectively, per ACC-US5/US6).
   */
  MANAGER: new Set([PACKAGE_CAT, PACKAGE_ORD, PACKAGE_RPRT]),

  /*
   * ADMIN (ACC-US4):
   * "Administrator accounts must have full access to all packages, including
   *  IPOS-SA-ACC."
   */
  ADMIN: new Set([PACKAGE_ACC, PACKAGE_CAT, PACKAGE_ORD, PACKAGE_RPRT]),
};

/* ── Route → Package Mapping ──────────────────────────────────────────────────
 *
 * Maps each frontend route/page identifier to the package it belongs to.
 * App.jsx uses this to determine whether to show a nav item and whether
 * to render the page component.
 *
 * The keys here are the "page" identifiers used by App.jsx's currentPage
 * state.  If you switch to React Router in the future, these would become
 * URL paths like "/catalogue", "/orders", etc.
 */
const ROUTE_PACKAGES = {
  catalogue: PACKAGE_CAT,
  order: PACKAGE_ORD,
  reporting: PACKAGE_RPRT,
  accounts: PACKAGE_ACC,
};

/*
 * ── roleCanAccessPackage ─────────────────────────────────────────────────────
 *
 * Checks whether a given role has access to a given package.
 *
 * @param {string} role       The user's role (e.g., "ADMIN", "MERCHANT").
 * @param {string} packageId  The package constant (e.g., PACKAGE_CAT).
 * @returns {boolean}         true if the role can access the package.
 *
 * FAIL-CLOSED: If the role is not in the ACCESS_MATRIX (e.g., a typo or
 * a new role that hasn't been configured), this returns false.
 * This is intentional — unknown roles should have NO access.
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
 * This is a convenience wrapper that maps route → package → role check.
 *
 * @param {string} role    The user's role.
 * @param {string} route   The page identifier (e.g., "catalogue", "order").
 * @returns {boolean}      true if the role can access the route.
 *
 * If the route is not in ROUTE_PACKAGES (e.g., a new page was added but
 * not mapped), this returns false (fail-closed).
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
 * @param {string} role  The user's role.
 * @returns {string[]}   Array of route identifiers the role can access.
 *
 * Example:
 *   getAccessibleRoutes("MERCHANT")  → ["catalogue", "order"]
 *   getAccessibleRoutes("ADMIN")     → ["catalogue", "order", "reporting", "accounts"]
 */
export function getAccessibleRoutes(role) {
  return Object.keys(ROUTE_PACKAGES).filter(
    (route) => roleCanAccessRoute(role, route)
  );
}
