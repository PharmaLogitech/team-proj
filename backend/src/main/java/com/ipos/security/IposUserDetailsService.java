/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Custom UserDetailsService implementation for Spring Security.        ║
 * ║                                                                              ║
 * ║  WHY:  Spring Security needs a way to load user credentials from OUR        ║
 * ║        database.  By default, Spring Security knows nothing about our       ║
 * ║        "users" table.  This class is the BRIDGE between our User entity     ║
 * ║        and Spring Security's authentication framework.                      ║
 * ║                                                                              ║
 * ║  HOW IT WORKS:                                                               ║
 * ║        1. A user submits username + password via POST /api/auth/login.      ║
 * ║        2. Spring Security calls loadUserByUsername(username) on THIS class. ║
 * ║        3. We look up the User in the database via UserRepository.           ║
 * ║        4. We return a Spring Security UserDetails object that contains:     ║
 * ║             - The username                                                  ║
 * ║             - The BCrypt password hash (Spring compares it automatically)   ║
 * ║             - The granted authorities (roles like ROLE_ADMIN)               ║
 * ║        5. Spring Security compares the submitted password against the hash. ║
 * ║           If they match → authentication succeeds → session is created.     ║
 * ║           If they don't → 401 Unauthorized is returned.                     ║
 * ║                                                                              ║
 * ║  ROLE MAPPING:                                                               ║
 * ║        Our User.Role enum (ADMIN, MANAGER, MERCHANT) is converted to       ║
 * ║        Spring Security authorities with the "ROLE_" prefix:                 ║
 * ║          ADMIN    → ROLE_ADMIN                                              ║
 * ║          MANAGER  → ROLE_MANAGER                                            ║
 * ║          MERCHANT → ROLE_MERCHANT                                           ║
 * ║                                                                              ║
 * ║        The "ROLE_" prefix is a Spring Security convention.  When we write   ║
 * ║        .hasRole("ADMIN") in SecurityConfig, Spring automatically checks     ║
 * ║        for the authority "ROLE_ADMIN".                                      ║
 * ║                                                                              ║
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - To add account locking (ACC-US5 "In Default"), add checks here    ║
 * ║          for account status and return UserDetails with                     ║
 * ║          accountNonLocked=false if the account is in default.               ║
 * ║        - To add password expiry, check a "passwordChangedAt" field.        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.security;

import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/*
 * @Service — Registers this class as a Spring-managed bean.
 *
 * Spring Security auto-detects beans that implement UserDetailsService.
 * When there is exactly ONE such bean, Spring Security uses it automatically
 * for authentication without any additional wiring.
 *
 * If you ever need multiple UserDetailsService implementations (unlikely),
 * you'd wire the correct one explicitly in SecurityConfig.
 */
@Service
public class IposUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /*
     * Constructor injection — Spring provides the UserRepository automatically.
     * We use it to look up users by username from the database.
     */
    public IposUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /*
     * ── loadUserByUsername ───────────────────────────────────────────────────
     *
     * This method is called by Spring Security EVERY TIME a user tries to
     * authenticate (log in).  It must:
     *   1. Find the user in the database by username.
     *   2. Return a UserDetails object with credentials and authorities.
     *   3. Throw UsernameNotFoundException if the user doesn't exist.
     *
     * Spring Security then compares the submitted password against the hash
     * stored in the UserDetails object using the configured PasswordEncoder
     * (BCrypt in our case).  We NEVER compare passwords ourselves.
     *
     * @param username  The username submitted in the login request.
     * @return          A UserDetails object that Spring Security uses for
     *                  password verification and authority checks.
     * @throws UsernameNotFoundException if no user with that username exists.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        /*
         * Look up the user in the database.  If not found, throw an exception.
         * Spring Security catches this and returns 401 Unauthorized.
         */
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No user found with username: " + username));

        /*
         * Convert our User entity into a Spring Security UserDetails object.
         *
         * org.springframework.security.core.userdetails.User (note: different
         * class from our com.ipos.entity.User) is a built-in implementation
         * of UserDetails.  We use its builder for clarity.
         *
         * .username()    → The login identifier.
         * .password()    → The BCrypt hash from the database.  Spring Security
         *                   will compare the raw password against this hash
         *                   using BCryptPasswordEncoder.matches().
         * .authorities() → The roles/permissions this user has.  We map our
         *                   single Role enum to a Spring Security authority
         *                   with the "ROLE_" prefix convention.
         *
         * FUTURE EXTENSION (ACC-US5 — Managing Defaulted Accounts):
         *   Add .accountNonLocked(user.getStatus() != AccountStatus.IN_DEFAULT)
         *   to block login for defaulted accounts.  Spring Security will
         *   automatically reject authentication with a LockedException.
         */
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                ))
                .build();
    }
}
