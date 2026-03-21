/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Application startup runner that seeds default users.                 ║
 * ║                                                                              ║
 * ║  WHY:  When the application starts with a FRESH database, there are no      ║
 * ║        users — so nobody can log in, and the system is unusable.  This      ║
 * ║        runner creates one user per role (ADMIN, MANAGER, MERCHANT) with     ║
 * ║        known credentials so the team can start using the app immediately.   ║
 * ║                                                                              ║
 * ║  WHEN DOES IT RUN?                                                           ║
 * ║        Spring calls ApplicationRunner.run() ONCE after the application      ║
 * ║        context is fully initialized (all beans created, database ready).    ║
 * ║        It runs BEFORE the embedded Tomcat starts accepting requests.        ║
 * ║                                                                              ║
 * ║  SAFETY:                                                                     ║
 * ║        - Only runs if ipos.bootstrap.enabled=true in application.properties.║
 * ║        - Only creates users that DON'T already exist (checks by username). ║
 * ║        - Passwords are BCrypt-hashed before storage — never plaintext.      ║
 * ║        - In production, set ipos.bootstrap.enabled=false.                   ║
 * ║                                                                              ║
 * ║  DEFAULT CREDENTIALS (for development):                                      ║
 * ║        ┌──────────┬─────────────┬──────────┐                                ║
 * ║        │ Username │ Password    │ Role     │                                ║
 * ║        ├──────────┼─────────────┼──────────┤                                ║
 * ║        │ admin    │ admin123    │ ADMIN    │                                ║
 * ║        │ manager  │ manager123  │ MANAGER  │                                ║
 * ║        │ merchant │ merchant123 │ MERCHANT │                                ║
 * ║        └──────────┴─────────────┴──────────┘                                ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add more default users by adding entries to the bootstrapUsers     ║
 * ║          array below.                                                       ║
 * ║        - To seed default products too, inject ProductRepository and add     ║
 * ║          product creation logic after the user seeding.                     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.config;

import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/*
 * @Component — Registers this class as a Spring-managed bean.
 *   Spring detects that it implements ApplicationRunner and calls run()
 *   on startup.
 *
 * ApplicationRunner — A Spring Boot interface with a single run() method.
 *   It's the recommended way to execute code on startup that depends on
 *   the fully-initialized application context (unlike @PostConstruct
 *   which runs before all beans are ready).
 */
@Component
public class DataBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /*
     * @Value reads a property from application.properties.
     *
     * ${ipos.bootstrap.enabled:false}
     *   - "ipos.bootstrap.enabled" is the property key.
     *   - ":false" is the DEFAULT if the property is missing.
     *     This ensures bootstrap NEVER runs accidentally in production
     *     if someone forgets to set the property.
     */
    @Value("${ipos.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    public DataBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            System.out.println("[Bootstrap] Skipped — ipos.bootstrap.enabled is false.");
            return;
        }

        System.out.println("[Bootstrap] Checking for default users...");

        /*
         * Each entry: { displayName, username, rawPassword, role }
         *
         * Passwords here are in PLAINTEXT only for this array — they are
         * BCrypt-hashed before being stored in the database (see below).
         * These defaults are for DEVELOPMENT ONLY.
         */
        Object[][] bootstrapUsers = {
                {"Admin User",    "admin",    "admin123",    User.Role.ADMIN},
                {"Manager User",  "manager",  "manager123",  User.Role.MANAGER},
                {"Merchant User", "merchant", "merchant123", User.Role.MERCHANT},
        };

        for (Object[] userData : bootstrapUsers) {
            String displayName = (String) userData[0];
            String username    = (String) userData[1];
            String rawPassword = (String) userData[2];
            User.Role role     = (User.Role) userData[3];

            /*
             * Only create the user if the username doesn't already exist.
             * This prevents duplicate-key errors on subsequent restarts
             * and respects any manual changes to existing accounts.
             */
            if (userRepository.findByUsername(username).isEmpty()) {
                User user = new User(
                        displayName,
                        username,
                        passwordEncoder.encode(rawPassword),
                        role
                );
                userRepository.save(user);
                System.out.println("[Bootstrap] Created user: " + username
                        + " (role: " + role + ", password: " + rawPassword + ")");
            } else {
                System.out.println("[Bootstrap] User already exists: " + username + " — skipping.");
            }
        }

        System.out.println("[Bootstrap] Done.");
    }
}
