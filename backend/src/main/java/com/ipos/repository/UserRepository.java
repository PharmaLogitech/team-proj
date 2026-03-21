/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: This is a Spring Data JPA Repository for the User entity.            ║
 * ║                                                                              ║
 * ║  WHY:  The Repository layer isolates ALL database access from the rest of    ║
 * ║        the application.  Services never write raw SQL — they call methods    ║
 * ║        on repositories instead.  This makes the code easier to test,         ║
 * ║        maintain, and swap databases if needed.                                ║
 * ║                                                                              ║
 * ║  AUTHENTICATION QUERIES:                                                     ║
 * ║        findByUsername(String) is used by IposUserDetailsService to load      ║
 * ║        the user during Spring Security's authentication flow.  When a        ║
 * ║        login request arrives, Spring calls loadUserByUsername(username),     ║
 * ║        which delegates to this repository method.                            ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Spring Data JPA generates SQL from method names automatically!        ║
 * ║        Just declare a method signature and Spring figures out the query:     ║
 * ║          - List<User> findByRole(User.Role role);  → WHERE role = ?         ║
 * ║          - Optional<User> findByName(String name); → WHERE name = ?         ║
 * ║        Full docs: https://docs.spring.io/spring-data/jpa/reference/         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.repository;

import com.ipos.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/*
 * @Repository — A specialization of @Component that tells Spring:
 *   "This bean does database work."  Spring registers it in the
 *   application context so it can be injected into services.
 *   (For JpaRepository interfaces, @Repository is technically optional
 *   because Spring Data auto-detects them, but we include it for clarity.)
 *
 * JpaRepository<User, Long>
 *   - User → the entity type this repository manages.
 *   - Long → the type of the entity's primary key (@Id field).
 *
 *   By extending JpaRepository you get ALL of these methods for free,
 *   with zero implementation code:
 *     save(entity)          — INSERT or UPDATE
 *     findById(id)          — SELECT … WHERE id = ?
 *     findAll()             — SELECT * FROM users
 *     deleteById(id)        — DELETE … WHERE id = ?
 *     count()               — SELECT COUNT(*) FROM users
 *     existsById(id)        — SELECT 1 WHERE id = ? (boolean check)
 *   …and many more.  You never write these yourself!
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /*
     * ── FIND BY USERNAME ─────────────────────────────────────────────────────
     *
     * Spring Data JPA auto-generates this query from the method name:
     *   SELECT * FROM users WHERE username = ?
     *
     * Returns Optional<User> because the username might not exist.
     * Optional forces the caller to handle the "not found" case explicitly
     * instead of risking a NullPointerException.
     *
     * USED BY:
     *   - IposUserDetailsService.loadUserByUsername() — for Spring Security
     *     authentication (login flow).
     *   - DataBootstrap — to check if a username already exists before
     *     seeding default users.
     */
    Optional<User> findByUsername(String username);
}
