/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller for authentication endpoints.                        ║
 * ║                                                                              ║
 * ║  WHY:  This controller is the FRONT DOOR for user authentication.           ║
 * ║        It handles three operations:                                         ║
 * ║          1. LOGIN  — Verify credentials and create an HTTP session.         ║
 * ║          2. LOGOUT — Invalidate the HTTP session.                           ║
 * ║          3. ME     — Return the currently authenticated user (for session   ║
 * ║                      restoration on page refresh).                          ║
 * ║                                                                              ║
 * ║  SESSION-BASED AUTH FLOW:                                                    ║
 * ║        1. Frontend sends POST /api/auth/login with { username, password }.  ║
 * ║        2. We call AuthenticationManager.authenticate() which:               ║
 * ║           a) Loads user via IposUserDetailsService.loadUserByUsername().    ║
 * ║           b) Compares password with BCrypt hash.                           ║
 * ║           c) Returns an Authentication token if valid.                     ║
 * ║        3. We store the Authentication in the SecurityContext, which is      ║
 * ║           backed by an HTTP session (JSESSIONID cookie).                   ║
 * ║        4. All subsequent requests include the JSESSIONID cookie            ║
 * ║           automatically — the user stays authenticated until logout.       ║
 * ║                                                                              ║
 * ║  WHY NOT JWT?                                                                ║
 * ║        Session-based auth is simpler for this project:                      ║
 * ║          - No token refresh logic needed.                                   ║
 * ║          - Logout is instant (invalidate session).  With JWT, you need     ║
 * ║            a token blocklist or short expiry + refresh tokens.              ║
 * ║          - The Vite proxy forwards cookies transparently.                   ║
 * ║        JWT is documented as a future option in docs/RBAC.md.               ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add POST /api/auth/register for self-registration.                ║
 * ║        - Add POST /api/auth/change-password for password updates.          ║
 * ║        - Add rate limiting to prevent brute-force login attempts.          ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.dto.LoginRequest;
import com.ipos.dto.UserResponse;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.User;
import com.ipos.repository.MerchantProfileRepository;
import com.ipos.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          MerchantProfileRepository merchantProfileRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.merchantProfileRepository = merchantProfileRepository;
    }

    private UserResponse buildUserResponse(User user) {
        if (user.getRole() == User.Role.MERCHANT) {
            return merchantProfileRepository.findByUserId(user.getId())
                    .map(profile -> UserResponse.fromEntity(user, profile.getDebtReminderOutstanding()))
                    .orElse(UserResponse.fromEntity(user));
        }
        return UserResponse.fromEntity(user);
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  POST /api/auth/login — Authenticate and create session
     * ══════════════════════════════════════════════════════════════════════════
     *
     * FLOW:
     *   1. Receive { "username": "...", "password": "..." } from frontend.
     *   2. Create an unauthenticated token (username + raw password).
     *   3. Pass it to AuthenticationManager which:
     *      a) Calls IposUserDetailsService.loadUserByUsername(username).
     *      b) Uses BCryptPasswordEncoder to compare raw password vs stored hash.
     *      c) Returns authenticated token if valid; throws AuthenticationException if not.
     *   4. Store the authenticated token in the SecurityContext.
     *   5. Save the SecurityContext to the HTTP session.
     *      This creates the JSESSIONID cookie that the browser sends on
     *      all subsequent requests.
     *   6. Return the user's safe details (no password hash) as JSON.
     *
     * SECURITY NOTES:
     *   - This endpoint is permitted without authentication (see SecurityConfig).
     *   - CSRF is disabled for this endpoint (no session exists yet to protect).
     *   - The raw password is NEVER logged, stored, or returned.
     *
     * @param request     The login credentials (username + password).
     * @param httpRequest The raw HTTP request — needed to create/access the session.
     * @return 200 with UserResponse on success; 401 with error message on failure.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest) {
        try {
            /*
             * Step 1: Create an unauthenticated token.
             * This is just a container for the credentials — no verification yet.
             */
            UsernamePasswordAuthenticationToken token =
                    new UsernamePasswordAuthenticationToken(
                            request.username(), request.password());

            /*
             * Step 2: Authenticate.
             * This is where the actual password check happens.  If the password
             * is wrong, AuthenticationException is thrown and we go to catch.
             */
            Authentication authentication =
                    authenticationManager.authenticate(token);

            /*
             * Step 3: Store authentication in the SecurityContext.
             * The SecurityContext holds the "who is currently logged in" info.
             * It's thread-local, so each request has its own context.
             */
            SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
            securityContext.setAuthentication(authentication);
            SecurityContextHolder.setContext(securityContext);

            /*
             * Step 4: Save the SecurityContext to the HTTP session.
             * This creates the JSESSIONID cookie.  On future requests, Spring
             * Security reads this session and restores the SecurityContext
             * automatically — so the user stays logged in.
             *
             * httpRequest.getSession(true) creates a new session if one
             * doesn't exist yet.
             */
            HttpSession session = httpRequest.getSession(true);
            session.setAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                    securityContext);

            /*
             * Step 5: Look up the full User entity to build the response.
             * authentication.getName() returns the username (from UserDetails).
             */
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found after authentication"));

            return ResponseEntity.ok(buildUserResponse(user));

        } catch (AuthenticationException e) {
            /*
             * Authentication failed — wrong username or password.
             * Return 401 with a generic message.
             *
             * SECURITY: We intentionally DON'T say whether the username or
             * password was wrong.  Revealing "username not found" vs "wrong
             * password" helps attackers enumerate valid usernames.
             */
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid username or password."));
        }
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  POST /api/auth/logout — Invalidate session
     * ══════════════════════════════════════════════════════════════════════════
     *
     * Destroys the HTTP session (and the JSESSIONID cookie).
     * After this, the browser's session cookie is invalid and any request
     * will get 401 Unauthorized until the user logs in again.
     *
     * NOTE: This endpoint requires authentication (see SecurityConfig).
     * An unauthenticated user can't "log out" because they're not logged in.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest httpRequest) {
        /*
         * getSession(false) returns the existing session or null if none exists.
         * We don't create a new session just to destroy it.
         */
        HttpSession session = httpRequest.getSession(false);
        if (session != null) {
            session.invalidate();
        }

        /* Clear the SecurityContext so the current thread is no longer authenticated. */
        SecurityContextHolder.clearContext();

        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    /*
     * ══════════════════════════════════════════════════════════════════════════
     *  GET /api/auth/me — Return current authenticated user
     * ══════════════════════════════════════════════════════════════════════════
     *
     * PURPOSE:
     *   When the React app loads (or the user refreshes the page), it calls
     *   GET /api/auth/me to check "am I still logged in?"
     *
     *   - If the JSESSIONID cookie is valid → returns the user's details.
     *     The frontend restores the logged-in state without showing login.
     *   - If the cookie is expired/missing → Spring Security returns 401
     *     (handled by SecurityConfig's authenticationEntryPoint).
     *     The frontend shows the login screen.
     *
     *   This is how login SURVIVES page refresh — the session cookie persists
     *   in the browser, and this endpoint converts it back to user data.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me() {
        /*
         * SecurityContextHolder.getContext().getAuthentication() returns the
         * Authentication object for the current request.  Because this endpoint
         * requires authentication (SecurityConfig), we're guaranteed to have one.
         *
         * .getName() returns the username (set by IposUserDetailsService).
         */
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Authenticated user not found in database"));

        return ResponseEntity.ok(buildUserResponse(user));
    }
}
