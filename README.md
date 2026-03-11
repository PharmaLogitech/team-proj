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
- [Creating Your First Users](#creating-your-first-users)
- [Running the Application](#running-the-application)
- [Login and Where Data Is Stored](#login-and-where-data-is-stored)
- [Project Structure](#project-structure)
- [Database Schema](#database-schema)
- [Publishing to GitHub](#publishing-to-github)

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

If you see **"release version 23 not supported"**, your Maven is using a JDK that doesn’t support Java 23. Either set `java.version` in `backend/pom.xml` to `17`, or install JDK 23 and set `JAVA_HOME` to it.

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

If **Node is too old** (e.g. Node 17), you’ll see errors about `node:fs/promises` or similar. Install Node 20 LTS or newer.

On **Windows**, if you get a PowerShell error about scripts not being signed when running `npm run dev`, run:

```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Then try `npm run dev` again.

---

## Creating Your First Users

The app shows a **Login** screen that lists users from the database. If the list is empty, you’ll see:

> No users in the system. Create a user via the API (POST /api/users) first.

Create at least one user (and optionally one per role) before using the app.

### Option A: PowerShell (Windows)

With the **backend running** on port 8080:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -ContentType "application/json" -Body '{"name":"Admin User","role":"ADMIN"}'
Invoke-RestMethod -Uri "http://localhost:8080/api/users" -Method Post -ContentType "application/json" -Body '{"name":"Merchant One","role":"MERCHANT"}'
```

### Option B: curl

```bash
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"name\":\"Admin User\",\"role\":\"ADMIN\"}"
curl -X POST http://localhost:8080/api/users -H "Content-Type: application/json" -d "{\"name\":\"Merchant One\",\"role\":\"MERCHANT\"}"
```

### Option C: Postman or Insomnia

- **Method:** POST  
- **URL:** `http://localhost:8080/api/users`  
- **Header:** `Content-Type: application/json`  
- **Body (raw JSON):**  
  `{"name":"Admin User","role":"ADMIN"}`  
  or `{"name":"Merchant One","role":"MERCHANT"}`  

After creating users, refresh the frontend; the Login screen will show a dropdown of users and you can log in.

---

## Running the Application

1. **Start MySQL** (if not running as a service).
2. **Start the backend:**  
   `cd backend` → `.\mvnw.cmd spring-boot:run` (Windows) or `./mvnw spring-boot:run` (macOS/Linux).
3. **Start the frontend:**  
   `cd frontend` → `npm run dev`.
4. Open **http://localhost:5173** in your browser.
5. On the Login screen, choose a user and click **Log in**.
6. Use **Catalogue** to view products and **Place Order** to create orders (stock is reduced when an order is placed).

To place orders, you need products. Create them via the API if the table is empty:

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/products" -Method Post -ContentType "application/json" -Body '{"description":"Sample Product","price":19.99,"availabilityCount":100}'
```

---

## Login and Where Data Is Stored

- **Phase 1 has no passwords.** The Login screen simply lets you “choose who you are” from the list of users in the database.

- **User accounts (who can log in):**  
  Stored in **MySQL**, in the **`users`** table. The backend exposes them via `GET /api/users` and creates them via `POST /api/users`. The Login page calls `GET /api/users` and shows them in a dropdown.

- **Who is logged in right now:**  
  Stored only in **React state** in the browser (`currentUser` in `App.jsx`). It is **not** saved to the database or to cookies. Refreshing the page or closing the tab clears it and shows the Login screen again.

---

## Project Structure

```
team-proj/
├── .gitignore
├── README.md
├── backend/                        # Spring Boot
│   ├── pom.xml
│   ├── mvnw, mvnw.cmd              # Maven wrapper
│   └── src/main/
│       ├── java/com/ipos/
│       │   ├── IposApplication.java
│       │   ├── config/WebConfig.java
│       │   ├── entity/             # User, Product, Order, OrderItem
│       │   ├── repository/
│       │   ├── service/
│       │   └── controller/
│       └── resources/
│           └── application.properties
└── frontend/                       # React + Vite
    ├── package.json
    ├── vite.config.js
    ├── index.html
    └── src/
        ├── main.jsx
        ├── App.jsx
        ├── App.css
        ├── api.js
        ├── Login.jsx
        ├── Catalogue.jsx
        └── OrderForm.jsx
```

---

## Database Schema

Tables are created automatically by Hibernate on first run.

```
┌──────────┐       ┌──────────────┐
│  users   │       │   products   │
├──────────┤       ├──────────────┤
│ id (PK)  │       │ id (PK)      │
│ name     │       │ description  │
│ role     │       │ price        │
└──────────┘       │ availability │
      │            │   _count     │
      │            └──────┬───────┘
      │                   │
      │  ┌────────────┐   │
      └──│   orders   │   │
         ├────────────┤   │
         │ id (PK)    │   │
         │ merchant_id│──→ (FK to users)
         │ status     │   │
         └─────┬──────┘   │
               │           │
         ┌─────┴────────┐  │
         │ order_items  │  │
         ├──────────────┤  │
         │ id (PK)      │  │
         │ order_id (FK)│──┘
         │ product_id   │──→ (FK to products)
         │ quantity     │
         └──────────────┘
```

---

## Publishing to GitHub

Follow these steps to put the project under Git and push it to GitHub.

### 1. Initialize Git (if not already)

In the project root (`team-proj`):

```bash
git init
```

### 2. Add and commit files

```bash
git add .
git status
```

Check that `backend/target/`, `frontend/node_modules/`, and `frontend/dist/` are **not** listed (they should be ignored by `.gitignore`). If they appear, ensure `.gitignore` is present and correct.

```bash
git commit -m "Initial commit: IPOS-SA Phase 1 - Spring Boot, React, MySQL MVP"
```

### 3. Create a repository on GitHub

1. Go to [github.com](https://github.com) and sign in.
2. Click **New repository** (or **+** → **New repository**).
3. Choose a name (e.g. `ipos-sa` or `team-proj`).
4. Leave **Initialize with README** unchecked (you already have a README).
5. Click **Create repository**.

### 4. Connect the local repo and push

GitHub will show commands; use these (replace `YOUR_USERNAME` and `YOUR_REPO` with your GitHub username and repo name):

```bash
git remote add origin https://github.com/YOUR_USERNAME/YOUR_REPO.git
git branch -M main
git push -u origin main
```

If you use SSH:

```bash
git remote add origin git@github.com:YOUR_USERNAME/YOUR_REPO.git
git branch -M main
git push -u origin main
```

### 5. After the first push

- **Never commit** `application.properties` if you put real passwords in it. Prefer environment variables or a local override file that is in `.gitignore`. For learning, many leave a placeholder like `root`/`root` and document that users must change it.
- For future changes:  
  `git add .` → `git commit -m "Your message"` → `git push`

---

## Learning Outcomes (Phase 1)

- React: components, `useState`, `useEffect`, fetching data, controlled forms, conditional rendering.
- Spring Boot: `@RestController`, `@Service`, `@Repository`, `@Transactional`, request/response flow.
- JPA/Hibernate: `@Entity`, `@Id`, `@ManyToOne`, `@OneToMany`, `ddl-auto`, primary and foreign keys.
- Full stack: frontend → REST API → service → repository → MySQL.

Next steps could include: real authentication (e.g. JWT), role-based access, and dashboards.
