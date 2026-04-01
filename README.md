# IPOS-SA — Pharmaceutical Stock & Order Management

A **Spring Boot (Java)**, **React (Vite)**, and **MySQL** prototype for pharmaceutical catalogue browsing, ordering, and **account management** (merchant profiles, credit limits, fixed/flexible discount plans, role-based access, and manager maintenance).

```
React (Frontend)  →  Spring Boot (Backend API)  →  MySQL (Database)
```

---

## Table of Contents

- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Full Setup Guide](#full-setup-guide)
- [Authentication & Default Users](#authentication--default-users)
- [Running the Application](#running-the-application)
- [Login and Where Data Is Stored](#login-and-where-data-is-stored)
- [Feature Packages (Brief Alignment)](#feature-packages-brief-alignment)
- [Key API Endpoints (summary)](#key-api-endpoints-summary)
- [Documentation & Progress Reports](#documentation--progress-reports)
- [Backend Tests](#backend-tests)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Prerequisite knowledge needed](#prerequisite-knowledge-needed)

---

## Tech Stack

| Layer    | Technology           | Version / notes      |
|----------|----------------------|----------------------|
| Frontend | React (Vite, JS)     | Node 20+ required    |
| Backend  | Spring Boot          | 3.4.x, **Java 17** or Java 23 (`pom.xml`) |
| Database | MySQL                | 8.x+                 |
| Build    | Maven                | 3.9+ (`mvn` on PATH) |

---

## Prerequisites

Install the following **before** running the app:

| Tool      | Purpose                          | Where to get it |
|-----------|-----------------------------------|-----------------|
| **Java 17 or 23**       | Backend compiles and runs on JVM (`java.version` in `backend/pom.xml`) | [Eclipse Temurin 17](https://adoptium.net/) |
| **Node.js 20+**   | Frontend build and dev server     | [nodejs.org](https://nodejs.org/) (LTS) or `winget install OpenJS.NodeJS.LTS` |
| **MySQL 8+**      | Database for users, products, orders | [MySQL](https://dev.mysql.com/downloads/) or a packaged install (e.g. XAMPP) |
| **Maven 3.9+**    | Build and run backend             | [Maven](https://maven.apache.org/download.cgi) — run commands from `backend/` |

- **Node:** Vite 6 needs Node 18+ (Node 20 LTS recommended). On Windows, if `npm` scripts are blocked, run:  
  `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

- **Java:** Use `java -version` and ensure it matches `java.version` in `backend/pom.xml` (17).

---

## Full Setup Guide

### Step 1: Clone or download the project

Use the project folder as your working directory (e.g. `team-proj`).

### Step 2: MySQL setup

1. **Install and start MySQL** (port 3306 by default).

2. **Create the database:**

   ```sql
   CREATE DATABASE ipos_sa;
   ```

3. **Configure the backend** to use your MySQL user and password:

   - Open `backend/src/main/resources/application.properties`.
   - Set:

   ```properties
   spring.datasource.username=YOUR_MYSQL_USERNAME
   spring.datasource.password=YOUR_MYSQL_PASSWORD
   ```

   Set values to match **your** MySQL user (e.g. `root` or a dedicated user). Do not commit real production passwords; use local credentials only.

4. **Optional:** Create a dedicated user and grant access:

   ```sql
   CREATE USER 'ipos'@'localhost' IDENTIFIED BY 'your_password';
   GRANT ALL PRIVILEGES ON ipos_sa.* TO 'ipos'@'localhost';
   FLUSH PRIVILEGES;
   ```

   Then set `spring.datasource.username=ipos` and `spring.datasource.password=your_password` in `application.properties`.

On first run, Spring Boot (Hibernate) will create the tables in `ipos_sa` automatically (`spring.jpa.hibernate.ddl-auto=update`).

### Step 3: Backend (Spring Boot)

1. Open a terminal in the **project root** and go to the backend:

   ```bash
   cd backend
   ```

2. Run the application (Maven must be on your PATH):

   ```bash
   mvn spring-boot:run
   ```

   On **Windows PowerShell**, if `-D` flags are passed to Maven, quote them (e.g. `mvn test "-Dspring.profiles.active=test"`).

3. Wait until you see something like:

   ```
   Started IposApplication in X.XXX seconds
   Tomcat started on port 8080
   ```

4. The API is available at **http://localhost:8080** (e.g. `http://localhost:8080/api/products`).

If you see **"release version not supported"**, install a JDK that matches `java.version` in `backend/pom.xml` (currently **17**) and point `JAVA_HOME` to it.

If you see **"Access denied for user 'root'@'localhost'"**, the username or password in `application.properties` does not match your MySQL setup. Fix the two properties and try again.

If MySQL fails DDL on **`year_month`**, ensure you are on a current schema: the flexible settlement table uses column name **`settlement_year_month`** (MySQL reserves `year_month`).

### Step 4: Frontend (React + Vite)

1. In a **new** terminal, go to the frontend:

   ```bash
   cd frontend
   ```

2. Install dependencies (first time only):

   ```bash
   npm install
   ```

3. Start the dev server:

   ```bash
   npm run dev
   ```

4. Open the URL shown (usually **http://localhost:5173**). The frontend will proxy `/api/*` requests to the backend.

If **Node is too old** (e.g. Node 17), you'll see errors about `node:fs/promises` or similar. Install Node 20 LTS or newer.

On **Windows**, if you get a PowerShell error about scripts not being signed when running `npm run dev`, run:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Then try `npm run dev` again.

---

## Authentication & Default Users

The app uses **real username + password authentication** with BCrypt password hashing and server-side sessions.

### Default Bootstrap Users

When `ipos.bootstrap.enabled=true` in `application.properties` (the default), the following users are created automatically on first startup if the users table is empty:

| Username | Password | Role | Access |
|----------|----------|------|--------|
| `admin` | `admin123` | ADMIN | Full access to all packages |
| `manager` | `manager123` | MANAGER | Reporting + merchant settings |
| `merchant` | `merchant123` | MERCHANT | Catalogue browsing + orders |

The seeded **merchant** user also has a **MerchantProfile** (contact details, £10,000 credit limit, FIXED 5% discount, standing NORMAL, **account status ACTIVE**).

Simply start the backend, open the frontend, and log in with one of these credentials.

### Creating Additional Users (Admin only)

Only **ADMIN** users can create new accounts.

**Merchant accounts** must be created via the **Accounts** page in the UI (or `POST /api/merchant-accounts`), which requires contact details, credit limit, and discount plan. This enforces the brief requirement: "if the required details are not provided the account will not be created."

**Staff accounts** (ADMIN, MANAGER) can be created via `POST /api/users`. Attempting to create a MERCHANT via this endpoint will be rejected — use the merchant account endpoint instead.

**PowerShell example (merchant account):**

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}' -WebSession $session

Invoke-RestMethod -Uri "http://localhost:8080/api/merchant-accounts" -Method Post -ContentType "application/json" -Body '{"name":"Alice Pharma","username":"alice","password":"pass123","contactEmail":"alice@pharma.co","contactPhone":"07700 900001","addressLine":"2 High St, London","creditLimit":5000,"planType":"FIXED","fixedDiscountPercent":3}' -WebSession $session
```

### Security Notes

- Passwords are stored as BCrypt hashes (never plaintext).
- Sessions are managed via JSESSIONID cookies.
- CSRF protection is enabled (XSRF-TOKEN cookie + X-XSRF-TOKEN header).
- Role-based access control restricts which pages and API endpoints each role can use.
- See **`RBAC.md`** in the **project root** for the full role × package access matrix.

---

## Running the Application

1. **Start MySQL** (if not running as a service).
2. **Start the backend:**  
   `cd backend` then `mvn spring-boot:run`.
3. **Start the frontend:**  
   `cd frontend` then `npm run dev`.
4. Open **http://localhost:5173** in your browser.
5. On the Login screen, enter credentials (e.g., `admin` / `admin123`) and click **Log in**.
6. Navigation items are shown based on your role. Use **Catalogue** to view products and **Place Order** to create orders. **Accounts** (admin) creates merchants; **Merchants** (manager/admin) edits profiles, standing, flexible month-close.

To place orders, you need products. Log in as admin and create them via the API (there is no admin catalogue UI yet — see `CATprogress.txt`):

```powershell
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body '{"username":"admin","password":"admin123"}' -WebSession $session
Invoke-RestMethod -Uri "http://localhost:8080/api/products" -Method Post -ContentType "application/json" -Body '{"description":"Paracetamol 500mg","price":4.99,"availabilityCount":100}' -WebSession $session
```

---

## Login and Where Data Is Stored

- **Authentication:** The Login screen requires a username and password. Spring Security verifies credentials against BCrypt hashes stored in the database.

- **User accounts (who can log in):**  
  Stored in **MySQL**, in the **`users`** table. Each user has a `username`, `password_hash` (BCrypt), `name`, and `role` (ADMIN, MANAGER, or MERCHANT).

- **Who is logged in right now:**  
  Stored in a **server-side HTTP session** (JSESSIONID cookie). The session persists across page refreshes. When the React app loads, it calls `GET /api/auth/me` to restore the logged-in user. Logging out invalidates the session.

- **Role-based access:**  
  Navigation items are shown/hidden based on the user's role. Backend API endpoints are also protected. See **`RBAC.md`** (project root) for full details.

---

## Feature Packages (Brief Alignment)

| Package        | Scope in this repo | Notes |
|----------------|--------------------|--------|
| **IPOS-SA-ACC** | Implemented (prototype) | Merchant onboarding (`POST /api/merchant-accounts`, validated DTO), staff users (`/api/users`), RBAC. See **`ACCprogress.txt`**. |
| **IPOS-SA-MER** | Implemented (prototype) | Merchant **profiles**: `GET`/`PUT /api/merchant-profiles`, flexible **month-close** (`POST .../close-month`), contact edits, standing rules (`IN_DEFAULT` → `NORMAL`/`SUSPENDED` with 30-day rule for managers), **`StandingChangeLog`** audit. Manager + Admin only (not merchants). Documented in **`ACCprogress.txt`** / **`RBAC.md`**. |
| **IPOS-SA-CAT** | Partial | `Product` entity, `GET`/`POST`/`PUT`/`DELETE /api/products` (ADMIN). Admin catalogue UI with create, edit modal, and delete Yes/No confirmation. `ProductDeletionLog` audit entity. **No** search, deliveries, min-stock, or merchant stock masking yet. See **`CATprogress.txt`**. |
| **IPOS-SA-ORD** | Partial | `POST /api/orders`: stock decrement, price snapshot, discounts, credit limit, **ORD-US1** (merchants forced to own `merchantId`). `GET /api/orders` returns **all** orders for any authenticated user (merchant-scoped list not implemented). Invoices/payments/status workflow still to do. |
| **IPOS-SA-RPRT** | Stub | `ReportingPlaceholder.jsx`; `/api/reports/**` secured for manager/admin — **no** report controllers yet. |

---

## Documentation & Progress Reports

| File | Purpose |
|------|---------|
| **`RBAC.md`** (project root) | Role × package matrix, endpoint access, architecture notes. |
| **`ACCprogress.txt`** | Account management epic: user stories, implementation detail, tests, gaps. |
| **`CATprogress.txt`** | Catalogue / inventory epic: current vs remaining work (detailed). |

**Note:** `RBAC.md` is the live reference for roles × packages and URL rules. If its “Future work” checklist ever drifts from **`ACCprogress.txt`** / **`CATprogress.txt`**, treat the progress files as the detailed source of truth for story completion.

---

## Key API Endpoints (summary)

| Area | Method & path | Roles (typical) |
|------|----------------|-----------------|
| Auth | `POST /api/auth/login` | Public |
| Auth | `GET /api/auth/me`, `POST /api/auth/logout` | Authenticated |
| Merchant accounts | `POST /api/merchant-accounts` | ADMIN |
| Merchant profiles | `GET`/`PUT /api/merchant-profiles`, `GET /api/merchant-profiles/{userId}`, `POST /api/merchant-profiles/close-month` | MANAGER, ADMIN |
| Staff users | `/api/users/**` | ADMIN |
| Products | `GET /api/products` | Authenticated |
| Products | `POST`/`PUT`/`DELETE /api/products/**` | ADMIN |
| Orders | `/api/orders/**` | Authenticated |
| Reports | `/api/reports/**` | MANAGER, ADMIN (no handlers yet) |

Full detail: **`RBAC.md`** and `backend/.../SecurityConfig.java`.

---

## Backend Tests

JUnit 5 **unit** tests live under `backend/src/test/java/` — **`com.ipos.service.MerchantAccountServiceTest`** (20 tests: merchant creation, tier validation, fixed/flexible order math, credit limit, ORD-US1 isolation, `AccountStatus`, `inDefaultSince`, `StandingChangeLog` entity checks, suspended/in-default blocking) and **`com.ipos.cat.CatalogueCatTest`** (15 Mockito unit tests for CAT-US1–US4 + 5 WebMvc integration tests for POST/PUT/DELETE validation and happy paths).

Test profile (H2 in-memory, bootstrap off):

```bash
cd backend
mvn test "-Dspring.profiles.active=test"
```

On Windows PowerShell, keep the profile argument in quotes so `-D` is not parsed incorrectly.

---

## Project Structure

```
team-proj/
├── .gitignore
├── README.md
├── RBAC.md                          # Role x package access matrix (project root)
├── ACCprogress.txt                  # IPOS-SA-ACC progress & documentation
├── CATprogress.txt                  # IPOS-SA-CAT progress & backlog
├── backend/                          # Spring Boot
│   ├── pom.xml                      # Java 17; validation, security-test, H2 (test)
│   └── src/main/
│       ├── java/com/ipos/
│       │   ├── IposApplication.java
│       │   ├── config/
│       │   │   ├── WebConfig.java
│       │   │   └── DataBootstrap.java   # Seeds default users + merchant profile
│       │   ├── security/
│       │   │   ├── SecurityConfig.java  # RBAC rules, CSRF, CORS, session config
│       │   │   └── IposUserDetailsService.java  # Loads users for Spring Security
│       │   ├── dto/
│       │   │   ├── LoginRequest.java
│       │   │   ├── UserResponse.java    # Safe DTO (no password hash)
│       │   │   ├── CreateMerchantAccountRequest.java  # ACC-US1
│       │   │   ├── MerchantProfileResponse.java       # Profile DTO
│       │   │   ├── UpdateMerchantProfileRequest.java   # ACC-US6
│       │   │   └── CloseMonthRequest.java              # Flexible settlement
│       │   ├── entity/
│       │   │   ├── User.java
│       │   │   ├── Product.java
│       │   │   ├── Order.java           # pricing, discounts, totalDue, status
│       │   │   ├── OrderItem.java       # unitPriceAtOrder snapshot
│       │   │   ├── MerchantProfile.java # contact, credit, plans, standing, accountStatus, inDefaultSince
│       │   │   ├── MonthlyRebateSettlement.java  # Flexible month-close (settlement_year_month)
│       │   │   └── StandingChangeLog.java        # Audit: standing changes (ACC-US5)
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── ProductRepository.java
│       │   │   ├── OrderRepository.java / OrderItemRepository.java
│       │   │   ├── MerchantProfileRepository.java
│       │   │   ├── MonthlyRebateSettlementRepository.java
│       │   │   └── StandingChangeLogRepository.java
│       │   ├── service/
│       │   │   ├── UserService.java
│       │   │   ├── ProductService.java
│       │   │   ├── OrderService.java    # Discount + credit limit + standing logic
│       │   │   └── MerchantAccountService.java  # Account creation + month-close
│       │   └── controller/
│       │       ├── AuthController.java
│       │       ├── UserController.java  # Staff CRUD (admin only)
│       │       ├── MerchantAccountController.java   # POST create (admin only)
│       │       ├── MerchantProfileController.java   # GET/PUT + close-month
│       │       ├── ProductController.java
│       │       └── OrderController.java
│       └── resources/
│           └── application.properties
│   └── src/test/
│       ├── java/com/ipos/service/
│       │   └── MerchantAccountServiceTest.java   # JUnit 5 + Mockito
│       └── resources/
│           └── application-test.properties       # H2, ipos.bootstrap.enabled=false
└── frontend/                          # React + Vite
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── main.jsx                   # Entry point (wraps app with AuthProvider)
        ├── App.jsx                    # Root component with RBAC navigation
        ├── App.css
        ├── api.js                     # Centralized API calls (with auth)
        ├── Login.jsx                  # Username + password login form
        ├── Catalogue.jsx
        ├── OrderForm.jsx              # Extended: shows discount breakdown
        ├── ReportingPlaceholder.jsx   # Stub for IPOS-SA-RPRT
        ├── MerchantCreate.jsx         # Admin: create merchant accounts (ACC-US1)
        ├── MerchantManagement.jsx     # Manager+Admin: profiles, standing, month-close
        └── auth/
            ├── AuthContext.jsx        # Auth state, login/logout, CSRF, session
            └── rbac.js                # Role x package access matrix (+ MER package)
```

---

## Database Design

Tables are created automatically by Hibernate on first run.

```
┌──────────────┐       ┌──────────────┐
│    users     │       │   products   │
├──────────────┤       ├──────────────┤
│ id (PK)      │       │ id (PK)      │
│ name         │       │ description  │
│ username     │       │ price        │
│ password_hash│       │ availability │
│ role         │       │   _count     │
└──────┬───────┘       └──────┬───────┘
       │                      │
       │  ┌────────────────────────────┐
       ├──│     merchant_profiles      │
       │  ├────────────────────────────┤
       │  │ id (PK)                    │
       │  │ user_id (FK, unique)       │──→ (FK to users)
       │  │ contact_email, phone, addr │
       │  │ credit_limit               │
       │  │ discount_plan_type         │
       │  │ fixed_discount_percent     │
       │  │ flexible_tiers_json        │
       │  │ account_status             │  # INACTIVE | ACTIVE (ACC-US1)
       │  │ standing                   │  # NORMAL | IN_DEFAULT | SUSPENDED
       │  │ in_default_since           │  # For 30-day rule (ACC-US5)
       │  │ flexible_discount_credit   │
       │  │ cheque_rebate_pending      │
       │  └────────────────────────────┘
       │
       │  ┌────────────────────────────┐
       ├──│   standing_change_logs     │
       │  ├────────────────────────────┤
       │  │ id (PK)                    │
       │  │ merchant_id (FK)           │──→ users
       │  │ previous_standing          │
       │  │ new_standing               │
       │  │ changed_by_user_id (FK)    │──→ users
       │  │ changed_at                 │
       │  └────────────────────────────┘
       │
       │  ┌──────────────────────────┐
       ├──│         orders           │
       │  ├──────────────────────────┤
       │  │ id (PK)                  │
       │  │ merchant_id (FK)         │──→ (FK to users)
       │  │ status                   │
       │  │ placed_at                │
       │  │ gross_total              │
       │  │ fixed_discount_amount    │
       │  │ flexible_credit_applied  │
       │  │ total_due                │
       │  └──────────┬───────────────┘
       │             │
       │  ┌──────────┴───────────┐
       │  │     order_items      │
       │  ├──────────────────────┤
       │  │ id (PK)              │
       │  │ order_id (FK)        │──→ (FK to orders)
       │  │ product_id (FK)      │──→ (FK to products)
       │  │ quantity             │
       │  │ unit_price_at_order  │
       │  └──────────────────────┘
       │
       │  ┌──────────────────────────────────┐
       └──│  monthly_rebate_settlements      │
          ├──────────────────────────────────┤
          │ id (PK)                          │
          │ merchant_id (FK)                 │──→ (FK to users)
          │ settlement_year_month (UK with merchant_id) │
          │ computed_discount                │
          │ mode                             │
          │ settled_at                       │
          └──────────────────────────────────┘
```

---

## Prerequisite knowledge needed

- React: components, `useState`, `useEffect`, fetching data, controlled forms, conditional rendering, Context API.
- Spring Boot: `@RestController`, `@Service`, `@Repository`, `@Transactional`, request/response flow.
- Spring Security: authentication, session management, CSRF protection, RBAC.
- JPA/Hibernate: `@Entity`, `@Id`, `@ManyToOne`, `@OneToMany`, `ddl-auto`, primary and foreign keys.
- Jakarta Bean Validation: used on merchant account DTOs (`spring-boot-starter-validation`).
- Full stack: frontend → REST API → service → repository → MySQL.

Remaining work is tracked in **`CATprogress.txt`** (catalogue/inventory) and the **Reporting** stub; **`ACCprogress.txt`** summarises the account-management package status.
