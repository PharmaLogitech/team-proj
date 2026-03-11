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
 * ║  HOW TO EXTEND:                                                              ║
 * ║        - Add @GetMapping("/{id}") to fetch a single user.                  ║
 * ║        - Add @PutMapping("/{id}") to update a user.                        ║
 * ║        - Add @DeleteMapping("/{id}") to delete a user.                     ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.controller;

import com.ipos.entity.User;
import com.ipos.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/*
 * @RestController — Combines two annotations:
 *   @Controller  → Marks this class as a Spring MVC controller (handles HTTP).
 *   @ResponseBody → Every method's return value is serialized to JSON
 *                    automatically (instead of looking for an HTML template).
 *
 *   So when findAll() returns a List<User>, Spring converts it to a JSON array
 *   like: [{"id":1,"name":"Alice","role":"ADMIN"}, …]
 *
 * @RequestMapping("/api/users") — A prefix for all endpoints in this controller.
 *   Every @GetMapping / @PostMapping path is relative to this prefix.
 *   So @GetMapping means GET /api/users.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /*
     * @GetMapping — Maps HTTP GET requests to this method.
     *   GET /api/users  →  returns all users as a JSON array.
     *
     *   GET is the standard HTTP method for READING data.
     *   It should never modify anything on the server.
     */
    @GetMapping
    public List<User> findAll() {
        return userService.findAll();
    }

    /*
     * @PostMapping — Maps HTTP POST requests to this method.
     *   POST /api/users  →  creates a new user.
     *
     *   POST is the standard HTTP method for CREATING data.
     *
     * @RequestBody — Tells Spring: "take the JSON body of the HTTP request
     *   and deserialize (convert) it into a User Java object."
     *
     *   For example, the frontend sends:
     *     POST /api/users
     *     Content-Type: application/json
     *     { "name": "Alice", "role": "ADMIN" }
     *
     *   Spring reads that JSON and creates a User object with
     *   name="Alice" and role=ADMIN, then passes it to this method.
     */
    @PostMapping
    public User create(@RequestBody User user) {
        return userService.save(user);
    }
}
