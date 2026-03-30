/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: JUnit 5 system tests for EmailService.                              ║
 * ║                                                                              ║
 * ║  ROLE:  System testing — tests a REQUIRED interface used by IPOS-SA.       ║
 * ║         Per the brief: "Develop jUnit tests for 2 of the methods of one of ║
 * ║         the required interfaces used by your own subsystem to test the     ║
 * ║         access to another subsystem."                                      ║
 * ║                                                                              ║
 * ║         EmailService represents the boundary between IPOS-SA and an        ║
 * ║         external email/notification subsystem.  IPOS-SA uses this          ║
 * ║         interface to send order confirmations, invoices, and settlement     ║
 * ║         notifications to merchants.                                         ║
 * ║                                                                              ║
 * ║  COVERAGE (maps to High-Level Design test cases):                          ║
 * ║    Test 28: Valid email + content                  → returns true           ║
 * ║    Test 29: Special characters in subject/body    → returns true           ║
 * ║    Test 30: Invalid email format                  → returns false           ║
 * ║    Test 31: Domain with consecutive dots          → returns false           ║
 * ║    Test 32: Empty 'to' recipient                  → exception thrown        ║
 * ║    Test 33: Null 'to' recipient                   → exception thrown        ║
 * ║    Test 34: Empty subject and body                → returns false           ║
 * ║                                                                              ║
 * ║  HOW:  No mocks needed — EmailService is a pure logic class (no external  ║
 * ║        dependencies injected).  Tests instantiate it directly.             ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmailServiceTest {

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 28-29: sendEmail — success paths
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 28: Valid email format and content → returns true.
     *
     * Design-doc: sendEmail(to='user@gmail.com', subject='welcome', body='approved')
     *   Comment: Valid email format and content
     *   Expected: Success. Returns true. Email sent successfully.
     */
    @Test
    @DisplayName("T28 sendEmail: Valid email + subject + body — returns true")
    void sendEmail_validFormatAndContent_returnsTrue() {
        boolean result = emailService.sendEmail(
                "user@gmail.com", "welcome", "approved");

        assertTrue(result, "Valid email with content should return true");
    }

    /*
     * TEST 29: Valid email format with special characters in subject and body.
     *
     * Design-doc: sendEmail(to='user@gmail.com ', subject='Invoice#1*', body='Details attached')
     *   Comment: Valid format containing special characters
     *   Expected: Success. Returns true with email successfully sent.
     */
    @Test
    @DisplayName("T29 sendEmail: Special characters in subject/body — returns true")
    void sendEmail_specialCharactersInContent_returnsTrue() {
        boolean result = emailService.sendEmail(
                "user@gmail.com", "Invoice#1*", "Details attached");

        assertTrue(result, "Email with special characters in subject/body should return true");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 30-31: sendEmail — invalid email format (soft failures)
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 30: Invalid email format — returns false (no exception).
     *
     * Design-doc: sendEmail(to='invalidEmail', subject='Test', body='Body')
     *   Comment: Invalid email format
     *   Expected: Failure. Returns false. Email not sent.
     */
    @Test
    @DisplayName("T30 sendEmail: Invalid email format — returns false")
    void sendEmail_invalidEmailFormat_returnsFalse() {
        boolean result = emailService.sendEmail(
                "invalidEmail", "Test", "Body");

        assertFalse(result, "Invalid email format should return false, not throw");
    }

    /*
     * TEST 31: Domain with consecutive dots — returns false.
     *
     * Design-doc: sendEmail(to='user@domain...com', subject='Test', body='Body')
     *   Comment: Invalid email format (consecutive dots in domain)
     *   Expected: Failure. Returns false. Email not sent.
     */
    @Test
    @DisplayName("T31 sendEmail: Consecutive dots in domain — returns false")
    void sendEmail_consecutiveDotsInDomain_returnsFalse() {
        boolean result = emailService.sendEmail(
                "user@domain...com", "Test", "Body");

        assertFalse(result, "Email with consecutive dots in domain should return false");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TESTS 32-33: sendEmail — null/empty recipient (hard failures → exceptions)
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 32: Empty 'to' recipient — exception raised.
     *
     * Design-doc: sendEmail(to=' ', subject='Test', body='Body')
     *   Comment: Empty email for recipient
     *   Expected: Failure. Exception raised ('Recipient email required').
     */
    @Test
    @DisplayName("T32 sendEmail: Empty 'to' — 'Recipient email required' exception")
    void sendEmail_emptyRecipient_throwsRecipientEmailRequired() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                emailService.sendEmail("  ", "Test", "Body"));

        assertTrue(ex.getMessage().contains("Recipient email required"),
                "Exception must say 'Recipient email required'");
    }

    /*
     * TEST 33: Null 'to' recipient — exception raised.
     *
     * Design-doc: sendEmail(to=null, subject='Test', body='Body')
     *   Comment: Null email provided
     *   Expected: Failure. Exception raised ('Recipient email cannot be null').
     */
    @Test
    @DisplayName("T33 sendEmail: Null 'to' — 'Recipient email cannot be null' exception")
    void sendEmail_nullRecipient_throwsRecipientEmailCannotBeNull() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                emailService.sendEmail(null, "Test", "Body"));

        assertTrue(ex.getMessage().contains("cannot be null"),
                "Exception must say 'Recipient email cannot be null'");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  TEST 34: sendEmail — empty subject and body
    // ═════════════════════════════════════════════════════════════════════════

    /*
     * TEST 34: Empty subject and body — returns false.
     *
     * Design-doc: sendEmail(to='user@gmail.com', subject=' ', body=' ')
     *   Comment: Empty subject and body
     *   Expected: Failure. Returns false. Email not sent due to incomplete content.
     */
    @Test
    @DisplayName("T34 sendEmail: Empty subject and body — returns false")
    void sendEmail_emptySubjectAndBody_returnsFalse() {
        boolean result = emailService.sendEmail(
                "user@gmail.com", "  ", "  ");

        assertFalse(result, "Email with empty subject and body should return false");
    }
}
