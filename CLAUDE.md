# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

---

## Project Overview

**IPOS-SA** — a pharmaceutical stock and order management prototype for InfoPharma Ltd.

- **Frontend**: React 19 + Vite 6 (JS, no TypeScript), runs on `http://localhost:5173`
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
mvn test "-Dspring.profiles.active=test" "-Dtest=MerchantAccountServiceTest#testMethodName"  # single test method
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

### Backend Layers (`backend/src/main/java/com/ipos/`)

Standard Spring Boot layered architecture: **Controller → Service → Repository → Entity**. All business logic lives in services; controllers only parse HTTP and delegate.

**Key Services:**
- **`service/OrderService.java`** — Most complex service. Handles the entire order pipeline atomically: standing check → stock validation + decrement → price snapshot → discount calc (FIXED or FLEXIBLE credit) → credit limit enforcement → save.
- **`service/MerchantAccountService.java`** — Merchant creation (atomic User + MerchantProfile), flexible tier validation, month-close settlement with rebate calculation.
- **`service/UserService.java`** — Staff user CRUD. Explicitly rejects `role=MERCHANT` (must use MerchantAccountService).

**Key Security:**
- **`security/SecurityConfig.java`** — CSRF (`CookieCsrfTokenRepository`), CORS, session config, and all URL-level RBAC rules. **Primary source of truth for API authorization.**
- **`security/IposUserDetailsService.java`** — Loads users for Spring Security; maps role to `ROLE_` authority prefix.

**Key Config:**
- **`config/DataBootstrap.java`** — Seeds default users on startup when `ipos.bootstrap.enabled=true`.

**Entities & Relationships:**
- `User` — id, name, username, passwordHash, role (ADMIN/MANAGER/MERCHANT). Optional 1:1 with MerchantProfile.
- `MerchantProfile` — 1:1 with User. Holds contactEmail, contactPhone, addressLine, creditLimit, accountStatus (ACTIVE/INACTIVE), standing (NORMAL/IN_DEFAULT/SUSPENDED), discount plan (FIXED or FLEXIBLE), inDefaultSince, flexibleDiscountCredit, chequeRebatePending.
- `Product` — id, description, price, availabilityCount. No SKU/business ID yet.
- `Order` → M:1 User (merchant), 1:M OrderItem. Snapshots grossTotal, fixedDiscountAmount, flexibleCreditApplied, totalDue at placement.
- `OrderItem` → M:1 Order, M:1 Product. Snapshots unitPriceAtOrder.
- `MonthlyRebateSettlement` — M:1 User. Records month-close rebate calculations. Unique constraint on (merchant_id, settlement_year_month).
- `StandingChangeLog` — Audit log for every standing transition (who, when, from, to).

### API Endpoints

| Endpoint | Methods | Access |
|----------|---------|--------|
| `/api/auth/login` | POST | Public |
| `/api/auth/logout`, `/api/auth/me` | POST, GET | Authenticated |
| `/api/users` | GET, POST | ADMIN |
| `/api/merchant-accounts` | POST | ADMIN |
| `/api/merchant-profiles` | GET, GET/{userId}, PUT/{userId} | MANAGER, ADMIN |
| `/api/merchant-profiles/close-month` | POST | MANAGER, ADMIN |
| `/api/products` | GET | Authenticated |
| `/api/products` | POST | ADMIN |
| `/api/products` | PUT, DELETE | ADMIN (security rules exist, **controller methods not yet implemented**) |
| `/api/orders` | GET, POST | Authenticated |
| `/api/reports` | * | MANAGER, ADMIN (security rules exist, **no controller yet**) |

### Frontend Structure (`frontend/src/`)

No router library — uses simple `currentPage` state in App.jsx.

- **`auth/rbac.js`** — **Single source of truth for frontend RBAC.** Defines `ACCESS_MATRIX`, `ROUTE_PACKAGES`, and exported helper functions. All role checks must use this module.
- **`auth/AuthContext.jsx`** — Auth state, login/logout, session restoration (`GET /api/auth/me` on load), and `fetchWithAuth()` which automatically attaches the `X-XSRF-TOKEN` header and `credentials: "include"`.
- **`App.jsx`** — Builds navigation and renders pages dynamically based on `getAccessibleRoutes(role)` from `rbac.js`.
- **`api.js`** — All API calls in one place. Uses `fetchWithAuth` from `AuthContext`.

**Frontend RBAC Matrix:**

| Role | ACC | CAT | ORD | RPRT | MER |
|------|-----|-----|-----|------|-----|
| MERCHANT | ✗ | ✓ | ✓ | ✗ | ✗ |
| MANAGER | ✗ | ✓ | ✓ | ✓ | ✓ |
| ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ |

**Route → Component mapping** (in `App.jsx`):
- `catalogue` → `Catalogue.jsx` (product listing table)
- `order` → `OrderForm.jsx` (place orders with discount breakdown display)
- `reporting` → `ReportingPlaceholder.jsx` (stub)
- `accounts` → `MerchantCreate.jsx` (admin form for atomic merchant+profile creation)
- `merchants` → `MerchantManagement.jsx` (edit profiles, standing, month-close settlement)

---

## Key Design Decisions

### Authentication
Session-based (not JWT). JSESSIONID cookie + CSRF protection via `XSRF-TOKEN` cookie / `X-XSRF-TOKEN` header. The Vite proxy enables this without CORS complications.

### Discount Logic
- **FIXED**: Discount applied at order placement time — `totalDue = grossTotal − (grossTotal × percent/100)`.
- **FLEXIBLE**: No per-order discount. Month-close settlement computes the rebate from monthly spend tiers; stored as `flexibleDiscountCredit` on the profile, consumed on subsequent orders via `min(availableCredit, grossTotal)`.

### Flexible Tiers JSON Format
```json
[{"maxExclusive":1000,"percent":1}, {"maxExclusive":2000,"percent":2}, {"percent":3}]
```
Last tier omits `maxExclusive` (catch-all). Validated at account creation and profile update.

### Standing Rules
Merchants must be in `NORMAL` standing to place orders. Managers (and Admins) can transition `IN_DEFAULT → NORMAL` or `IN_DEFAULT → SUSPENDED` only — and only after 30 days (`inDefaultSince`). All transitions are audited in `standing_change_logs`.

### Merchant Account Creation
`POST /api/merchant-accounts` (ADMIN only) is the only valid way to create a MERCHANT user. `POST /api/users` explicitly rejects `role=MERCHANT`. The creation is fully atomic: no `User` row exists without an accompanying `MerchantProfile`.

### Credit Limit Enforcement
Outstanding exposure = sum of `totalDue` across all non-CANCELLED orders for a merchant. New orders are rejected if `existingExposure + newOrder.totalDue > creditLimit`.

### Merchant Isolation (ORD-US1)
If the caller is a MERCHANT, `OrderService.placeOrder()` forces `merchantId` to the caller's own user ID at the service layer — merchants cannot place orders for others.

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

Unit tests live at `backend/src/test/java/com/ipos/service/MerchantAccountServiceTest.java` (20 tests, JUnit 5 + Mockito). Test profile uses H2 in-memory DB with bootstrap disabled (`application-test.properties`). Tests cover: merchant account creation, tier validation, discount calculations, standing guards, credit limits, and merchant isolation.

There are no frontend tests, no `ProductController` tests, and no `WebMvc` integration tests yet.

---

## User Story Backlog (What's Left)

Detailed status in `ACCprogress.txt` (ACC — complete) and `CATprogress.txt` (CAT — largely incomplete). Key remaining gaps:

**CAT (Catalogue & Inventory):**
- **CAT-US2**: `POST /api/products` exists but has weak validation (no DTO, no unique SKU field).
- **CAT-US3/US4**: No `PUT`/`DELETE` on `ProductController` (security rules exist, controller methods don't).
- **CAT-US5/US6**: No product search endpoint; merchants see raw stock counts (should show Available/Out of Stock only).
- **CAT-US7**: No stock delivery recording (`StockDelivery` entity doesn't exist).
- **CAT-US8/US9**: No `minStockThreshold` field on `Product`; no low-stock warnings.
- **CAT-US10**: `ReportingPlaceholder.jsx` is a stub; no report controllers.

**ORD (Orders):**
- `GET /api/orders` returns all orders regardless of role — merchant-scoped listing not implemented.

---

## Default Bootstrap Credentials

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | ADMIN |
| `manager` | `manager123` | MANAGER |
| `merchant` | `merchant123` | MERCHANT |

Set `ipos.bootstrap.enabled=false` once real accounts exist.

---

## Reference Documentation

- `RBAC.md` — Full RBAC architecture and role/permission matrix
- `ACCprogress.txt` — ACC epic user story status (all 6 complete)
- `CATprogress.txt` — CAT epic user story status with implementation gaps and suggested order
