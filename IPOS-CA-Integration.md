# IPOS-CA-Integration — Complete guide (AI & developer replication)

**Purpose:** This document is the **single source of truth** for reproducing the **IPOS-SA ↔ IPOS-CA wholesale order integration** on any machine. It targets **AI coding agents** and **human developers** who must apply the same behaviour without drift.

**Scope:** Wholesale order placement, order status lifecycle, push notifications, and outstanding balance queries between **IPOS-CA** (pharmacy desktop app) and **IPOS-SA** (InfoPharma wholesale backend). **IPOS-PU** (public online shop) is **out of scope** — see [`IPOS-PU-Integration.md`](IPOS-PU-Integration.md) for PU-specific work.

**Companion docs (repo root):**

| Document | Role |
|----------|------|
| [`CLAUDE.md`](CLAUDE.md) | Monorepo overview (ports, stack, architecture). |
| [`IPOS-PU-Integration.md`](IPOS-PU-Integration.md) | SA ↔ PU integration (separate concern). |

---

## 1. Repository layout

| System | Path in this repo |
|--------|-------------------|
| **IPOS-SA** (Spring Boot API + React) | `backend/`, `frontend/` |
| **IPOS-CA** (Java Swing + SQLite) | `tempdir_ipos_ca/IN2033-Team-Project-Team-2/Team 2 - CA/` |

All CA file paths below are relative to `tempdir_ipos_ca/IN2033-Team-Project-Team-2/Team 2 - CA/`.

---

## 2. Runtime topology

| Process | Default port | Notes |
|---------|--------------|--------|
| IPOS-SA backend (Spring Boot) | **8080** | MySQL `ipos_sa` database required |
| IPOS-CA inbound server | **8081** | Embedded JDK `HttpServer` — only active with `-Dipos.http=true` |
| IPOS-SA React (Vite) | **5173** | SA admin UI for managing orders |
| IPOS-PU (not involved) | **8082** | Out of scope for this document |

**Startup order:**
1. Start **MySQL**, then start **IPOS-SA**: `cd backend && mvn spring-boot:run` (port 8080)
2. Compile **IPOS-CA** (first time): `cd "Team 2 - CA" && find src -name "*.java" > sources.txt && javac -cp "lib/*" -d out @sources.txt`
3. Start **IPOS-CA** with HTTP integration: `java -Dipos.http=true -cp "out:lib/*" app.Main`

CA logs on successful startup:
```
[HttpSaGateway] Logged in to SA successfully
[CaApiServer] Listening on port 8081 (/order-update, /online-sale, /stock)
```

---

## 3. Integration overview — four interface points

```
┌──────────────────────────────────────────────────────────────────────────┐
│  §1a      CA → SA    Place Wholesale Order     POST /api/orders         │
│  §1a-inv  CA → SA    Fetch Invoice by Order    GET /api/invoices/by-order│
│  §1b      CA → SA    Track Order Status        (via push, see §1c)      │
│  §1c      SA → CA    Push Status Update        POST /order-update       │
│  §1d      CA → SA    Query Outstanding Balance GET /merchant-financials  │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 4. Configuration matrix

### 4.1 IPOS-SA (`backend/src/main/resources/application.properties`)

| Property | Value | Purpose |
|----------|-------|---------|
| `ipos.bootstrap.enabled` | `true` | Seeds `ca_merchant` account + 14-product catalogue on fresh DB |
| `ipos.integration-ca.webhook-url` | `http://localhost:8081/order-update` | CA's inbound endpoint for status push |
| `ipos.integration-ca.webhook-enabled` | `true` | Master switch — set `false` if CA is offline |

### 4.2 IPOS-CA (command-line flag)

| Flag | Value | Purpose |
|------|-------|---------|
| `-Dipos.http` | `true` | Enables `HttpSaGateway` + `CaApiServer`; without it, all SA/PU is mocked locally |

CA's SA connection details are hardcoded in `HttpSaGateway.java`:

| Constant | Value |
|----------|-------|
| `SA_BASE` | `http://localhost:8080` |
| `CA_USERNAME` | `ca_merchant` |
| `CA_PASSWORD` | `ca_pass` |

---

## 5. SA changes made (IPOS-SA — our system)

### 5.1 Seed `ca_merchant` in DataBootstrap

**File:** `backend/src/main/java/com/ipos/config/DataBootstrap.java`

Added after the three existing PDF merchants:

```java
seedPdfMerchantIfMissing(
        "ca_merchant",
        "IPOS-CA Pharmacy",
        "ca_pass",
        "1 High Street, London EC1V 0HB",
        "0207 040 9999",
        new BigDecimal("50000"),
        DiscountPlanType.FIXED,
        new BigDecimal("0"),
        null);
```

This creates a `MERCHANT` user with a `MerchantProfile` (£50,000 credit limit, 0% fixed discount — wholesale cost). CA authenticates with these credentials on startup via `POST /api/auth/login`.

### 5.2 Add `DELIVERED` status to Order entity

**File:** `backend/src/main/java/com/ipos/entity/Order.java`

`OrderStatus` enum now includes `DELIVERED`:

```java
public enum OrderStatus {
    @Deprecated PENDING,
    @Deprecated CONFIRMED,
    ACCEPTED,
    PROCESSING,
    DISPATCHED,
    DELIVERED,
    CANCELLED
}
```

New fields added:

```java
@Column(name = "delivered_at")
private Instant deliveredAt;

@Column(name = "courier_name", length = 120)
private String courierName;

@Column(name = "courier_reference", length = 120)
private String courierReference;

@Column(name = "dispatch_date")
private LocalDate dispatchDate;

@Column(name = "expected_delivery_date")
private LocalDate expectedDeliveryDate;
```

Hibernate `ddl-auto=update` adds the columns automatically. Full lifecycle is now:
**ACCEPTED → PROCESSING → DISPATCHED → DELIVERED** (forward-only), plus **ACCEPTED | PROCESSING → CANCELLED**.

When transitioning to DISPATCHED, the shipping fields (courier name, courier reference, dispatch date, expected delivery date) are populated from the request body.

### 5.3 Add DISPATCHED → DELIVERED transition + CA webhook

**File:** `backend/src/main/java/com/ipos/service/OrderService.java`

**Transition map** updated:

```java
private static final Map<Order.OrderStatus, Set<Order.OrderStatus>> VALID_TRANSITIONS = Map.of(
        Order.OrderStatus.ACCEPTED,   Set.of(Order.OrderStatus.PROCESSING, Order.OrderStatus.CANCELLED),
        Order.OrderStatus.PROCESSING, Set.of(Order.OrderStatus.DISPATCHED, Order.OrderStatus.CANCELLED),
        Order.OrderStatus.DISPATCHED, Set.of(Order.OrderStatus.DELIVERED)
);
```

**`updateOrderStatus`** has a 6-arg overload that accepts optional shipping details (plus a 2-arg convenience overload that passes nulls). Sets `deliveredAt`, shipping fields, and fires the CA webhook:

```java
public Order updateOrderStatus(Long orderId, Order.OrderStatus newStatus,
                               String courierName, String courierReference,
                               LocalDate dispatchDate, LocalDate expectedDeliveryDate) {
    // ... find order, validate transition ...
    order.setStatus(newStatus);
    if (newStatus == Order.OrderStatus.DISPATCHED) {
        if (order.getDispatchedAt() == null) order.setDispatchedAt(Instant.now());
        if (courierName != null)          order.setCourierName(courierName);
        if (courierReference != null)     order.setCourierReference(courierReference);
        if (dispatchDate != null)         order.setDispatchDate(dispatchDate);
        if (expectedDeliveryDate != null) order.setExpectedDeliveryDate(expectedDeliveryDate);
    }
    if (newStatus == Order.OrderStatus.DELIVERED && order.getDeliveredAt() == null) {
        order.setDeliveredAt(Instant.now());
    }
    Order saved = orderRepository.save(order);
    notifyCaStatusChange(saved);
    return saved;
}
```

**`OrderController.UpdateOrderStatusRequest`** record now includes optional shipping fields:

```java
record UpdateOrderStatusRequest(Order.OrderStatus status,
                                String courierName,
                                String courierReference,
                                LocalDate dispatchDate,
                                LocalDate expectedDeliveryDate) {}
```

**Webhook method** (fire-and-forget) — includes shipping details for DISPATCHED:

```java
private void notifyCaStatusChange(Order order) {
    if (!caProperties.isWebhookEnabled()) return;
    try {
        RestTemplate rt = new RestTemplate();
        HashMap<String, Object> body = new HashMap<>();
        body.put("orderId", order.getId());
        body.put("status", order.getStatus().name());
        if (order.getStatus() == Order.OrderStatus.DISPATCHED) {
            if (order.getCourierName() != null)          body.put("courierName", order.getCourierName());
            if (order.getCourierReference() != null)     body.put("courierReference", order.getCourierReference());
            if (order.getDispatchDate() != null)         body.put("dispatchDate", order.getDispatchDate().toString());
            if (order.getExpectedDeliveryDate() != null) body.put("expectedDeliveryDate", order.getExpectedDeliveryDate().toString());
        }
        rt.postForObject(caProperties.getWebhookUrl(), body, String.class);
    } catch (Exception e) {
        System.err.println("[SA→CA] CA notification failed (non-fatal): " + e.getMessage());
    }
}
```

This fires for **every** status transition (PROCESSING, DISPATCHED, DELIVERED, CANCELLED). Shipping details are included only for DISPATCHED. If CA is offline, the SA transaction still succeeds.

### 5.4 CSRF exemptions for CA desktop client

**File:** `backend/src/main/java/com/ipos/security/SecurityConfig.java`

CA is a Java desktop `HttpClient` — it cannot participate in the browser CSRF cookie/header pattern. All CA-facing endpoints are exempted:

```java
.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
    .ignoringRequestMatchers("/api/auth/login", "/api/integration-pu/inbound/**",
            "/api/orders", "/api/orders/**",
            "/api/invoices", "/api/invoices/**",
            "/api/merchant-financials", "/api/merchant-financials/**")
)
```

CORS origin added for `http://localhost:8081` (CA's inbound server):

```java
config.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:8081"));
```

The SA browser frontend is unaffected — `fetchWithAuth` continues to send the `X-XSRF-TOKEN` header.

### 5.5 CA integration properties

**New file:** `backend/src/main/java/com/ipos/config/IntegrationCaProperties.java`

```java
@ConfigurationProperties(prefix = "ipos.integration-ca")
public class IntegrationCaProperties {
    private String webhookUrl = "http://localhost:8081/order-update";
    private boolean webhookEnabled = false;
    // getters + setters
}
```

**New file:** `backend/src/main/java/com/ipos/config/IntegrationCaConfig.java`

```java
@Configuration
@EnableConfigurationProperties(IntegrationCaProperties.class)
public class IntegrationCaConfig {
}
```

**Added to** `application.properties`:

```properties
ipos.integration-ca.webhook-url=http://localhost:8081/order-update
ipos.integration-ca.webhook-enabled=true
```

### 5.6 Invoice lookup by order ID

**File:** `backend/src/main/java/com/ipos/controller/InvoiceController.java`

New endpoint allows CA to fetch the invoice linked to a specific SA order:

```java
@GetMapping("/by-order/{orderId}")
public ResponseEntity<?> getByOrder(@PathVariable Long orderId, Authentication auth) {
    User caller = resolveUser(auth);
    Invoice invoice = invoiceRepository.findByOrder_Id(orderId).orElse(null);
    if (invoice == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "No invoice found for order " + orderId));
    }
    if (caller.getRole() == User.Role.MERCHANT
            && !invoice.getMerchant().getId().equals(caller.getId())) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Access denied."));
    }
    return ResponseEntity.ok(invoice);
}
```

Uses existing `InvoiceRepository.findByOrder_Id()`. Merchant isolation enforced — `ca_merchant` can only retrieve invoices linked to their own orders.

### 5.7 SA frontend: "Track Merchant Orders" page

Order tracking has been moved from `OrderForm.jsx` into a **dedicated page** called `TrackMerchantOrders.jsx`.

**New file:** `frontend/src/TrackMerchantOrders.jsx`

- Staff (MANAGER/ADMIN) see all orders with merchant name column and status transition buttons
- MERCHANT sees their own orders (read-only, no action buttons)
- Auto-polls `GET /api/orders` every 15 seconds + manual Refresh button
- Status badges: ACCEPTED (blue), PROCESSING (amber), DISPATCHED (green), DELIVERED (dark green), CANCELLED (red)
- Staff action buttons: ACCEPTED → [Processing, Cancelled], PROCESSING → [Dispatched, Cancelled], DISPATCHED → [Delivered]
- **Shipping column** — for DISPATCHED and DELIVERED orders, displays courier name, courier reference/tracking number, dispatch date, and expected delivery date
- **Dispatch modal** — when staff click the "Dispatched" button, a modal dialog collects shipping details (courier name, courier reference, dispatch date, expected delivery date) before confirming the status transition. These fields are sent with the `PUT /api/orders/{id}/status` request and stored on the order entity

**Route registration:**

- `frontend/src/auth/rbac.js`: Added `trackOrders: PACKAGE_ORD` to `ROUTE_PACKAGES`
- `frontend/src/App.jsx`: Added `trackOrders: "Track Merchant Orders"` to `NAV_LABELS` and `PAGE_COMPONENTS`

**`OrderForm.jsx` simplified** — now only contains the order placement form and financial breakdown. The orders table has been removed.

### 5.8 SA frontend: DELIVERED status in order tracking UI

`DELIVERED` is included in `TrackMerchantOrders.jsx` status labels, colours, and transition map so SA staff (MANAGER/ADMIN) can advance orders to DELIVERED from the web UI:

```javascript
const STATUS_LABELS = {
  // ... existing ...
  DELIVERED: "Delivered",
};

const STATUS_COLORS = {
  // ... existing ...
  DELIVERED: "#059669",
};

const NEXT_STATUSES = {
  ACCEPTED: ["PROCESSING", "CANCELLED"],
  PROCESSING: ["DISPATCHED", "CANCELLED"],
  DISPATCHED: ["DELIVERED"],
};
```

---

## 6. CA changes made (IPOS-CA — partner code, minimal)

### 6.1 Fix `mapSaStatus` + parse shipping details in CaApiServer

**File:** `src/integration/CaApiServer.java`

**Problem 1 (status mapping):** SA sends `ACCEPTED` (not `CONFIRMED`) for newly placed orders, and `PROCESSING` for picking/packing. CA's original switch only mapped `CONFIRMED` → `ACCEPTED` and had no case for `PROCESSING`. Both would fall to the `default` branch and be **silently dropped**.

**Fix:** Added two cases:

```java
private OrderStatus mapSaStatus(String saStatus) {
    return switch (saStatus.toUpperCase()) {
        case "PENDING"    -> OrderStatus.PENDING;
        case "CONFIRMED"  -> OrderStatus.ACCEPTED;
        case "ACCEPTED"   -> OrderStatus.ACCEPTED;      // NEW
        case "PROCESSING" -> OrderStatus.BEING_PROCESSED; // NEW
        case "DISPATCHED" -> OrderStatus.DISPATCHED;
        case "DELIVERED"  -> OrderStatus.DELIVERED;
        case "CANCELLED"  -> OrderStatus.CANCELLED;
        default -> {
            System.err.println("[CaApiServer] Unknown SA status received: " + saStatus);
            yield null;
        }
    };
}
```

**Problem 2 (shipping details):** SA's webhook for DISPATCHED status sends `courierName`, `courierReference`, `dispatchDate`, and `expectedDeliveryDate` in the JSON body. CA's `handleOrderUpdate()` was only reading `orderId` and `status`, silently dropping all shipping fields. They were never stored in SQLite.

**Fix:** `handleOrderUpdate()` now parses all 4 optional shipping fields from the JSON and passes them to `WholesaleOrderService.receiveStatusUpdate()`:

```java
String    courierName  = json.has("courierName")      ? json.get("courierName").getAsString()      : null;
String    courierRef   = json.has("courierReference")  ? json.get("courierReference").getAsString()  : null;
LocalDate dispatchDate = json.has("dispatchDate")      ? LocalDate.parse(json.get("dispatchDate").getAsString()) : null;
LocalDate expectedDel  = json.has("expectedDeliveryDate") ? LocalDate.parse(json.get("expectedDeliveryDate").getAsString()) : null;

orderService.receiveStatusUpdate(saId, status, courierName, courierRef, dispatchDate, expectedDel);
```

`WholesaleOrderService.receiveStatusUpdate()` now has a 6-arg overload that forwards the shipping details to `repo.updateStatus()`, which already supported all 6 fields.

### 6.2 Added `getInvoiceByOrderId()` and `getOutstandingBalance()` to gateway

**Files:** `src/integration/ISaGateway.java`, `src/integration/HttpSaGateway.java`, `src/integration/MockSaGateway.java`

Two new methods added to the SA gateway interface and both implementations:

```java
// ISaGateway.java — new methods
Map<String, Object> getInvoiceByOrderId(int saOrderId);
Map<String, Object> getOutstandingBalance();
```

- `getInvoiceByOrderId()` calls `GET /api/invoices/by-order/{saOrderId}` and parses invoice details (number, dates, lines, totals)
- `getOutstandingBalance()` calls `GET /api/merchant-financials/balance` and returns outstanding total, currency, due date, and days elapsed
- Both return `null` if SA is unreachable (fire-and-forget pattern)
- `MockSaGateway` returns stub data so the app runs without SA

### 6.3 "View Invoice" and "Check Balance" buttons in WholesaleOrderUI

**File:** `src/ui/WholesaleOrderUI.java`

Two new buttons added to the button panel:

- **View Invoice** — enabled when a selected order has an `saOrderId > 0`. Fetches the invoice from SA via `SwingWorker` and displays it in a scrollable dialog (invoice number, dates, line items, gross total, discounts, total due)
- **Check Balance** — always enabled. Queries `getOutstandingBalance()` and displays the result with overdue warnings:
  - 1–30 days overdue: yellow reminder
  - >30 days overdue: red suspension warning

---

## 7. Interface contracts (HTTP)

### 7.1 §1a — CA → SA: Place Wholesale Order

| Detail | Value |
|--------|-------|
| **Trigger** | Merchant places order from CA's Wholesale Orders screen |
| **Prerequisites** | CA must be logged in (session established via §7.1a below) |

#### 7.1a Authentication

| | |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/auth/login` |
| **Headers** | `Content-Type: application/json` |
| **Body** | `{ "username": "ca_merchant", "password": "ca_pass" }` |
| **Success** | `200` + `JSESSIONID` cookie (stored by CA's `CookieManager`) |
| **Failure** | `401` — credentials wrong or user doesn't exist |

#### 7.1b Order placement

| | |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8080/api/orders` |
| **Headers** | `Content-Type: application/json` + `JSESSIONID` cookie (automatic) |
| **Body** | `{ "merchantId": 0, "items": [{ "productId": 1, "quantity": 10 }, ...] }` |
| **Success** | `200` + full `Order` JSON including `id` (SA's numeric order ID) |
| **Failure** | `400` + `{ "error": "..." }` (insufficient stock, credit limit, standing block) |

**Notes:**
- `merchantId: 0` is safe — SA's ORD-US1 enforcement overrides it with the authenticated user's ID.
- Product IDs must match SA's catalogue. On a fresh database, `CatalogueDataBootstrap` seeds 14 products with IDs 1–14 (see §9).
- SA creates the order with status **`ACCEPTED`**, generates an invoice, and reduces stock — all in one atomic transaction.
- CA extracts `id` from the response and stores it locally as `sa_order_id`.

### 7.2 §1b — CA → SA: Track Order Status

Order status tracking is implemented via the **push** mechanism (§7.3). CA's local SQLite database is the source of truth for the UI — it is kept up-to-date by inbound status pushes from SA.

CA does **not** poll SA for status. This is by design: CA's `HttpSaGateway.getOrderById()` and `getOrderHistory()` read from local SQLite.

### 7.3 §1c — SA → CA: Push Status Update

| | |
|---|---|
| **Method** | `POST` |
| **URL** | `http://localhost:8081/order-update` |
| **Headers** | `Content-Type: application/json` |
| **Body** | `{ "orderId": <SA order ID>, "status": "<STATUS_STRING>" }` |
| **Body (DISPATCHED)** | `{ "orderId": <SA order ID>, "status": "DISPATCHED", "courierName": "...", "courierReference": "...", "dispatchDate": "YYYY-MM-DD", "expectedDeliveryDate": "YYYY-MM-DD" }` |
| **Success** | `200` + `{ "ok": true }` |
| **Failure** | `400` + `{ "error": "..." }` (malformed JSON, missing fields) |

**When it fires:** Every time SA's `OrderService.updateOrderStatus()` is called (via `PUT /api/orders/{id}/status` from the SA admin UI or any API client). For DISPATCHED status, the webhook includes shipping details (courier name, courier reference, dispatch date, expected delivery date) if they were provided during the status transition.

**Status strings SA sends:**

| SA `Order.OrderStatus` | String sent | CA maps to |
|------------------------|-------------|------------|
| `ACCEPTED` | `"ACCEPTED"` | `OrderStatus.ACCEPTED` |
| `PROCESSING` | `"PROCESSING"` | `OrderStatus.BEING_PROCESSED` |
| `DISPATCHED` | `"DISPATCHED"` | `OrderStatus.DISPATCHED` |
| `DELIVERED` | `"DELIVERED"` | `OrderStatus.DELIVERED` (triggers stock increase) |
| `CANCELLED` | `"CANCELLED"` | `OrderStatus.CANCELLED` |

**What CA does on DELIVERED:** `WholesaleOrderService.receiveStatusUpdate()` calls `markDelivered()`, which loops through every order line and calls `stockService.increaseStock(itemId, quantity)` to replenish CA's local stock.

### 7.4 §1a-inv — CA → SA: Fetch Invoice by Order ID

| | |
|---|---|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/invoices/by-order/{saOrderId}` |
| **Headers** | `JSESSIONID` cookie (from login session) |
| **Access** | `MERCHANT` (own orders only), `MANAGER`, `ADMIN` |

**Response (200):** Full `Invoice` JSON including `invoiceNumber`, `issuedAt`, `dueDate`, `grossTotal`, `fixedDiscountAmount`, `flexibleCreditApplied`, `totalDue`, and nested `lines[]` array (each with `description`, `quantity`, `unitPrice`, `lineTotal`).

**Response (404):** `{ "error": "No invoice found for order X" }` — order has no invoice (should not happen for ACCEPTED orders).

**Response (403):** Merchant trying to view another merchant's invoice.

**When CA calls this:** After clicking "View Invoice" in the WholesaleOrderUI. The button is only enabled when the selected order has `saOrderId > 0`.

### 7.5 §1d — CA → SA: Query Outstanding Balance

| | |
|---|---|
| **Method** | `GET` |
| **URL** | `http://localhost:8080/api/merchant-financials/balance` |
| **Headers** | `JSESSIONID` cookie (from login session) |
| **Access** | `MERCHANT` only (enforced by SA's `@PreAuthorize`) |

**Response (200):**

```json
{
  "outstandingTotal": 1234.50,
  "currency": "GBP",
  "oldestUnpaidDueDate": "2026-03-15",
  "daysElapsedSinceDue": 31
}
```

| Field | Type | Meaning |
|-------|------|---------|
| `outstandingTotal` | `BigDecimal` | Sum of unpaid invoice amounts |
| `currency` | `String` | Always `"GBP"` |
| `oldestUnpaidDueDate` | `LocalDate` or `null` | Earliest unpaid invoice due date |
| `daysElapsedSinceDue` | `long` | Days since oldest unpaid due date (0 if not overdue) |

**SA-side implementation:** Already complete — `MerchantFinancialsController` computes this from invoices and payments. No SA changes needed.

**CA-side implementation:** See §8 (complete).

---

## 8. CA integration features — now implemented

### 8.1 Balance query (§1d) — COMPLETE

Both `getOutstandingBalance()` and `getInvoiceByOrderId()` are now implemented in `ISaGateway`, `HttpSaGateway`, and `MockSaGateway`. The "Check Balance" button in `WholesaleOrderUI` calls the SA endpoint and displays the result with overdue warnings.

**Payment late warnings (spec requirement) — implemented:**
- If `daysElapsedSinceDue` is **1–30**: yellow reminder dialog ("Payment is X days overdue")
- If `daysElapsedSinceDue` is **>30**: red error dialog ("Account may be suspended — new orders will be rejected")

SA enforces the standing block on the `placeOrder` path (standing guard in `OrderService`). The balance response gives CA the data to display proactive warnings.

### 8.2 Invoice retrieval (§1a-inv) — COMPLETE

The "View Invoice" button in `WholesaleOrderUI` calls `GET /api/invoices/by-order/{saOrderId}` and displays the full invoice in a scrollable dialog: invoice number, issued date, due date, line items, gross total, discounts, and total due.

---

## 9. Product ID alignment

On a fresh SA database, `CatalogueDataBootstrap` inserts 14 products in array order, receiving sequential auto-increment IDs starting at 1.

| SA Product ID | SA Description | CA Stock ID | CA Name |
|---------------|----------------|-------------|---------|
| 1 | Paracetamol box | 1 | Paracetamol |
| 2 | Aspirin box | 2 | Aspirin |
| 3 | Analgin box | 3 | Analgin |
| 4 | Celebrex, caps 100 mg box | 4 | Celebrex, caps 100 mg |
| 5 | Celebrex, caps 200 mg box | 5 | Celebrex, caps 200 mg |
| 6 | Retin-A Tretin, 30 g box | 6 | Retin-A Tretin, 30 g |
| 7 | Lipitor TB, 20 mg box | 7 | Lipitor TB, 20 mg |
| 8 | Claritin CR, 60g box | 8 | Claritin CR, 60g |
| 9 | Iodine tincture | 9 | Iodine tincture |
| 10 | Rhynol | 10 | Rhynol |
| 11 | Ospen box | 11 | Ospen |
| 12 | Amopen box | 12 | Amopen |
| 13 | Vitamin C box | 13 | Vitamin C |
| 14 | Vitamin B12 box | 14 | Vitamin B12 |

**Requirement:** Both SA and CA databases must be fresh (or IDs must align) for orders to reference the correct products.

---

## 10. Order lifecycle — end-to-end flow

```
CA places order
  └── POST /api/orders → SA creates order (ACCEPTED), generates invoice
        └── SA admin advances status in web UI:
              PUT /api/orders/{id}/status { "status": "PROCESSING" }
                └── SA saves + POSTs to CA: { "orderId": id, "status": "PROCESSING" }
                      └── CA updates local order → BEING_PROCESSED
              PUT /api/orders/{id}/status { "status": "DISPATCHED",
                    "courierName": "Royal Mail", "courierReference": "RM123456789GB",
                    "dispatchDate": "2026-04-15", "expectedDeliveryDate": "2026-04-18" }
                └── SA saves (sets dispatchedAt + shipping fields)
                └── POSTs to CA: { "orderId": id, "status": "DISPATCHED",
                      "courierName": "Royal Mail", "courierReference": "RM123456789GB",
                      "dispatchDate": "2026-04-15", "expectedDeliveryDate": "2026-04-18" }
                      └── CA updates local order → DISPATCHED
                      └── CA stores courier/expected delivery in SQLite
              PUT /api/orders/{id}/status { "status": "DELIVERED" }
                └── SA saves (sets deliveredAt) + POSTs to CA: { ... "DELIVERED" }
                      └── CA updates local order → DELIVERED
                      └── CA increments stock for every line item
```

---

## 11. Verify integration with curl

### Test 1 — CA → SA: Login

```bash
curl -v -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"ca_merchant","password":"ca_pass"}'
# Expected: 200 + Set-Cookie: JSESSIONID=...
```

### Test 2 — CA → SA: Place order (use JSESSIONID from Test 1)

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=<value from test 1>" \
  -d '{"merchantId":0,"items":[{"productId":1,"quantity":10}]}'
# Expected: 200 + Order JSON with "id" and "status":"ACCEPTED"
```

### Test 3 — SA → CA: Push status update

```bash
curl -X POST http://localhost:8081/order-update \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"status":"PROCESSING"}'
# Expected: {"ok":true}
```

### Test 3b — SA: Dispatch with shipping details (via SA's API)

```bash
curl -X PUT http://localhost:8080/api/orders/1/status \
  -H "Content-Type: application/json" \
  -H "Cookie: JSESSIONID=<admin session>" \
  -d '{"status":"DISPATCHED","courierName":"Royal Mail","courierReference":"RM123456789GB","dispatchDate":"2026-04-15","expectedDeliveryDate":"2026-04-18"}'
# Expected: 200 + Order JSON with courierName, courierReference, dispatchDate, expectedDeliveryDate populated
# SA also POSTs to CA with shipping details in the webhook body
```

### Test 4 — SA → CA: Push DELIVERED (triggers stock increase)

```bash
curl -X POST http://localhost:8081/order-update \
  -H "Content-Type: application/json" \
  -d '{"orderId":1,"status":"DELIVERED"}'
# Expected: {"ok":true}
# CA console: stock increased for each line item in the order
```

### Test 5 — CA → SA: Fetch invoice by order ID

```bash
curl -X GET http://localhost:8080/api/invoices/by-order/1 \
  -H "Cookie: JSESSIONID=<value from test 1>"
# Expected: 200 + Invoice JSON with invoiceNumber, lines, totalDue
```

### Test 6 — CA → SA: Query balance

```bash
curl -X GET http://localhost:8080/api/merchant-financials/balance \
  -H "Cookie: JSESSIONID=<value from test 1>"
# Expected: 200 + {"outstandingTotal":...,"currency":"GBP",...}
```

---

## 12. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| CA logs `SA login failed — status 401` | `ca_merchant` doesn't exist in SA database | Run SA with `ipos.bootstrap.enabled=true` on a fresh DB, or manually create via `POST /api/merchant-accounts` |
| CA logs `SA rejected order — 403` | CSRF token required | Verify `/api/orders` is in the CSRF ignore list in `SecurityConfig.java` |
| CA logs `SA rejected order — 400` | Insufficient stock, credit limit, or standing block | Check SA console for the specific error; verify products exist with adequate stock |
| SA logs `[SA→CA] CA notification failed` | CA is not running or not in HTTP mode | Start CA with `-Dipos.http=true`; check port 8081 is not in use |
| CA receives status but nothing happens | Unknown status string | Check `mapSaStatus` handles the string SA sends (e.g. `ACCEPTED`, `PROCESSING`) |
| Stock not increasing on DELIVERED | `findBySaOrderId` returns null | Verify CA's local order has `sa_order_id` set (check wholesale_orders table in SQLite) |
| `webhook-enabled=true` but no POSTs | Properties not loaded | Ensure `IntegrationCaConfig` class exists with `@EnableConfigurationProperties` |
| Balance endpoint returns 403 | Wrong role | `ca_merchant` must be `MERCHANT` role; verify with `GET /api/auth/me` |
| Invoice fetch returns 403 | CSRF blocking request | Verify `/api/invoices/**` is in the CSRF ignore list in `SecurityConfig.java` |
| Invoice fetch returns 404 | No invoice for order | Verify the order was placed via SA and has status ACCEPTED (invoice generated at placement) |
| "View Invoice" button disabled | No SA order ID | Order was placed in mock mode or SA submission failed — `saOrderId` must be > 0 |
| Shipping details missing on DISPATCHED order (SA side) | Dispatch done without modal | Shipping fields are only saved when provided in the `PUT /api/orders/{id}/status` request body. Ensure the dispatch modal is used (TrackMerchantOrders) or fields are sent via API |
| Courier/Expected Delivery columns blank in CA after SA dispatch | CA not parsing shipping fields from webhook | Ensure `CaApiServer.handleOrderUpdate()` parses `courierName`, `courierReference`, `dispatchDate`, `expectedDeliveryDate` from the JSON and passes them to `WholesaleOrderService.receiveStatusUpdate()` (6-arg overload). Recompile CA after any changes |
| `courier_name` column doesn't exist | Schema not updated | Hibernate `ddl-auto=update` creates columns on startup. Restart the SA backend once after the entity change |

---

## 13. Files changed summary

### IPOS-SA (12 files changed, 3 files created)

| File | Change |
|------|--------|
| `backend/src/main/java/com/ipos/config/DataBootstrap.java` | Added `ca_merchant` / `ca_pass` merchant seed |
| `backend/src/main/java/com/ipos/entity/Order.java` | Added `DELIVERED` to `OrderStatus` enum; `deliveredAt`, `courierName`, `courierReference`, `dispatchDate`, `expectedDeliveryDate` fields |
| `backend/src/main/java/com/ipos/service/OrderService.java` | 6-arg `updateOrderStatus()` with shipping fields; webhook sends shipping details for DISPATCHED |
| `backend/src/main/java/com/ipos/controller/OrderController.java` | `UpdateOrderStatusRequest` record expanded with shipping fields |
| `backend/src/main/java/com/ipos/security/SecurityConfig.java` | CSRF exempt `/api/orders/**`, `/api/invoices/**`, `/api/merchant-financials/**`; CORS origin `localhost:8081` |
| `backend/src/main/java/com/ipos/controller/InvoiceController.java` | Added `GET /api/invoices/by-order/{orderId}` endpoint |
| `backend/src/main/resources/application.properties` | Added `ipos.integration-ca.*` properties |
| `backend/src/test/java/com/ipos/ord/ORDOrderTest.java` | Updated WebMvc mock to match 6-arg `updateOrderStatus` signature |
| `frontend/src/OrderForm.jsx` | Simplified — order placement only (tracking table removed) |
| `frontend/src/api.js` | `updateOrderStatus()` now accepts optional `shippingDetails` object |
| `frontend/src/auth/rbac.js` | Added `trackOrders` route to `ROUTE_PACKAGES` |
| `frontend/src/App.jsx` | Added `trackOrders` to `NAV_LABELS`, `PAGE_COMPONENTS`, imported `TrackMerchantOrders` |
| `frontend/src/TrackMerchantOrders.jsx` | **NEW** — order tracking page with status badges, Shipping column, dispatch modal, and staff action buttons |
| `backend/src/main/java/com/ipos/config/IntegrationCaProperties.java` | **NEW** — CA webhook config bean |
| `backend/src/main/java/com/ipos/config/IntegrationCaConfig.java` | **NEW** — `@EnableConfigurationProperties` for CA integration |

### IPOS-CA (6 files changed)

| File | Change |
|------|--------|
| `src/integration/CaApiServer.java` | Added `ACCEPTED`/`PROCESSING` to `mapSaStatus`; parse shipping fields from webhook JSON |
| `src/service/WholesaleOrderService.java` | Added 6-arg `receiveStatusUpdate()` overload that forwards shipping details to repo |
| `src/integration/ISaGateway.java` | Added `getInvoiceByOrderId()` and `getOutstandingBalance()` methods |
| `src/integration/HttpSaGateway.java` | Implemented `getInvoiceByOrderId()` (calls SA) and `getOutstandingBalance()` (calls SA) |
| `src/integration/MockSaGateway.java` | Added stub implementations for both new methods |
| `src/ui/WholesaleOrderUI.java` | Added "View Invoice" and "Check Balance" buttons with `SwingWorker` handlers |

---

## 14. Default credentials

### SA staff (for managing orders via web UI)

| Username | Password | Role | Use |
|----------|----------|------|-----|
| `Sysdba` | `London_weighting` | ADMIN | Full access — advance orders, manage catalogue |
| `manager` | `Get_it_done` | MANAGER | Advance order status |

### SA merchants

| Username | Password | Role | Use |
|----------|----------|------|-----|
| `ca_merchant` | `ca_pass` | MERCHANT | **CA integration account** — session auth for orders + balance |
| `city` | `northampton` | MERCHANT | PDF sample merchant |
| `cosymed` | `bondstreet` | MERCHANT | PDF sample merchant |
| `hello` | `there` | MERCHANT | PDF sample merchant |

### CA local users

| Username | Password | Role |
|----------|----------|------|
| `admin` | `admin123` | Admin |
| `pharmacist` | `pharm123` | Pharmacist |
| `manager` | `manager123` | Manager |
