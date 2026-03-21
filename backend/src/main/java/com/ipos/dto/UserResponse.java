/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A Data Transfer Object (DTO) for safe user responses.               ║
 * ║                                                                              ║
 * ║  WHY:  We NEVER want to send the User entity directly in auth responses     ║
 * ║        because it contains the passwordHash field.  Even though we use      ║
 * ║        @JsonIgnore on passwordHash, using a dedicated DTO is a              ║
 * ║        BELT-AND-SUSPENDERS approach:                                        ║
 * ║          - If someone accidentally removes @JsonIgnore, the DTO still      ║
 * ║            protects us because it simply doesn't have a hash field.         ║
 * ║          - DTOs decouple the API response shape from the database schema.  ║
 * ║            We can change the entity without breaking the frontend.          ║
 * ║                                                                              ║
 * ║  JAVA RECORDS:                                                               ║
 * ║        Records (Java 16+) are immutable data carriers.  The compiler        ║
 * ║        auto-generates: constructor, getters, equals(), hashCode(),          ║
 * ║        toString().  Perfect for DTOs since they should never be mutated.    ║
 * ║                                                                              ║
 * ║  USED BY:                                                                    ║
 * ║        - AuthController.login()  → returned after successful authentication ║
 * ║        - AuthController.me()     → returned to restore session on refresh   ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - ACC-US1: Add contact details, credit limit, account status.        ║
 * ║        - ACC-US2/US3: Add discount plan information.                        ║
 * ║        Keep this DTO thin — only include fields the frontend needs.         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

import com.ipos.entity.User;

/*
 * Record fields:
 *   id       — The database primary key.  The frontend uses this to identify
 *              the user (e.g., when placing orders as a specific merchant).
 *   name     — The display name shown in the UI header ("Logged in as Alice").
 *   username — The login identifier.  Shown in the login confirmation.
 *   role     — The user's role as a string ("ADMIN", "MANAGER", "MERCHANT").
 *              The frontend uses this to decide which navigation items to show
 *              (see frontend/src/auth/rbac.js for the access matrix).
 */
public record UserResponse(Long id, String name, String username, String role) {

    /*
     * Factory method to convert a User entity into a safe UserResponse.
     *
     * This is the ONLY place where entity → DTO conversion happens for auth
     * responses.  Having a single conversion point means:
     *   - If we add a field to UserResponse, we update ONE place.
     *   - If we rename a User field, we update ONE place.
     *
     * @param user  The User entity loaded from the database.
     * @return      A UserResponse with no sensitive data (no password hash).
     */
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getRole().name()
        );
    }
}
