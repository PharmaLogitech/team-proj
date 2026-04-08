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
| Catalogue & Inventory | IPOS-SA-CAT | ⚠️ Mostly Complete (US1 partial) |
| Orders & Fulfillment | IPOS-SA-ORD | ✅ Complete (US1–US6 all done) |
| Merchant Profiles | IPOS-SA-MER | ✅ Complete |
| Reporting | IPOS-SA-RPRT | ✅ Complete (CAT-US10 low-stock + RPT-US1–US5) |

### Backend Layers (`backend/src/main/java/com/ipos/`)

Standard Spring Boot layered architecture: **Controller → Service → Repository → Entity**. All business logic lives in services; controllers only parse HTTP and delegate.

**Key Services:**
- **`service/ProductService.java`** — Full catalogue CRUD: create (CAT-US2), update (CAT-US4), delete with audit log (CAT-US3), role-aware list (CAT-US6), multi-criteria search (CAT-US5), stock delivery recording with atomic availability increment (CAT-US7), min-stock threshold support (CAT-US8), low-stock query for report and banner (CAT-US9/US10).
- **`service/OrderService.java`** — Most complex service. Handles the entire order pipeline atomically: standing check → stock validation + decrement → price snapshot → discount calc (FIXED or FLEXIBLE credit) → credit limit enforcement → save with status ACCEPTED → invoice generation (ORD-US5). Also provides role-scoped order listing (`findOrdersForActor`) and lifecycle status updates (`updateOrderStatus`) with transition validation.
- **`service/InvoiceService.java`** — Invoice generation (ORD-US5). Snapshots merchant contact details, VAT, and order line items onto an Invoice entity. Called from `OrderService.placeOrder` in the same transaction. Sequential numbering (INV-YYYY-NNNNN). Idempotent via unique order FK.
- **`service/PaymentService.java`** — Payment recording (ORD-US6). Validates amount against invoice outstanding balance (totalDue - sum paid). ADMIN only.
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
- `MerchantProfile` — 1:1 with User. Holds contactEmail, contactPhone, addressLine, creditLimit, accountStatus (ACTIVE/INACTIVE), standing (NORMAL/IN_DEFAULT/SUSPENDED), discount plan (FIXED or FLEXIBLE), inDefaultSince, flexibleDiscountCredit, chequeRebatePending, vatRegistrationNumber, paymentTermsDays (default 30).
- `Product` — id, productCode (unique business SKU), description, price (BigDecimal), availabilityCount, minStockThreshold (nullable Integer — CAT-US8, null = no threshold configured).
- `StockDelivery` — id, product (ManyToOne), deliveryDate (LocalDate), quantityReceived, supplierReference (nullable, max 255), recordedBy (ManyToOne User), recordedAt (Instant). Table: `stock_deliveries`. CAT-US7 audit trail.
- `LowStockProductDto` — read-only DTO for the low-stock report (CAT-US9/US10): id, productCode, description, availabilityCount (0 if null), minStockThreshold.
- `CatalogueMetadata` — singleton row (id=1) recording when the catalogue was initialized (CAT-US1).
- `Order` → M:1 User (merchant), 1:M OrderItem. Snapshots grossTotal, fixedDiscountAmount, flexibleCreditApplied, totalDue at placement. Status lifecycle: ACCEPTED → PROCESSING → DISPATCHED (forward-only) + CANCELLED branch. Legacy PENDING/CONFIRMED kept with @Deprecated.
- `OrderItem` → M:1 Order, M:1 Product. Snapshots unitPriceAtOrder.
- `Invoice` — 1:1 with Order (unique FK). Snapshots merchant contact details, VAT, financial totals. 1:M InvoiceLine, 1:M Payment. Generated automatically at order placement (ORD-US5).
- `InvoiceLine` — M:1 Invoice. Snapshots product description, quantity, unitPrice, lineTotal.
- `Payment` — M:1 Invoice. Records method (BANK_TRANSFER/CARD/CHEQUE), amount, recordedBy (ADMIN user). ORD-US6.
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
| `/api/orders` | GET, POST | Authenticated (GET is role-scoped: MERCHANT own orders only) |
| `/api/orders/{id}/status` | PUT | MANAGER, ADMIN (ORD-US2 lifecycle transitions) |
| `/api/invoices` | GET | Authenticated (role-scoped: MERCHANT own; staff all) (ORD-US5) |
| `/api/invoices/{id}` | GET | Authenticated (MERCHANT own only; staff any) (ORD-US5) |
| `/api/invoices/{id}/payments` | POST | ADMIN only (ORD-US6 payment recording) |
| `/api/merchant-financials/balance` | GET | MERCHANT only (ORD-US3 outstanding balance) |
| `/api/reports/low-stock` | GET | MANAGER, ADMIN (CAT-US10 real-time low-stock report) |
| `/api/reports/sales-turnover` | GET | MANAGER, ADMIN (RPT-US1) |
| `/api/reports/invoices` | GET | MANAGER, ADMIN (RPT-US4 global invoice monitoring) |
| `/api/reports/stock-turnover` | GET | MANAGER, ADMIN (RPT-US5) |
| `/api/reports/merchants/{id}/order-history` | GET | MANAGER, ADMIN (RPT-US2) |
| `/api/reports/merchants/{id}/activity` | GET | MANAGER, ADMIN (RPT-US3) |

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
- `order` → `OrderForm.jsx` — multi-line order placement with discount breakdown; orders tracking table ("My Orders" for MERCHANT, "All Orders" for staff) with status badges, staff action buttons, and 15s auto-polling (ORD-US1/US2).
- `invoices` → `Invoices.jsx` — invoice listing (role-scoped), invoice detail with lines and payments, MERCHANT balance summary card (ORD-US3), ADMIN payment recording form (ORD-US6).
- `reporting` → `ReportingPlaceholder.jsx` — low-stock (CAT-US10) + RPT-US1–US5 operational reports (date ranges, printable tables).
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
| `getLowStockReport()` | GET | `/api/reports/low-stock` |
| `getSalesTurnoverReport({ start, end })` | GET | `/api/reports/sales-turnover` |
| `getGlobalInvoiceReport({ start, end })` | GET | `/api/reports/invoices` |
| `getStockTurnoverReport({ start, end })` | GET | `/api/reports/stock-turnover` |
| `getMerchantOrderHistory(merchantId, { start, end })` | GET | `/api/reports/merchants/{id}/order-history` |
| `getMerchantActivityReport(merchantId, { start, end })` | GET | `/api/reports/merchants/{id}/activity` |
| `getCatalogueStatus()` | GET | `/api/catalogue/status` |
| `initializeCatalogue()` | POST | `/api/catalogue/initialize` |
| `getUsers()` | GET | `/api/users` |
| `createUser(user)` | POST | `/api/users` |
| `createMerchantAccount(data)` | POST | `/api/merchant-accounts` |
| `getMerchantProfiles()` | GET | `/api/merchant-profiles` |
| `updateMerchantProfile(userId, data)` | PUT | `/api/merchant-profiles/{userId}` |
| `closeMonth(yearMonth, settlementMode)` | POST | `/api/merchant-profiles/close-month` |
| `placeOrder(merchantId, items)` | POST | `/api/orders` |
| `getOrders()` | GET | `/api/orders` (role-scoped by backend) |
| `updateOrderStatus(orderId, status)` | PUT | `/api/orders/{id}/status` |
| `getInvoices()` | GET | `/api/invoices` (role-scoped by backend) |
| `getInvoiceDetail(invoiceId)` | GET | `/api/invoices/{id}` |
| `recordPayment(invoiceId, amount, method)` | POST | `/api/invoices/{id}/payments` (ADMIN) |
| `getMerchantBalance()` | GET | `/api/merchant-financials/balance` (MERCHANT) |

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
Net outstanding exposure = sum of `totalDue` across all non-CANCELLED orders for that merchant, **minus** the sum of all **payments** recorded against that merchant’s invoices (`InvoiceRepository.sumPaymentsByMerchantId`). The result is floored at zero. New orders are rejected if `netExposure + newOrder.totalDue > creditLimit`. This aligns the credit check with money actually still owed after partial settlements (ORD-US6).

### Merchant Isolation (ORD-US1)
If the caller is a MERCHANT, `OrderService.placeOrder()` forces `merchantId` to the caller's own user ID at the service layer — merchants cannot place orders for others.

### Stock Delivery (CAT-US7)
`POST /api/products/{id}/deliveries` is protected by both the URL-level security rule (`POST /api/products/**` → ADMIN) and a method-level `@PreAuthorize("hasRole('ADMIN')")` annotation (defence-in-depth). The service method is `@Transactional`: it increments `availabilityCount` and saves a `StockDelivery` audit record atomically. The response includes `newAvailabilityCount` so the frontend can update without an extra GET.

### Min Stock Threshold (CAT-US8)
`minStockThreshold` is a nullable `Integer` on `Product`. `null` means no threshold is configured. The field is omitted from MERCHANT-facing responses via `@JsonInclude(NON_NULL)` on `CatalogueProductDto`. ADMIN/MANAGER see the value; the admin catalogue table highlights the cell in red when `availabilityCount < minStockThreshold` (strict less-than per US9 acceptance criterion).

### Low-Stock Warnings & Report (CAT-US9/US10)
A persistent low-stock warning banner is displayed below the navigation bar for ADMIN users on every page (`App.jsx`). It fetches `GET /api/reports/low-stock` (shared with the US10 report) and shows a count of affected products with an expandable details table including Product ID, Description, and Current Stock. The Reporting page (`ReportingPlaceholder.jsx`) displays the full low-stock report for MANAGER and ADMIN, with a Refresh button for on-demand regeneration. The backend uses `ProductRepository.findLowStockProducts()` with `COALESCE(availabilityCount, 0) < minStockThreshold` — no caching, real-time per US10 acceptance criteria.

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

127 tests total across 11 test classes (some source files contain multiple nested test classes). Test profile uses H2 in-memory DB with bootstrap disabled (`application-test.properties`).

| Test Class | Tests | Coverage |
|------------|-------|---------|
| `com/ipos/cat/CatalogueCatTest.java` | 31 Mockito unit tests | CAT-US2–US10: product CRUD, search, stock masking, delivery recording, threshold validation, audit logging, low-stock query |
| `com/ipos/cat/ProductControllerCatalogueCatWebMvcTest.java` | 15 WebMvc slice tests | DTO validation (400), success paths (200/201/204), role enforcement (403), CAT-US5/US6/US7/US8 |
| `com/ipos/cat/ReportControllerWebMvcTest.java` | 8 WebMvc slice tests | CAT-US10 low-stock RBAC; RPT-US4/US5 invoice + stock-turnover RBAC (`ReportingService` mocked; lives in `CatalogueCatTest.java`) |
| `com/ipos/ord/ORDOrderTest.java` | 9 Mockito unit tests | ORD-US1: placeOrder ACCEPTED status, empty/null items rejection, credit limit net of payments; ORD-US2: findOrdersForActor scoping, updateOrderStatus transitions |
| `com/ipos/ord/OrderControllerWebMvcTest.java` | 5 WebMvc slice tests | ORD-US2: GET /api/orders role-scoped, PUT status RBAC (MANAGER 200, MERCHANT 403, unauth 401) |
| `com/ipos/ord/ORDInvoicePaymentTest.java` | 4 + 3 + 5 tests (nested classes in same file) | ORD-US5/ORD-US6 Mockito; `PaymentServiceTest`; `InvoicePaymentWebMvcTest` WebMvc RBAC |
| `com/ipos/service/MerchantAccountServiceTest.java` | 20 Mockito unit tests | ACC-US1 merchant creation, tier validation, discount calculations, standing guards, credit limits, ORD-US1 merchant isolation |
| `com/ipos/service/ReportingServiceTest.java` | 21 Mockito unit tests | RPT-US1–US5 report service behaviour |
| `com/ipos/service/ProductServiceTest.java` | 6 Mockito unit tests | Product service unit tests |
| `com/ipos/ord/PaymentServiceTest.java` | 3 Mockito unit tests | Payment service |

`IposTestSuite.java` aggregates a subset of these for IDE suite runs (see `@SelectClasses`).

There are no frontend tests.

---

## User Story Backlog (What's Left)

Full status in `ACCprogress.txt` (ACC — complete), `CATprogress.txt` (CAT), `ORDprogress.txt` (ORD — US1–US6 complete), and `RPTprogress.txt` (RPT — US1–US5 complete).

**CAT (Catalogue & Inventory):**
- **CAT-US2–US10**: Complete. US9 persistent low-stock banner for ADMIN; US10 real-time low-stock report at `/api/reports/low-stock`.
- **CAT-US1**: Partial — catalogue initialization endpoint exists but is not enforced as a prerequisite before adding products.

**ORD (Orders):**
- **ORD-US1–US6**: All complete. Multi-line order placement, status lifecycle tracking, automated stock reduction, invoice generation with merchant/VAT snapshots, payment recording (Bank Transfer/Card/Cheque), and merchant outstanding balance with due-date elapsed tracking.

**RPT (Reporting):**
- **RPT-US1–US5**: Complete per `RPTprogress.txt` (sales turnover, merchant history, merchant activity, global invoices, stock turnover; plus CAT-US10 low-stock on the reporting route).

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
- `CATprogress.txt` — CAT epic user story status (US2–US10 complete; US1 partial)
- `ORDprogress.txt` — ORD epic user stories (verbatim) and implementation status (ORD-US1–US6)
