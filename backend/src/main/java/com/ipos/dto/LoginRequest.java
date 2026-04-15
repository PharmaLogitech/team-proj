/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: A DTO representing the login request body.                           ║
 * ║                                                                              ║
 * ║  WHY:  Instead of accepting raw Map<String, String> in the controller,      ║
 * ║        we define a typed record.  This gives us:                            ║
 * ║          - Compile-time safety (typos caught by the compiler).              ║
 * ║          - Auto-generated getters for Jackson deserialization.              ║
 * ║          - Clear API documentation (developers see exactly what's needed).  ║
 * ║                                                                              ║
 * ║  USED BY:                                                                    ║
 * ║        AuthController.login(@RequestBody LoginRequest request)              ║
 * ║                                                                              ║
 * ║  EXPECTED JSON:                                                              ║
 * ║        { "username": "Sysdba", "password": "London_weighting" }            ║
 * ║                                                                              ║
 * ║  SECURITY NOTE:                                                              ║
 * ║        The password field contains the RAW (plaintext) password submitted   ║
 * ║        by the user.  It is NEVER stored anywhere — Spring Security's        ║
 * ║        BCryptPasswordEncoder compares it against the stored hash in memory  ║
 * ║        and the raw value is discarded after authentication completes.       ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.dto;

public record LoginRequest(String username, String password) {
}
