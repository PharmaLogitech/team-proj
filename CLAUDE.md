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
mvn test "-Dspring.profiles.active=test" -Dtest=CatalogueCatTest           # single test class
mvn test "-Dspring.profiles.active=test" -Dtest=MerchantAccountServiceTest  # single test class
mvn test "-Dspring.profiles.active=test" "-Dtest=CatalogueCatTest#testMethodName"  # single test method
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
| Catalogue & Inventory | IPOS-SA-CAT | ⚠️ Mostly Complete (US1 partial; US9/10 remain) |
| Orders & Fulfillment | IPOS-SA-ORD | ⚠️ Partial |
| Merchant Profiles | IPOS-SA-MER | ✅ Complete |
| Reporting | IPOS-SA-RPRT | ❌ Stub only |

### Backend Layers (`backend/src/main/java/com/ipos/`)

Standard Spring Boot layered architecture: **Controller → Service → Repository → Entity**. All business logic lives in services; controllers only parse HTTP and delegate.

**Key Services:**
- **`service/ProductService.java`** — Full catalogue CRUD: create (CAT-US2), update (CAT-US4), delete with audit log (CAT-US3), role-aware list (CAT-US6), multi-criteria search (CAT-US5), stock delivery recording with atomic availability increment (CAT-US7), min-stock threshold support (CAT-US8).
- **`service/OrderService.java`** — Most complex service. Handles the entire order pipeline atomically: standing check → stock validation + decrement → price snapshot → discount calc (FIXED or FLEXIBLE credit) → credit limit enforcement → save.
- **`service/MerchantAccountService.java`** — Merchant creation (atomic User + MerchantProfile), flexible tier validation, month-close settlement with rebate calculation.
- **`service/UserService.java`** — Staff user CRUD. Explicitly rejects `role=MERCHANT` (must use MerchantAccountService).
- **`service/CatalogueService.java`** — Catalogue initialization guard (CAT-US1): ensures the catalogue is registered only once via `CatalogueMetadata` singleton row.

**Key Security:**
- **`security/SecurityConfig.java`** — CSRF (`CookieCsrfTokenRepository`), CORS, session config, URL-level RBAC rules, and `@EnableMethodSecurity` for `@PreAuthorize` support. **Primary source of truth for API authorization.**
- **`security/IposUserDetailsService.java`** — Loads users for Spring Security; maps role to `ROLE_` authority prefix.

**Key Config:**
- **`config/DataBootstrap.java`** — Seeds default users on startup when `ipos.bootstrap.enabled=true`.
- **`config/CatalogueLifecycleRunner.java`** — Runs on startup; ensures catalogue metadata is initialized and backfills `productCode` from description if null on legacy rows.

**Entities & Relationships:**
- `User` — id, name, username, passwordHash, role (ADMIN/MANAGER/MERCHANT). Optional 1:1 with MerchantProfile.
- `MerchantProfile` — 1:1 with User. Holds contactEmail, contactPhone, addressLine, creditLimit, accountStatus (ACTIVE/INACTIVE), standing (NORMAL/IN_DEFAULT/SUSPENDED), discount plan (FIXED or FLEXIBLE), inDefaultSince, flexibleDiscountCredit, chequeRebatePending.
- `Product` — id, productCode (unique business SKU), description, price (BigDecimal), availabilityCount, minStockThreshold (nullable Integer — CAT-US8, null = no threshold configured).
- `StockDelivery` — id, product (ManyToOne), deliveryDate (LocalDate), quantityReceived, supplierReference (nullable, max 255), recordedBy (ManyToOne User), recordedAt (Instant). Table: `stock_deliveries`. CAT-US7 audit trail.
- `CatalogueMetadata` — singleton row (id=1) recording when the catalogue was initialized (CAT-US1).
- `Order` → M:1 User (merchant), 1:M OrderItem. Snapshots grossTotal, fixedDiscountAmount, flexibleCreditApplied, totalDue at placement.
- `OrderItem` → M:1 Order, M:1 Product. Snapshots unitPriceAtOrder.
- `MonthlyRebateSettlement` — M:1 User. Records month-close rebate calculations. Unique constraint on (merchant_id, settlement_year_month).
- `ProductDeletionLog` — Audit trail for product deletions (CAT-US3): snapshots productId, productCode, description, deletedBy (User), deletedAt.
- `StandingChangeLog` — Audit log for every standing transition (who, when, from, to). CAT-US5 / ACC-US5.

### API Endpoints

| Endpoint | Methods | Access |
|----------|---------|--------|
| `/api/auth/login` | POST | Public |
| `/api/auth/logout` | POST | Authenticated |
| `/api/auth/me` | GET | Authenticated |
| `/api/users` | GET, POST | ADMIN |
| `/api/merchant-accounts` | POST | ADMIN |
| `/api/merchant-profiles` | GET | MANAGER, ADMIN |
| `/api/merchant-profiles/{userId}` | GET, PUT | MANAGER, ADMIN |
| `/api/merchant-profiles/close-month` | POST | MANAGER, ADMIN |
| `/api/catalogue/initialize` | POST | ADMIN |
| `/api/catalogue/status` | GET | ADMIN |
| `/api/products` | GET | Authenticated |
| `/api/products` | POST | ADMIN |
| `/api/products/search` | GET | Authenticated (CAT-US5/US6) |
| `/api/products/{id}` | PUT, DELETE | ADMIN |
| `/api/products/{id}/deliveries` | POST | ADMIN (CAT-US7, also `@PreAuthorize`) |
| `/api/orders` | GET, POST | Authenticated |
| `/api/reports/**` | * | MANAGER, ADMIN (security rule exists, **no controller yet**) |

### Frontend Structure (`frontend/src/`)

No router library — uses simple `currentPage` state in App.jsx.

- **`auth/rbac.js`** — **Single source of truth for frontend RBAC.** Defines `ACCESS_MATRIX`, `ROUTE_PACKAGES`, and exported helper functions (`roleCanAccessRoute`, `roleCanAccessPackage`, `getAccessibleRoutes`). All role checks must use this module.
- **`auth/AuthContext.jsx`** — Auth state, login/logout, session restoration (`GET /api/auth/me` on load), and `fetchWithAuth()` which automatically attaches the `X-XSRF-TOKEN` header and `credentials: "include"`.
- **`App.jsx`** — Builds navigation and renders pages dynamically based on `getAccessibleRoutes(role)` from `rbac.js`.
- **`api.js`** — All API calls in one place. Uses `fetchWithAuth` from `AuthContext`. See API Service section below.

**Frontend RBAC Matrix:**

| Role | ACC | CAT | ORD | RPRT | MER |
|------|-----|-----|-----|------|-----|
| MERCHANT | ✗ | ✓ | ✓ | ✗ | ✗ |
| MANAGER | ✗ | ✓ | ✓ | ✓ | ✓ |
| ADMIN | ✓ | ✓ | ✓ | ✓ | ✓ |

**Route → Component mapping** (in `App.jsx`):
- `catalogue` → `Catalogue.jsx` — product listing table with role-aware columns; ADMIN tools: init, create, edit (with minStockThreshold), delete (Yes/No modal), "+ Stock" delivery modal (CAT-US7); low-stock warning in table (CAT-US8/US9).
- `order` → `OrderForm.jsx` — place orders with discount breakdown display; stock availability masked for MERCHANT.
- `reporting` → `ReportingPlaceholder.jsx` — stub page for IPOS-SA-RPRT.
- `accounts` → `MerchantCreate.jsx` — admin form for atomic merchant+profile creation.
- `merchants` → `MerchantManagement.jsx` — edit profiles, standing transitions, month-close settlement.

**API Service (`api.js`) — exported functions:**

| Function | Method | Endpoint |
|----------|--------|----------|
| `getProducts()` | GET | `/api/products` |
| `searchProducts({productCode, q, minPrice, maxPrice})` | GET | `/api/products/search` |
| `createProduct(product)` | POST | `/api/products` |
| `updateProduct(id, product)` | PUT | `/api/products/{id}` |
| `deleteProduct(id)` | DELETE | `/api/products/{id}` |
| `recordDelivery(productId, {deliveryDate, quantityReceived, supplierReference})` | POST | `/api/products/{id}/deliveries` |
| `getCatalogueStatus()` | GET | `/api/catalogue/status` |
| `initializeCatalogue()` | POST | `/api/catalogue/initialize` |
| `getUsers()` | GET | `/api/users` |
| `createUser(user)` | POST | `/api/users` |
| `createMerchantAccount(data)` | POST | `/api/merchant-accounts` |
| `getMerchantProfiles()` | GET | `/api/merchant-profiles` |
| `updateMerchantProfile(userId, data)` | PUT | `/api/merchant-profiles/{userId}` |
| `closeMonth(yearMonth, settlementMode)` | POST | `/api/merchant-profiles/close-month` |
| `placeOrder(merchantId, items)` | POST | `/api/orders` |
| `getOrders()` | GET | `/api/orders` |

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

### Stock Delivery (CAT-US7)
`POST /api/products/{id}/deliveries` is protected by both the URL-level security rule (`POST /api/products/**` → ADMIN) and a method-level `@PreAuthorize("hasRole('ADMIN')")` annotation (defence-in-depth). The service method is `@Transactional`: it increments `availabilityCount` and saves a `StockDelivery` audit record atomically. The response includes `newAvailabilityCount` so the frontend can update without an extra GET.

### Min Stock Threshold (CAT-US8)
`minStockThreshold` is a nullable `Integer` on `Product`. `null` means no threshold is configured. The field is omitted from MERCHANT-facing responses via `@JsonInclude(NON_NULL)` on `CatalogueProductDto`. ADMIN/MANAGER see the value; the admin catalogue table highlights the cell in red when `availabilityCount ≤ minStockThreshold`.

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

63 tests total across 2 test classes. Test profile uses H2 in-memory DB with bootstrap disabled (`application-test.properties`).

| Test Class | Tests | Coverage |
|------------|-------|---------|
| `com/ipos/cat/CatalogueCatTest.java` | 28 Mockito unit tests | CAT-US2–US8: product CRUD, search, stock masking, delivery recording, threshold validation, audit logging |
| `com/ipos/cat/ProductControllerCatalogueCatWebMvcTest.java` | 15 WebMvc slice tests | DTO validation (400), success paths (200/201/204), role enforcement (403), CAT-US5/US6/US7/US8 |
| `com/ipos/service/MerchantAccountServiceTest.java` | 20 Mockito unit tests | ACC-US1 merchant creation, tier validation, discount calculations, standing guards, credit limits, ORD-US1 merchant isolation |

There are no frontend tests.

---

## User Story Backlog (What's Left)

Full status in `ACCprogress.txt` (ACC — complete) and `CATprogress.txt` (CAT).

**CAT (Catalogue & Inventory):**
- **CAT-US9**: Partial — low-stock warning shown inline in the admin catalogue table (stock ≤ threshold highlighted red with ⚠). No global dashboard banner yet.
- **CAT-US10**: `ReportingPlaceholder.jsx` is a stub; no low-stock report endpoint or controller yet.

**ORD (Orders):**
- `GET /api/orders` returns all orders regardless of caller role — merchant-scoped listing not implemented.

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
- `ACCprogress.txt` — ACC epic user story status (all complete)
- `CATprogress.txt` — CAT epic user story status (US2–US8 complete; US9 partial; US10 not started)
