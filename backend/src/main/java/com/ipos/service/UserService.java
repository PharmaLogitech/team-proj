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
 * ║  HOW TO EXTEND:                                                              ║
 * ║        Add methods like findByRole(Role role) and call the repository.      ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import com.ipos.entity.User;
import com.ipos.repository.UserRepository;
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

    /*
     * Constructor injection — Spring sees that this constructor needs a
     * UserRepository and automatically provides the one it created.
     * When there's only one constructor, @Autowired is optional (Spring
     * infers it), but the pattern is the same.
     */
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User save(User user) {
        return userRepository.save(user);
    }
}
