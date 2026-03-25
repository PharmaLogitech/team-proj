# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**IPOS-SA** — a pharmaceutical stock and order management prototype for InfoPharma Ltd.

- **Frontend**: React + Vite (JS), runs on `http://localhost:5173`
- **Backend**: Spring Boot 3.4.x, Java 17, runs on `http://localhost:8080`
- **Database**: MySQL 8+ (`ipos_sa` schema); Hibernate `ddl-auto=update` auto-manages schema

Vite proxies `/api/*` → `http://localhost:8080`, so session cookies work as same-origin.

---

## Commands

### Backend
```bash
cd backend
mvn spring-boot:run                                    # start dev server
mvn test "-Dspring.profiles.active=test"               # run all tests (H2 in-memory)
mvn test "-Dspring.profiles.active=test" -Dtest=MerchantAccountServiceTest  # single test class
```

### Frontend
```bash
cd frontend
npm install       # first time only
npm run dev       # start dev server
npm run build     # production build
```

### Database
```sql
CREATE DATABASE ipos_sa;  -- once, before first backend start
```

Configure `backend/src/main/resources/application.properties` with your MySQL credentials before starting.

---

## Architecture

### System Packages (from brief)

| Package | Code | Status |
|---------|------|--------|
| Account Management | IPOS-SA-ACC | ✅ Complete |
| Catalogue & Inventory | IPOS-SA-CAT | ⚠️ Partial |
| Orders & Fulfillment | IPOS-SA-ORD | ⚠️ Partial |
| Merchant Profiles | IPOS-SA-MER | ✅ Complete |
| Reporting | IPOS-SA-RPRT | ❌ Stub only |

### Backend Structure (`backend/src/main/java/com/ipos/`)

- **`security/SecurityConfig.java`** — CSRF (`CookieCsrfTokenRepository`), CORS, session config, and all URL-level RBAC rules. **Primary source of truth for API authorization.**
- **`service/OrderService.java`** — The most complex service. Handles the entire order pipeline atomically: standing check → stock validation + decrement → price snapshot → discount calc (FIXED or FLEXIBLE credit) → credit limit enforcement → save.
- **`service/MerchantAccountService.java`** — Merchant creation (atomic User + MerchantProfile), flexible month-close settlement, standing transitions.
- **`config/DataBootstrap.java`** — Seeds default users on startup when `ipos.bootstrap.enabled=true`.
- **`entity/MerchantProfile.java`** — Core merchant state: `accountStatus` (INACTIVE/ACTIVE), `standing` (NORMAL/IN_DEFAULT/SUSPENDED), `inDefaultSince`, `flexibleTiersJson` (JSON), `flexibleDiscountCredit`, `chequeRebatePending`.
- **`entity/Order.java`** + **`entity/OrderItem.java`** — Orders snapshot `unitPriceAtOrder` at placement time. `totalDue` reflects applied discounts.
- **`entity/StandingChangeLog.java`** — Audit log for every standing transition (ACC-US5).

### Frontend Structure (`frontend/src/`)

- **`auth/rbac.js`** — **Single source of truth for frontend RBAC.** Defines `ACCESS_MATRIX`, `ROUTE_PACKAGES`, and exported helper functions. All role checks must use this module.
- **`auth/AuthContext.jsx`** — Auth state, login/logout, session restoration (`GET /api/auth/me` on load), and `fetchWithAuth()` which automatically attaches the `X-XSRF-TOKEN` header.
- **`App.jsx`** — Builds navigation and renders pages dynamically based on `getAccessibleRoutes(role)` from `rbac.js`.
- **`api.js`** — All API calls in one place. Uses `fetchWithAuth` from `AuthContext`.

---

## Key Design Decisions

### Authentication
Session-based (not JWT). JSESSIONID cookie + CSRF protection via `XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header. The Vite proxy enables this without CORS complications.

### Discount Logic
- **FIXED**: Discount applied at order placement time — `totalDue = grossTotal − (grossTotal × percent/100)`.
- **FLEXIBLE**: No per-order discount. Month-close settlement computes the rebate from monthly spend; stored as `flexibleDiscountCredit` on the profile, consumed on subsequent orders.

### Standing Rules
Merchants must be in `NORMAL` standing to place orders. Managers (and Admins) can transition `IN_DEFAULT → NORMAL` or `IN_DEFAULT → SUSPENDED` only — and only after 30 days (`inDefaultSince`). All transitions are audited in `standing_change_logs`.

### Merchant Account Creation
`POST /api/merchant-accounts` (ADMIN only) is the only valid way to create a MERCHANT user. `POST /api/users` explicitly rejects `role=MERCHANT`. The creation is fully atomic: no `User` row exists without an accompanying `MerchantProfile`.

### Adding a New Screen
1. Create `NewFeature.jsx` in `frontend/src/`.
2. Add a package constant (if new) and route entry in `auth/rbac.js`.
3. Add the route key to `NAV_LABELS` and `PAGE_COMPONENTS` in `App.jsx` and import the component.

### Adding a New Role
1. Add to `User.Role` enum in `backend/.../entity/User.java`.
2. Add entry to `ACCESS_MATRIX` in `auth/rbac.js`.
3. Add URL rules to `SecurityConfig.java`.
4. Update `RBAC.md`.

---

## Tests

Unit tests live at `backend/src/test/java/com/ipos/service/MerchantAccountServiceTest.java` (20 tests, JUnit 5 + Mockito). Test profile uses H2 in-memory DB with bootstrap disabled (`application-test.properties`). There are no `ProductController` or `WebMvc` integration tests yet.

---

## User Story Backlog (What's Left)

Detailed status in `ACCprogress.txt` (ACC — complete) and `CATprogress.txt` (CAT — largely incomplete). Key remaining CAT gaps:

- **CAT-US3/US4**: No `PUT`/`DELETE` on `ProductController` (security rules exist, controller methods don't).
- **CAT-US5/US6**: No product search endpoint; merchants see raw stock counts (should show Available/Out of Stock only).
- **CAT-US7**: No stock delivery recording (`StockDelivery` entity doesn't exist).
- **CAT-US8/US9**: No `minStockThreshold` field on `Product`; no low-stock warnings.
- **CAT-US10**: `ReportingPlaceholder.jsx` is a stub; no report controllers.

ORD gap: `GET /api/orders` returns all orders regardless of role — merchant-scoped listing not implemented.

---

## Default Bootstrap Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | ADMIN |
| `manager` | `manager123` | MANAGER |
| `merchant` | `merchant123` | MERCHANT |

Set `ipos.bootstrap.enabled=false` once real accounts exist.
