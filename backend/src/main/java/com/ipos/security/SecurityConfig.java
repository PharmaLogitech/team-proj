/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Central Spring Security configuration for the IPOS-SA backend.       ║
 * ║                                                                              ║
 * ║  WHY:  This is the SINGLE SOURCE OF TRUTH for:                              ║
 * ║        1. Which URLs require authentication.                                ║
 * ║        2. Which roles can access which URLs (RBAC — ACC-US4).               ║
 * ║        3. How sessions, CSRF, and CORS are configured.                      ║
 * ║        4. Which password encoder is used (BCrypt).                           ║
 * ║                                                                              ║
 * ║  HOW SPRING SECURITY WORKS (simplified):                                    ║
 * ║        Every HTTP request passes through a FILTER CHAIN before reaching     ║
 * ║        any @RestController.  The SecurityFilterChain defined here tells     ║
 * ║        Spring Security what to do at each step:                             ║
 * ║                                                                              ║
 * ║        Request → CORS → CSRF → Authentication → Authorisation → Controller ║
 * ║                                                                              ║
 * ║        If any step fails (e.g., no valid session, wrong role), the request  ║
 * ║        is rejected BEFORE it ever reaches the controller.  This is why      ║
 * ║        security rules live here, not in individual controllers.             ║
 * ║                                                                              ║
 * ║  ROLE-BASED ACCESS CONTROL (ACC-US4):                                       ║
 * ║        ┌────────────┬──────────────────────────────────────────────┐         ║
 * ║        │ Role       │ Allowed Packages / URL Patterns             │         ║
 * ║        ├────────────┼──────────────────────────────────────────────┤         ║
 * ║        │ MERCHANT   │ /api/products (GET), /api/orders            │         ║
 * ║        │ MANAGER    │ /api/products (GET), /api/orders,           │         ║
 * ║        │            │ /api/reports, /api/users (merchant settings)│         ║
 * ║        │ ADMIN      │ ALL endpoints                               │         ║
 * ║        └────────────┴──────────────────────────────────────────────┘         ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - To add a new protected endpoint: add a requestMatchers() line      ║
 * ║          inside the authorizeHttpRequests block below.                       ║
 * ║        - To add method-level security (e.g., @PreAuthorize on a specific    ║
 * ║          controller method), enable @EnableMethodSecurity on this class.    ║
 * ║        - See docs/RBAC.md for the full role × package matrix.              ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/*
 * @Configuration — Marks this class as a source of bean definitions.
 *   Spring reads it on startup and registers all @Bean methods.
 *
 * @EnableWebSecurity — Activates Spring Security's web security support
 *   and provides the HttpSecurity builder used in securityFilterChain().
 *   Without this annotation, our SecurityFilterChain bean would be ignored.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  PASSWORD ENCODER BEAN
     * ══════════════════════════════════════════════════════════════════════════
     *
     * BCrypt is the industry-standard password hashing algorithm.
     * It automatically:
     *   - Generates a random SALT for each password (so identical passwords
     *     produce different hashes — protects against rainbow tables).
     *   - Uses a configurable COST FACTOR (default 10, meaning 2^10 = 1024
     *     rounds of hashing) — makes brute-force attacks slow.
     *   - Stores the salt inside the hash string itself, so no separate
     *     salt column is needed in the database.
     *
     * Spring Security uses this bean in two places:
     *   1. When creating users (DataBootstrap) — to hash the initial passwords.
     *   2. When authenticating (login) — to compare the submitted password
     *      against the stored hash.
     *
     * The encoder is declared as a @Bean so it can be injected anywhere.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  AUTHENTICATION MANAGER BEAN
     * ══════════════════════════════════════════════════════════════════════════
     *
     * The AuthenticationManager is the central interface for authentication.
     * Our AuthController calls authManager.authenticate(token) to verify
     * username + password.  Spring wires it to our IposUserDetailsService
     * and BCryptPasswordEncoder automatically.
     *
     * We expose it as a @Bean so we can inject it into AuthController.
     * Without this explicit bean, we'd have to use a different authentication
     * pattern (like httpBasic or formLogin), which doesn't suit our REST API.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  CORS CONFIGURATION
     * ══════════════════════════════════════════════════════════════════════════
     *
     * CORS (Cross-Origin Resource Sharing) controls which websites can call
     * our API.  During development:
     *   - Frontend runs on http://localhost:5173 (Vite dev server).
     *   - Backend runs on http://localhost:8080 (Spring Boot).
     *
     * These are DIFFERENT ORIGINS (different ports).  Without CORS config,
     * the browser blocks cross-origin requests for security.
     *
     * IMPORTANT: This replaces the old WebConfig.java CORS setup.  When
     * Spring Security is active, CORS must be configured WITHIN the security
     * filter chain — otherwise Spring Security's CORS filter runs BEFORE
     * our MVC CORS config and blocks the requests.
     *
     * .allowCredentials(true) is CRITICAL for session-based auth:
     *   It tells the browser to include cookies (like JSESSIONID and
     *   XSRF-TOKEN) in cross-origin requests.  Without this, the session
     *   cookie would be silently dropped and every request would appear
     *   unauthenticated.
     *
     * PRODUCTION NOTE:
     *   Replace "http://localhost:5173" with your actual production domain.
     *   Never use allowedOrigins("*") with credentials — browsers reject it.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  THE SECURITY FILTER CHAIN — THE HEART OF SECURITY CONFIG
     * ══════════════════════════════════════════════════════════════════════════
     *
     * This bean defines the complete security policy for ALL HTTP requests.
     * Spring Security evaluates rules TOP-TO-BOTTOM — the FIRST matching
     * rule wins.  Order matters!
     *
     * READING GUIDE:
     *   .cors(...)                → Cross-origin setup (see above).
     *   .csrf(...)                → Cross-Site Request Forgery protection.
     *   .authorizeHttpRequests()  → URL-level access control (RBAC).
     *   .exceptionHandling(...)   → What to return for 401/403 errors.
     *
     * For a complete walkthrough of Spring Security filter chains, see:
     * https://docs.spring.io/spring-security/reference/servlet/architecture.html
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            /*
             * ── CORS ─────────────────────────────────────────────────────────
             * Use the CorsConfigurationSource bean defined above.
             */
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            /*
             * ── CSRF (Cross-Site Request Forgery) Protection ─────────────────
             *
             * WHAT IS CSRF?
             *   A CSRF attack tricks a logged-in user's browser into making
             *   unwanted requests to our API.  Example: a malicious site
             *   includes <img src="http://our-api/api/orders?delete=all">
             *   which fires a GET request with the user's session cookie.
             *
             * HOW WE PROTECT AGAINST IT:
             *   Spring Security generates a random CSRF TOKEN and stores it
             *   in a cookie called XSRF-TOKEN.  For every state-changing
             *   request (POST, PUT, DELETE, PATCH), the frontend must:
             *     1. Read the XSRF-TOKEN cookie value.
             *     2. Send it back as an X-XSRF-TOKEN HTTP header.
             *   Spring Security compares the cookie and header — if they
             *   don't match, the request is rejected (403 Forbidden).
             *
             * CookieCsrfTokenRepository.withHttpOnlyFalse():
             *   Stores the CSRF token in a cookie that JavaScript CAN read
             *   (httpOnly=false).  This is the standard pattern for SPAs
             *   (Single Page Applications) where the frontend is JavaScript.
             *
             * CsrfTokenRequestAttributeHandler:
             *   Required in Spring Security 6+ to ensure the CSRF token is
             *   eagerly loaded into request attributes.  Without this, the
             *   cookie might not be set on the first response.
             *
             * SECURITY NOTE:
             *   This is safe because the Same-Origin Policy prevents other
             *   sites from reading our cookies.  Only JavaScript running on
             *   our origin (localhost:5173) can read XSRF-TOKEN.
             *
             * We exempt /api/auth/login from CSRF because the user has no
             * session yet when logging in (no cookie to protect).
             */
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/auth/login")
            )

            /*
             * ── URL-LEVEL AUTHORISATION (ACC-US4 — RBAC) ────────────────────
             *
             * Rules are evaluated TOP-TO-BOTTOM.  First match wins.
             *
             * The pattern is:
             *   requestMatchers(method, url).hasRole("X")
             *     → Only users with ROLE_X authority can access this URL+method.
             *
             *   requestMatchers(url).authenticated()
             *     → Any logged-in user can access, regardless of role.
             *
             *   requestMatchers(url).permitAll()
             *     → No authentication needed (public endpoint).
             *
             * ┌─────────────────────────┬──────────────────────────────────────┐
             * │ Endpoint                │ Access Rule                          │
             * ├─────────────────────────┼──────────────────────────────────────┤
             * │ POST /api/auth/login    │ Public (no session yet)             │
             * │ POST /api/auth/logout   │ Authenticated (any role)            │
             * │ GET  /api/auth/me       │ Authenticated (any role)            │
             * │ /api/users/**           │ ADMIN only (IPOS-SA-ACC)           │
             * │ GET  /api/products      │ Authenticated (all roles can read) │
             * │ POST /api/products      │ ADMIN only (catalogue management)  │
             * │ /api/orders/**          │ Authenticated (all roles)           │
             * │ /api/reports/**         │ MANAGER or ADMIN (IPOS-SA-RPRT)   │
             * │ Everything else         │ Authenticated                       │
             * └─────────────────────────┴──────────────────────────────────────┘
             *
             * FUTURE WORK:
             *   - ORD-US1: Restrict merchants to their own orders only
             *     (needs method-level @PreAuthorize or service-layer checks).
             *   - ACC-US5: Managers need access to merchant account settings
             *     (add /api/users/{id}/settings with MANAGER role).
             *   - ACC-US6: Managers can alter credit limits and discount plans.
             */
            .authorizeHttpRequests(auth -> auth
                /* Public: login endpoint (no session exists yet). */
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()

                /* Authenticated: session management endpoints. */
                .requestMatchers("/api/auth/logout", "/api/auth/me").authenticated()

                /*
                 * ── MERCHANT ACCOUNT CREATION (ACC-US1) — ADMIN only ─────────
                 * POST /api/merchant-accounts creates a new merchant user +
                 * profile atomically.  Only admins can onboard merchants.
                 */
                .requestMatchers("/api/merchant-accounts/**").hasRole("ADMIN")

                /*
                 * ── MERCHANT PROFILE MANAGEMENT (ACC-US6, brief §iii) ────────
                 * GET/PUT /api/merchant-profiles and POST close-month.
                 * Managers can alter credit limits, discount plans, and
                 * standing (IN_DEFAULT → NORMAL | SUSPENDED).
                 * Admins also have access for full system oversight.
                 */
                .requestMatchers("/api/merchant-profiles/**").hasAnyRole("MANAGER", "ADMIN")

                /*
                 * IPOS-SA-ACC (Account Management) — ADMIN only.
                 * This covers staff user CRUD: creating admin/manager accounts.
                 */
                .requestMatchers("/api/users/**").hasRole("ADMIN")

                /*
                 * IPOS-SA-CAT (Catalogue) — Read access for all authenticated users.
                 * Merchants browse read-only (CAT-US6), Admins manage (CAT-US2/US4).
                 */
                .requestMatchers(HttpMethod.GET, "/api/products/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")

                /*
                 * IPOS-SA-ORD (Orders) — All authenticated users.
                 * Merchants place/track orders; Admins/Managers can view.
                 * ORD-US1 merchant isolation is enforced in OrderService
                 * (merchants are forced to their own ID).
                 */
                .requestMatchers("/api/orders/**").authenticated()

                /*
                 * IPOS-SA-RPRT (Reporting) — MANAGER and ADMIN only.
                 * These endpoints don't exist yet; this rule is a placeholder
                 * so when RPT-US1–US5 are implemented, they're already secured.
                 */
                .requestMatchers("/api/reports/**").hasAnyRole("MANAGER", "ADMIN")

                /* Default: require authentication for anything else. */
                .anyRequest().authenticated()
            )

            /*
             * ── EXCEPTION HANDLING ───────────────────────────────────────────
             *
             * By default, Spring Security redirects to /login when
             * unauthenticated.  That's for server-rendered apps.  Our React
             * SPA needs JSON error responses instead, so the frontend can
             * handle them programmatically.
             *
             * authenticationEntryPoint → Triggered when a request has NO
             *   valid session (401 Unauthorized).
             *
             * accessDeniedHandler → Triggered when a request HAS a valid
             *   session but the user's role is insufficient (403 Forbidden).
             */
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"error\":\"Not authenticated. Please log in.\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write(
                            "{\"error\":\"Access denied. Insufficient permissions.\"}");
                })
            );

        return http.build();
    }
}
