# IPOS-SA — Pharmaceutical Stock & Order Management

A learning project for **Spring Boot (Java)**, **React (Vite)**, and **MySQL**. Phase 1 is a minimal MVP: users, product catalogue, and orders with stock reduction.

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
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)

---

## Tech Stack

| Layer    | Technology           | Version / notes      |
|----------|----------------------|----------------------|
| Frontend | React (Vite, JS)     | Node 20+ required    |
| Backend  | Spring Boot          | 3.4.x, Java 23       |
| Database | MySQL                | 8.x+                 |
| Build    | Maven                | 3.9+ or use `mvnw`   |

---

## Prerequisites

Install the following **before** running the app:

| Tool      | Purpose                          | Where to get it |
|-----------|-----------------------------------|-----------------|
| **Java 23**       | Backend runs on JVM; Maven compiles with it | [Oracle](https://www.oracle.com/java/technologies/downloads/) or [Eclipse Temurin](https://adoptium.net/) |
| **Node.js 20+**   | Frontend build and dev server     | [nodejs.org](https://nodejs.org/) (LTS) or `winget install OpenJS.NodeJS.LTS` |
| **MySQL 8+**      | Database for users, products, orders | [MySQL](https://dev.mysql.com/downloads/) or a packaged install (e.g. XAMPP) |
| **Maven 3.9+**    | Build and run backend             | [Maven](https://maven.apache.org/download.cgi) or use the `mvnw` wrapper in `backend/` |

- **Node:** Vite 6 needs Node 18+ (Node 20 LTS recommended). On Windows, if `npm` scripts are blocked, run:  
  `Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser`

- **Java:** If you have any issues surrounding java just let me know on whatsapp
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

   If you use the `root` user, set the password you use to connect to MySQL (e.g. with MySQL Workbench or the command line). The default in the project is `root` / `root`; change both if your setup differs.

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

2. Run the application:

   **Windows (PowerShell):**

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   **macOS / Linux:**

   ```bash
   ./mvnw spring-boot:run
   ```

3. Wait until you see something like:

   ```
   Started IposApplication in X.XXX seconds
   Tomcat started on port 8080
   ```

4. The API is available at **http://localhost:8080** (e.g. `http://localhost:8080/api/products`).

If you see **"release version 23 not supported"**, your Maven is using a JDK that doesn't support Java 23. Either set `java.version` in `backend/pom.xml` to `17`, or install JDK 23 and set `JAVA_HOME` to it.

If you see **"Access denied for user 'root'@'localhost'"**, the username or password in `application.properties` does not match your MySQL setup. Fix the two properties and try again.

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
- See `docs/RBAC.md` for the full role x package access matrix.

---

## Running the Application

1. **Start MySQL** (if not running as a service).
2. **Start the backend:**  
   `cd backend` then `.\mvnw.cmd spring-boot:run` (Windows) or `./mvnw spring-boot:run` (macOS/Linux).
3. **Start the frontend:**  
   `cd frontend` then `npm run dev`.
4. Open **http://localhost:5173** in your browser.
5. On the Login screen, enter credentials (e.g., `admin` / `admin123`) and click **Log in**.
6. Navigation items are shown based on your role. Use **Catalogue** to view products and **Place Order** to create orders.

To place orders, you need products. Log in as admin and create them via the API:

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
  Navigation items are shown/hidden based on the user's role. Backend API endpoints are also protected. See `docs/RBAC.md` for full details.

---

## Project Structure

```
team-proj/
├── .gitignore
├── README.md
├── docs/
│   └── RBAC.md                      # Role x package access matrix & architecture
├── backend/                          # Spring Boot
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd               # Maven wrapper
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
│       │   │   ├── Order.java           # Extended: pricing, discounts, totalDue
│       │   │   ├── OrderItem.java       # Extended: unitPriceAtOrder
│       │   │   ├── MerchantProfile.java # ACC-US1: contact, credit, discount, standing
│       │   │   └── MonthlyRebateSettlement.java  # Flexible month-close records
│       │   ├── repository/
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

## Database Schema

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
       │  │ standing                   │
       │  │ flexible_discount_credit   │
       │  │ cheque_rebate_pending      │
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

## Learning

- React: components, `useState`, `useEffect`, fetching data, controlled forms, conditional rendering, Context API.
- Spring Boot: `@RestController`, `@Service`, `@Repository`, `@Transactional`, request/response flow.
- Spring Security: authentication, session management, CSRF protection, RBAC.
- JPA/Hibernate: `@Entity`, `@Id`, `@ManyToOne`, `@OneToMany`, `ddl-auto`, primary and foreign keys.
- Full stack: frontend → REST API → service → repository → MySQL.

Next steps will include meeting all the user stories that we must finish.
