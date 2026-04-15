/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Application startup runner that seeds PDF sample users (IPOS-SA4).    ║
 * ║                                                                              ║
 * ║  WHY:  On a fresh database there are no users.  This seeds seven staff       ║
 * ║        accounts and three merchant accounts from IPOS_SampleData_2026_v1.1m  ║
 * ║        .pdf (merchants via MerchantAccountService — same rules as ACC-US1).   ║
 * ║                                                                              ║
 * ║  WHEN: Spring calls ApplicationRunner.run() once after context startup.       ║
 * ║                                                                              ║
 * ║  SAFETY:                                                                     ║
 * ║        - Only runs if ipos.bootstrap.enabled=true.                           ║
 * ║        - Only creates users whose username does not already exist.           ║
 * ║        - Staff passwords: plaintext only in the array below — BCrypt-hashed. ║
 * ║        - Merchants: createMerchantAccount hashes passwords.                   ║
 * ║        - No PU welcome-email relay here (relay runs from REST only).        ║
 * ║        - Set ipos.bootstrap.enabled=false in production.                     ║
 * ║                                                                              ║
 * ║  PDF staff (roles map to ADMIN / MANAGER only):                              ║
 * ║        ┌────────────┬───────────────────┬──────────┐                         ║
 * ║        │ Username   │ Password          │ App role ║                         ║
 * ║        ├────────────┼───────────────────┼──────────┤                         ║
 * ║        │ Sysdba     │ London_weighting  │ ADMIN    ║                         ║
 * ║        │ accountant │ Count_money       │ ADMIN    ║                         ║
 * ║        │ manager    │ Get_it_done       │ MANAGER  ║                         ║
 * ║        │ clerk      │ Paperwork         │ MANAGER  ║                         ║
 * ║        │ warehouse1 │ Get_a_beer        │ MANAGER  ║                         ║
 * ║        │ warehouse2 │ Lot_smell         │ MANAGER  ║                         ║
 * ║        │ delivery   │ Too_dark          │ MANAGER  ║                         ║
 * ║        └────────────┴───────────────────┴──────────┘                         ║
 * ║                                                                              ║
 * ║  PDF merchants (ACC0001–ACC0003 — contact email omitted in PDF; synthetic): ║
 * ║        city     / northampton  — CityPharmacy, FIXED 3%, £10,000             ║
 * ║        cosymed  / bondstreet   — Cosymed Ltd, FLEXIBLE tiers per PDF        ║
 * ║        hello    / there        — HelloPharmacy, FLEXIBLE (top tier 3%)      ║
 * ║        Email pattern: {username}@merchant.sample.ipos                        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.config;

import com.ipos.entity.MerchantProfile.DiscountPlanType;
import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import com.ipos.service.MerchantAccountService;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class DataBootstrap implements ApplicationRunner {

    /** IPOS_SampleData Cosymed — &lt;£1000: 0%; £1000–£2000: 1%; £2000+: 2% */
    private static final String PDF_COSYMED_FLEXIBLE_TIERS =
            "[{\"maxExclusive\":1000,\"percent\":0},{\"maxExclusive\":2000,\"percent\":1},{\"percent\":2}]";

    /** IPOS_SampleData HelloPharmacy — same bands; £2000+: 3% */
    private static final String PDF_HELLO_FLEXIBLE_TIERS =
            "[{\"maxExclusive\":1000,\"percent\":0},{\"maxExclusive\":2000,\"percent\":1},{\"percent\":3}]";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MerchantAccountService merchantAccountService;

    @Value("${ipos.bootstrap.enabled:false}")
    private boolean bootstrapEnabled;

    public DataBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            MerchantAccountService merchantAccountService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.merchantAccountService = merchantAccountService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!bootstrapEnabled) {
            System.out.println("[Bootstrap] Skipped — ipos.bootstrap.enabled is false.");
            return;
        }

        System.out.println("[Bootstrap] Checking for default staff users (PDF sample data)...");

        /*
         * Each entry: { displayName, username, rawPassword, role }
         * Passwords: plaintext here only — hashed before persistence.
         */
        Object[][] bootstrapUsers = {
                {"Administrator",           "Sysdba",     "London_weighting", User.Role.ADMIN},
                {"Senior accountant",       "accountant", "Count_money",      User.Role.ADMIN},
                {"Director of Operations",  "manager",    "Get_it_done",      User.Role.MANAGER},
                {"Accountant",              "clerk",      "Paperwork",        User.Role.MANAGER},
                {"Warehouse employee",      "warehouse1", "Get_a_beer",       User.Role.MANAGER},
                {"Warehouse employee",      "warehouse2", "Lot_smell",        User.Role.MANAGER},
                {"Delivery department",   "delivery",   "Too_dark",         User.Role.MANAGER},
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
            } else {
                System.out.println("[Bootstrap] User already exists: " + username + " — skipping.");
            }
        }

        System.out.println("[Bootstrap] Checking for PDF merchant accounts...");

        seedPdfMerchantIfMissing(
                "city",
                "CityPharmacy",
                "northampton",
                "Northampton Square, London EC1V 0HB",
                "0207 040 8000",
                new BigDecimal("10000"),
                DiscountPlanType.FIXED,
                new BigDecimal("3"),
                null);

        seedPdfMerchantIfMissing(
                "cosymed",
                "Cosymed Ltd",
                "bondstreet",
                "25, Bond Street, London WC1V 8LS",
                "0207 321 8001",
                new BigDecimal("5000"),
                DiscountPlanType.FLEXIBLE,
                null,
                PDF_COSYMED_FLEXIBLE_TIERS);

        seedPdfMerchantIfMissing(
                "hello",
                "HelloPharmacy",
                "there",
                "12, Bond Street, London WC1V 9NS",
                "0207 321 8002",
                new BigDecimal("5000"),
                DiscountPlanType.FLEXIBLE,
                null,
                PDF_HELLO_FLEXIBLE_TIERS);

        System.out.println("[Bootstrap] Checking for IPOS-CA integration merchant...");

        seedPdfMerchantIfMissing(
                "ca_merchant",
                "IPOS-CA Pharmacy",
                "ca_pass",
                "1 High Street, London EC1V 0HB",
                "0207 040 9999",
                new BigDecimal("50000"),
                DiscountPlanType.FIXED,
                new BigDecimal("0"),
                null);

        System.out.println("[Bootstrap] Done.");
    }

    private void seedPdfMerchantIfMissing(
            String username,
            String tradingName,
            String rawPassword,
            String addressLine,
            String phone,
            BigDecimal creditLimit,
            DiscountPlanType planType,
            BigDecimal fixedDiscountPercent,
            String flexibleTiersJson) {

        if (userRepository.findByUsername(username).isPresent()) {
            System.out.println("[Bootstrap] Merchant user already exists: " + username + " — skipping.");
            return;
        }

        String email = username + "@merchant.sample.ipos";
        merchantAccountService.createMerchantAccount(
                tradingName,
                username,
                rawPassword,
                email,
                phone,
                addressLine,
                creditLimit,
                planType,
                fixedDiscountPercent,
                flexibleTiersJson,
                null,
                null);

        System.out.println("[Bootstrap] Created merchant: " + username
                + " (" + tradingName + ", password: " + rawPassword + ")");
    }
}
