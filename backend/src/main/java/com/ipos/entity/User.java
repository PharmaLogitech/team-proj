/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: This is a JPA Entity class representing the "users" database table.  ║
 * ║                                                                              ║
 * ║  WHY:  In Spring Data JPA, an Entity is a plain Java class that maps         ║
 * ║        directly to a database table.  Each instance of this class is one     ║
 * ║        row in the table.  JPA (via Hibernate) handles all the SQL for us.    ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add a new column: declare a new field (e.g., String email).         ║
 * ║          Hibernate will ALTER the table automatically (ddl-auto=update).     ║
 * ║        - Add a relationship: see Order.java for @ManyToOne examples.         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.entity;

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

    private String name;

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
     * This enum defines the allowed user roles for Phase 1.
     * ADMIN    — Can manage the product catalogue.
     * MERCHANT — Can browse products and place orders.
     */
    public enum Role {
        ADMIN,
        MERCHANT
    }

    /* JPA requires a no-arg constructor to create instances via reflection. */
    public User() {
    }

    public User(String name, Role role) {
        this.name = name;
        this.role = role;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────
    // JPA uses these to read/write field values.  Jackson (the JSON library)
    // also needs getters to serialize objects into JSON for API responses.

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

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
