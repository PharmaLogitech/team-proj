/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: REST Controller that exposes User-related HTTP endpoints.            ║
 * ║                                                                              ║
 * ║  WHY:  Controllers are the FRONT DOOR of the backend.  Every HTTP request   ║
 * ║        from the React frontend hits a controller method first.  The          ║
 * ║        controller's job is simple:                                           ║
 * ║          1. Receive the HTTP request and parse its data.                     ║
 * ║          2. Delegate to a Service for business logic.                        ║
 * ║          3. Return the result as JSON.                                       ║
 * ║        Controllers should contain ZERO business logic.                       ║
 * ║                                                                              ║
 * ║  ACCESS CONTROL (ACC-US4 — RBAC):                                           ║
 * ║        ALL endpoints in this controller are restricted to ADMIN only.       ║
 * ║        This is enforced in SecurityConfig.java via:                         ║
 * ║          .requestMatchers("/api/users/**").hasRole("ADMIN")                ║
 * ║                                                                              ║
 * ║        Only Administrators can create users, list users, or manage          ║
 * ║        accounts.  Managers and Merchants will receive 403 Forbidden.        ║
 * ║                                                                              ║
 * ║  FUTURE WORK (ACC-US5, ACC-US6):                                            ║
 * ║        - Add endpoints for Managers to view/edit merchant settings           ║
 * ║          (credit limits, discount plans) at a different URL path             ║
 * ║          like /api/merchant-settings/{id} with MANAGER role access.         ║
 * ║        - Add PUT /api/users/{id} for updating user details.                ║
 * ║        - Add DELETE /api/users/{id} for deactivating accounts.             ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.dto.UserResponse;
import com.ipos.entity.User;
import com.ipos.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/*
 * @RestController — Combines two annotations:
 *   @Controller  → Marks this class as a Spring MVC controller (handles HTTP).
 *   @ResponseBody → Every method's return value is serialized to JSON
 *                    automatically (instead of looking for an HTML template).
 *
 * @RequestMapping("/api/users") — A prefix for all endpoints in this controller.
 *   Every @GetMapping / @PostMapping path is relative to this prefix.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /*
     * GET /api/users → Returns all users as a JSON array of UserResponse DTOs.
     *
     * SECURITY: ADMIN only (enforced by SecurityConfig).
     *
     * We return UserResponse DTOs (not raw User entities) to ensure password
     * hashes are NEVER included in the response.  Even though User.passwordHash
     * has @JsonIgnore, using DTOs is belt-and-suspenders security.
     */
    @GetMapping
    public List<UserResponse> findAll() {
        return userService.findAll().stream()
                .map(UserResponse::fromEntity)
                .toList();
    }

    /*
     * POST /api/users → Creates a new user.
     *
     * SECURITY: ADMIN only (enforced by SecurityConfig).
     *
     * Expected JSON body:
     * {
     *   "name": "Alice Smith",
     *   "username": "alice",
     *   "password": "securePassword123",
     *   "role": "MERCHANT"
     * }
     *
     * The password is hashed by UserService.createUser() before storage.
     * The response contains the created user WITHOUT the password hash.
     *
     * We use a Map<String, String> for the request body to keep it simple.
     * A dedicated CreateUserRequest DTO could be used in a future refactor.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, String> body) {
        try {
            String name     = body.get("name");
            String username = body.get("username");
            String password = body.get("password");
            String roleStr  = body.get("role");

            if (roleStr == null || roleStr.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Role is required (ADMIN, MANAGER, or MERCHANT)."));
            }

            User.Role role;
            try {
                role = User.Role.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Invalid role: '" + roleStr + "'. Must be ADMIN, MANAGER, or MERCHANT."));
            }

            User user = userService.createUser(name, username, password, role);
            return ResponseEntity.ok(UserResponse.fromEntity(user));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /*
     * PUT /api/users/{id}/role → Changes a user's role.
     *
     * SECURITY: ADMIN only (enforced by SecurityConfig).
     *
     * Expected JSON body:
     * {
     *   "role": "ADMIN"   // or "MANAGER"
     * }
     *
     * Only ADMIN ↔ MANAGER transitions are allowed.  MERCHANT users cannot
     * be changed from this endpoint (they have an associated MerchantProfile),
     * and assigning the MERCHANT role is also rejected.
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id,
                                        @RequestBody Map<String, String> body) {
        try {
            String roleStr = body.get("role");

            if (roleStr == null || roleStr.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Role is required (ADMIN or MANAGER)."));
            }

            User.Role role;
            try {
                role = User.Role.valueOf(roleStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error",
                                "Invalid role: '" + roleStr + "'. Must be ADMIN or MANAGER."));
            }

            User user = userService.updateUserRole(id, role);
            return ResponseEntity.ok(UserResponse.fromEntity(user));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
