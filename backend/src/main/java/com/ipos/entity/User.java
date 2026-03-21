/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: This is a JPA Entity class representing the "users" database table.  ║
 * ║                                                                              ║
 * ║  WHY:  In Spring Data JPA, an Entity is a plain Java class that maps         ║
 * ║        directly to a database table.  Each instance of this class is one     ║
 * ║        row in the table.  JPA (via Hibernate) handles all the SQL for us.    ║
 * ║                                                                              ║
 * ║  AUTHENTICATION FIELDS (added for ACC-US4 — Role-Based Access Control):     ║
 * ║        - username:     Unique login identifier for the user.                 ║
 * ║        - passwordHash: BCrypt-hashed password.  NEVER stored as plaintext.   ║
 * ║          We use @JsonIgnore so Jackson NEVER serialises this into JSON       ║
 * ║          responses — the password hash must never leave the server.          ║
 * ║                                                                              ║
 * ║  ROLES (ACC-US4):                                                            ║
 * ║        - ADMIN:    Full access to ALL packages (IPOS-SA-ACC, CAT, ORD, RPRT)║
 * ║        - MANAGER:  Access to IPOS-SA-RPRT (Reporting) and merchant settings.║
 * ║        - MERCHANT: Access to IPOS-SA-CAT (Catalogue) and IPOS-SA-ORD       ║
 * ║                    (Orders) only.                                            ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a new column: declare a new field (e.g., String email).         ║
 * ║          Hibernate will ALTER the table automatically (ddl-auto=update).     ║
 * ║        - Add a relationship: see Order.java for @ManyToOne examples.         ║
 * ║        - Future: ACC-US1 will add contact details, credit limit, discount   ║
 * ║          plan, and account status (Active/Inactive/In Default) fields.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * @Entity — Tells JPA: "this class maps to a database table."
 *           Hibernate will scan for all @Entity classes on startup and create
 *           or update their corresponding tables.
 *
 * @Table(name = "users") — By default, the table name would match the class
 *           name ("User"), but "user" is a RESERVED WORD in MySQL.
 *           We explicitly name it "users" to avoid SQL errors.
 */
@Entity
@Table(name = "users")
public class User {

    /*
     * @Id — Marks this field as the PRIMARY KEY of the table.
     *       A primary key uniquely identifies each row.  No two users can
     *       share the same id.
     *
     * @GeneratedValue(strategy = GenerationType.IDENTITY)
     *       Tells the database to auto-generate the id value using
     *       MySQL's AUTO_INCREMENT.  We never set the id manually;
     *       the database assigns it when we INSERT a new row.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /* The display name shown in the UI (e.g. "Alice Smith"). */
    private String name;

    /*
     * ── USERNAME (login credential) ──────────────────────────────────────────
     *
     * Used as the unique login identifier.  Spring Security's
     * UserDetailsService loads users by this field.
     *
     * @Column(unique = true, nullable = false)
     *   - unique = true  → Adds a UNIQUE CONSTRAINT in the database.
     *                       No two users can share the same username.
     *   - nullable = false → The column cannot be NULL in the database.
     *                        Every user MUST have a username to log in.
     *
     * WHY NOT USE EMAIL?
     *   The spec (ACC-US1) doesn't mandate email-based login.  Username is
     *   simpler for Phase 2.  A future iteration could add email as well.
     */
    @Column(unique = true, nullable = false)
    private String username;

    /*
     * ── PASSWORD HASH ────────────────────────────────────────────────────────
     *
     * Stores the BCrypt hash of the user's password.
     *
     * SECURITY RULES:
     *   1. Passwords are NEVER stored as plaintext — only BCrypt hashes.
     *   2. @JsonIgnore prevents Jackson from ever including this field in
     *      any JSON response.  If an API accidentally returns a User object,
     *      the hash is still hidden.
     *   3. The AuthController uses a dedicated UserResponse DTO that omits
     *      this field entirely — belt-and-suspenders protection.
     *
     * BCrypt automatically salts each hash, so identical passwords produce
     * different hashes.  This protects against rainbow-table attacks.
     *
     * @Column(nullable = false)
     *   Every user must have a password hash.  Accounts without passwords
     *   cannot exist (prevents accidental passwordless logins).
     */
    @JsonIgnore
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /*
     * @Enumerated(EnumType.STRING)
     *       By default, JPA stores enums as integers (0, 1, 2…).
     *       That is fragile — reordering the enum would silently change
     *       meanings.  EnumType.STRING stores the actual name ("ADMIN",
     *       "MERCHANT") as a VARCHAR in the database, which is much safer
     *       and easier to read when querying directly.
     */
    @Enumerated(EnumType.STRING)
    private Role role;

    /*
     * ── ROLE ENUM ────────────────────────────────────────────────────────────
     *
     * Defines the allowed user roles for the IPOS-SA system.
     * These map directly to the ACC-US4 acceptance criteria:
     *
     *   ADMIN    — Full access to ALL packages:
     *              IPOS-SA-ACC (Account Management)
     *              IPOS-SA-CAT (Catalogue)
     *              IPOS-SA-ORD (Orders)
     *              IPOS-SA-RPRT (Reporting)
     *
     *   MANAGER  — Access to:
     *              IPOS-SA-RPRT (Reporting)
     *              Merchant account settings (ACC-US5, ACC-US6)
     *              NOTE: Managers can also browse the catalogue (read-only)
     *              and view orders for oversight, but cannot place orders.
     *
     *   MERCHANT — Access to:
     *              IPOS-SA-CAT (Catalogue — read-only browsing)
     *              IPOS-SA-ORD (Orders — placing and tracking their own)
     *
     * HOW TO ADD A NEW ROLE:
     *   1. Add the enum value here (e.g., WAREHOUSE_STAFF).
     *   2. Update SecurityConfig.java to define what URLs the role can access.
     *   3. Update frontend/src/auth/rbac.js to add the role to the access matrix.
     *   4. Update docs/RBAC.md with the new role's permissions.
     */
    public enum Role {
        ADMIN,
        MANAGER,
        MERCHANT
    }

    /* JPA requires a no-arg constructor to create instances via reflection. */
    public User() {
    }

    /*
     * Convenience constructor for creating users with all required fields.
     * Used by the bootstrap runner (DataBootstrap.java) to seed initial users.
     */
    public User(String name, String username, String passwordHash, Role role) {
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    // JPA uses these to read/write field values.  Jackson (the JSON library)
    // also needs getters to serialize objects into JSON for API responses.
    // Note: getPasswordHash() is @JsonIgnore'd above, so Jackson skips it.

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
