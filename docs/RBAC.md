# RBAC Architecture — IPOS-SA

This document describes the Role-Based Access Control (RBAC) implementation in the IPOS-SA system, covering both the backend (Spring Security) and frontend (React). It maps directly to **ACC-US4 — Role-Based Access Control** from the user stories.

---

## Role x Package Access Matrix

This table is the authoritative reference for which roles can access which system packages. It is enforced in two places:

- **Backend**: `SecurityConfig.java` (URL-level rules)
- **Frontend**: `frontend/src/auth/rbac.js` (navigation guards + route guards)

| Package | Code | MERCHANT | MANAGER | ADMIN |
|---------|------|----------|---------|-------|
| Account Management | IPOS-SA-ACC | No | No | **Yes** |
| Catalogue & Inventory | IPOS-SA-CAT | **Yes** (read-only) | **Yes** (read-only) | **Yes** (full CRUD) |
| Orders & Fulfillment | IPOS-SA-ORD | **Yes** (own orders) | **Yes** (all orders) | **Yes** (all orders) |
| Reporting | IPOS-SA-RPRT | No | **Yes** | **Yes** |

### Notes

- **Merchants** can only browse the catalogue (read-only) and place/track their own orders.
- **Managers** have reporting access and can manage merchant account settings (ACC-US5, ACC-US6).
- **Admins** have unrestricted access to all packages.

---

## Authentication Flow

The system uses **session-based authentication** with Spring Security.

```
1. User enters username + password on Login screen
2. Frontend POSTs to /api/auth/login (credentials: "include")
3. Spring Security verifies password against BCrypt hash
4. On success: server creates HTTP session, sets JSESSIONID cookie
5. Browser sends JSESSIONID cookie on all subsequent requests
6. On page refresh: frontend calls GET /api/auth/me to restore session
7. On logout: POST /api/auth/logout invalidates the session
```

### Why Sessions (not JWT)?

- Simpler to implement for this project scope.
- Logout is instant (invalidate session vs. token blocklist).
- No token refresh logic needed.
- Vite proxy transparently forwards session cookies.

**Future option**: JWT can be added if the system needs stateless auth (e.g., for a mobile app). The `AuthController` would return a JWT instead of setting a session, and the frontend would store it in memory and send it as an `Authorization: Bearer <token>` header.

---

## CSRF Protection

CSRF (Cross-Site Request Forgery) protection prevents malicious websites from making requests on behalf of a logged-in user.

### How it works

1. Spring Security sets an `XSRF-TOKEN` cookie (readable by JavaScript).
2. For every state-changing request (POST, PUT, DELETE, PATCH), the frontend reads this cookie and sends the value as an `X-XSRF-TOKEN` header.
3. Spring Security compares the cookie and header values — if they match, the request is legitimate.

### Why this is safe

A malicious site can trigger the browser to send cookies automatically, but it **cannot read** our cookies (Same-Origin Policy). Only JavaScript running on our origin can read `XSRF-TOKEN` and set the header.

### Where it's configured

- **Backend**: `SecurityConfig.java` → `CookieCsrfTokenRepository.withHttpOnlyFalse()`
- **Frontend**: `auth/AuthContext.jsx` → `getCsrfToken()` + `fetchWithAuth()`

---

## Backend Security Configuration

### URL-Level Rules (SecurityConfig.java)

| Endpoint | Method | Required Role | Notes |
|----------|--------|---------------|-------|
| `/api/auth/login` | POST | Public | No session exists yet |
| `/api/auth/logout` | POST | Authenticated | Any role |
| `/api/auth/me` | GET | Authenticated | Session restoration |
| `/api/users/**` | ALL | ADMIN | Account management (IPOS-SA-ACC) |
| `/api/products/**` | GET | Authenticated | Catalogue browsing (all roles) |
| `/api/products/**` | POST/PUT/DELETE | ADMIN | Catalogue management |
| `/api/orders/**` | ALL | Authenticated | Order operations |
| `/api/reports/**` | ALL | MANAGER or ADMIN | Reporting (IPOS-SA-RPRT) |

### Password Storage

- Passwords are hashed using **BCrypt** (Spring Security's `BCryptPasswordEncoder`).
- BCrypt automatically salts each hash (no separate salt column needed).
- The `passwordHash` field is `@JsonIgnore`d and never appears in API responses.
- The `UserResponse` DTO omits the field entirely (belt-and-suspenders).

---

## Frontend RBAC Configuration

### Files

| File | Purpose |
|------|---------|
| `frontend/src/auth/rbac.js` | Access matrix, package constants, role-check functions |
| `frontend/src/auth/AuthContext.jsx` | Auth state, login/logout, session restoration, CSRF helper |
| `frontend/src/App.jsx` | Navigation guards (hides/shows nav items per role) |

### How to add a new screen

1. Create the page component (e.g., `NewFeature.jsx`).
2. Add a route entry in `ROUTE_PACKAGES` inside `rbac.js` mapping the route key to a package.
3. Add the route key to `NAV_LABELS` and `PAGE_COMPONENTS` in `App.jsx`.
4. Import the component in `App.jsx`.
5. The nav and route guards will pick it up automatically.

### How to add a new role

1. Add the role to `User.Role` enum in `backend/.../entity/User.java`.
2. Add a new entry in `ACCESS_MATRIX` inside `rbac.js`.
3. Update `SecurityConfig.java` with URL-level rules for the new role.
4. Update this document.

---

## Default Bootstrap Users

When `ipos.bootstrap.enabled=true` in `application.properties`, the following users are created on first startup if the users table is empty:

| Username | Password | Role | Purpose |
|----------|----------|------|---------|
| `admin` | `admin123` | ADMIN | Full system access |
| `manager` | `manager123` | MANAGER | Reporting + merchant management |
| `merchant` | `merchant123` | MERCHANT | Catalogue browsing + orders |

Set `ipos.bootstrap.enabled=false` in production.

---

## Future Work Checklist

The following user stories depend on the RBAC foundation and will extend it:

### Account Management (IPOS-SA-ACC)
- [ ] ACC-US1: Merchant Account Creation (contact details, credit limit, discount plan, Active/Inactive status)
- [ ] ACC-US2: Fixed Discount Plan Assignment
- [ ] ACC-US3: Flexible Discount Plan Configuration
- [ ] ACC-US5: Managing Defaulted Accounts (Manager-only restore, audit log)
- [ ] ACC-US6: Managing Accounts (Manager edits credit limits and discount plans)

### Catalogue & Inventory (IPOS-SA-CAT)
- [ ] CAT-US3: Product Discontinuation/Removal (delete endpoint + confirmation + audit log)
- [ ] CAT-US4: Product Data Maintenance (update endpoint + validation)
- [ ] CAT-US5: Internal Product Search (search by ID, description, price range)
- [ ] CAT-US6: Merchant Catalogue Browsing (hide stock levels, partial-word search)
- [ ] CAT-US7: Stock Delivery Recording
- [ ] CAT-US8: Minimum Stock Thresholds
- [ ] CAT-US9: Automated Low-Stock Warnings
- [ ] CAT-US10: Low-Stock Reporting

### Orders & Fulfillment (IPOS-SA-ORD)
- [ ] ORD-US1: Restrict merchants to their own orders
- [ ] ORD-US2: Real-time Order Tracking
- [ ] ORD-US3: Financial Balance Oversight
- [ ] ORD-US4: Stock reduction on "Accepted" status (not on placement)
- [ ] ORD-US5: Automated Invoice Generation
- [ ] ORD-US6: Recording Merchant Payments

### Reporting (IPOS-SA-RPRT)
- [ ] RPT-US1: Sales Turnover Reporting
- [ ] RPT-US2: Merchant History Tracking
- [ ] RPT-US3: Individual Merchant Activity Report
- [ ] RPT-US4: Global Invoice Monitoring
- [ ] RPT-US5: Stock Turnover Analysis
