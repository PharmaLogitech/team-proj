/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: This is a Service class for User-related business logic.             ║
 * ║                                                                              ║
 * ║  WHY:  The Service layer sits between Controllers and Repositories.          ║
 * ║                                                                              ║
 * ║          Controller  →  Service  →  Repository  →  Database                 ║
 * ║                                                                              ║
 * ║        Controllers should NEVER call repositories directly.  All business    ║
 * ║        rules, validations, and multi-step operations live in services.       ║
 * ║        This separation means you can reuse the same logic from different     ║
 * ║        controllers, scheduled jobs, or message listeners.                    ║
 * ║                                                                              ║
 * ║  AUTHENTICATION INTEGRATION:                                                 ║
 * ║        The createUser() method hashes passwords using BCrypt before saving. ║
 * ║        This ensures that passwords NEVER touch the database in plaintext.   ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4):                                                   ║
 * ║        The UserController restricts all endpoints to ADMIN only.  This      ║
 * ║        service does NOT enforce role checks — that's the controller's job   ║
 * ║        (via SecurityConfig URL rules).  The service handles business logic  ║
 * ║        like validation and password hashing.                                ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - ACC-US1: Add createMerchantAccount() with contact details,         ║
 * ║          credit limit, discount plan, and Active/Inactive status logic.     ║
 * ║        - ACC-US5: Add restoreFromDefault() for Manager-only status changes. ║
 * ║        - ACC-US6: Add updateCreditLimit() and updateDiscountPlan().         ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * @Service — A specialization of @Component.  It tells Spring:
 *   "This class contains business logic."  Spring creates a single
 *   instance (singleton) and makes it available for dependency injection.
 *
 *   When a controller declares:  private final UserService userService;
 *   Spring automatically injects THIS instance — that's called
 *   "constructor injection" and it's the recommended pattern.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /*
     * Constructor injection — Spring sees that this constructor needs a
     * UserRepository and PasswordEncoder, and automatically provides them.
     * The PasswordEncoder bean is defined in SecurityConfig.
     */
    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    /*
     * ── CREATE USER ──────────────────────────────────────────────────────────
     *
     * Creates a new user with a BCrypt-hashed password.
     *
     * IMPORTANT: The "rawPassword" parameter is the plaintext password from
     * the request body.  We hash it IMMEDIATELY and never store the raw value.
     *
     * VALIDATION:
     *   - Username must not be null or empty.
     *   - Password must not be null or empty.
     *   - Username must be unique (enforced by DB constraint + check here).
     *
     * @param name        The display name (e.g., "Alice Smith").
     * @param username    The login identifier (must be unique).
     * @param rawPassword The plaintext password (hashed before storage).
     * @param role        The role to assign (ADMIN, MANAGER, MERCHANT).
     * @return            The saved User entity (with generated ID).
     * @throws RuntimeException if the username already exists.
     */
    public User createUser(String name, String username, String rawPassword, User.Role role) {
        if (username == null || username.isBlank()) {
            throw new RuntimeException("Username is required.");
        }
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new RuntimeException("Password is required.");
        }

        /*
         * ACC-US1 guard: Merchant accounts MUST be created via
         * MerchantAccountService.createMerchantAccount() so that the
         * mandatory profile (contact details, credit limit, discount plan)
         * is created atomically.  Using this generic method would create a
         * User with role MERCHANT but no MerchantProfile — violating the
         * brief: "if the required details are not provided the account
         * will not be created."
         */
        if (role == User.Role.MERCHANT) {
            throw new RuntimeException(
                    "Merchant accounts must be created via the merchant account endpoint "
                    + "(POST /api/merchant-accounts) which requires contact details, "
                    + "credit limit, and discount plan.");
        }

        /* Check for duplicate username before hitting the DB constraint. */
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username '" + username + "' is already taken.");
        }

        User user = new User(
                name,
                username,
                passwordEncoder.encode(rawPassword),
                role
        );

        return userRepository.save(user);
    }

    /*
     * Raw save — used internally (e.g., DataBootstrap) when the password
     * is already hashed.  Controllers should use createUser() instead.
     */
    public User save(User user) {
        return userRepository.save(user);
    }
}
