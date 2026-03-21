/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Application startup runner that seeds default users and profiles.    ║
 * ║                                                                              ║
 * ║  WHY:  When the application starts with a FRESH database, there are no      ║
 * ║        users — so nobody can log in, and the system is unusable.  This      ║
 * ║        runner creates one user per role (ADMIN, MANAGER, MERCHANT) with     ║
 * ║        known credentials so the team can start using the app immediately.   ║
 * ║                                                                              ║
 * ║  MERCHANT PROFILE (ACC-US1):                                                 ║
 * ║        The brief requires all merchants to have a MerchantProfile with      ║
 * ║        contact details, credit limit, and discount plan.  The bootstrap     ║
 * ║        creates a profile for the seeded "merchant" user so that login and   ║
 * ║        order placement work on a fresh database without manual setup.       ║
 * ║                                                                              ║
 * ║  WHEN DOES IT RUN?                                                           ║
 * ║        Spring calls ApplicationRunner.run() ONCE after the application      ║
 * ║        context is fully initialized.  It runs BEFORE Tomcat starts          ║
 * ║        accepting requests.                                                  ║
 * ║                                                                              ║
 * ║  SAFETY:                                                                     ║
 * ║        - Only runs if ipos.bootstrap.enabled=true in application.properties.║
 * ║        - Only creates users/profiles that DON'T already exist.              ║
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
 * ║        - Add more default users by adding entries to bootstrapUsers below. ║
 * ║        - To seed default products, inject ProductRepository and add         ║
 * ║          product creation logic after the user seeding.                     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.config;

import com.ipos.entity.MerchantProfile;
import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.MerchantProfile.MerchantStanding;
import com.ipos.entity.User;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class DataBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final MerchantProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ipos.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    public DataBootstrap(UserRepository userRepository,
                         MerchantProfileRepository profileRepository,
                         PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.profileRepository = profileRepository;
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
         * BCrypt-hashed before being stored in the database.
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

                /*
                 * ── MERCHANT PROFILE SEED (ACC-US1) ──────────────────────
                 *
                 * The brief requires every merchant to have a profile with
                 * contact details, credit limit, and discount plan.  Without
                 * this, the seeded merchant user couldn't place orders (the
                 * OrderService would reject them for having no profile).
                 *
                 * Demo values:
                 *   - Credit limit: £10,000
                 *   - Fixed discount: 5%
                 *   - Standing: NORMAL (can trade immediately)
                 */
                if (role == User.Role.MERCHANT) {
                    MerchantProfile profile = new MerchantProfile();
                    profile.setUser(user);
                    profile.setContactEmail("merchant@example.com");
                    profile.setContactPhone("07700 900000");
                    profile.setAddressLine("1 Demo Street, London, EC1A 1BB");
                    profile.setCreditLimit(new BigDecimal("10000.00"));
                    profile.setDiscountPlanType(DiscountPlanType.FIXED);
                    profile.setFixedDiscountPercent(new BigDecimal("5.00"));
                    profile.setStanding(MerchantStanding.NORMAL);
                    profile.setFlexibleDiscountCredit(BigDecimal.ZERO);
                    profile.setChequeRebatePending(BigDecimal.ZERO);
                    profileRepository.save(profile);
                    System.out.println("[Bootstrap] Created merchant profile for: " + username
                            + " (credit: £10,000, plan: FIXED 5%)");
                }
            } else {
                System.out.println("[Bootstrap] User already exists: " + username + " — skipping.");
            }
        }

        System.out.println("[Bootstrap] Done.");
    }
}
