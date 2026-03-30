/*
 * ╔══════════════════════════════════════════════════════════════════════════════╗
 * ║  WHAT: Service for sending emails — a REQUIRED interface used by IPOS-SA.  ║
 * ║                                                                              ║
 * ║  WHY:  IPOS-SA needs to notify merchants (e.g. order confirmation,         ║
 * ║        invoice dispatch, month-close settlement cheque notification).       ║
 * ║        This service is the boundary between IPOS-SA and an external         ║
 * ║        email subsystem.                                                     ║
 * ║                                                                              ║
 * ║  SYSTEM-TEST PERSPECTIVE:                                                   ║
 * ║        In system testing, this class represents the REQUIRED interface     ║
 * ║        that IPOS-SA uses from another subsystem (the email provider).      ║
 * ║        Tests T28-T34 verify the validation contract of this interface.     ║
 * ║                                                                              ║
 * ║  DESIGN-DOC TEST CASES: T28-T34 (sendEmail).                               ║
 * ║                                                                              ║
 * ║  VALIDATION RULES (from design-document test cases):                        ║
 * ║        T28: Valid email, subject, body → returns true.                     ║
 * ║        T29: Special characters in subject/body → returns true.             ║
 * ║        T30: Invalid email format → returns false.                          ║
 * ║        T31: Domain with consecutive dots (user@domain...com) → false.      ║
 * ║        T32: Empty 'to' address → throws ('Recipient email required').      ║
 * ║        T33: Null 'to' address → throws ('Recipient email cannot be null'). ║
 * ║        T34: Empty subject and body → returns false.                        ║
 * ╚══════════════════════════════════════════════════════════════════════════════╝
 */
package com.ipos.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class EmailService {

    /*
     * RFC 5321-simplified email pattern.
     * Accepts standard addresses (user@domain.com) and special characters in
     * the local part (e.g. Invoice#1*).  Rejects:
     *   - Missing @ or domain part.
     *   - Consecutive dots in the domain (e.g. domain...com).
     *   - Empty local part.
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"
    );

    /* Domain must not contain consecutive dots */
    private static final Pattern CONSECUTIVE_DOTS = Pattern.compile("\\.{2,}");

    /*
     * sendEmail — Send an email via the external email subsystem.
     *
     * @param to       Recipient email address.  Null → exception. Empty → exception.
     * @param subject  Email subject line.  Empty or null combined with empty body → false.
     * @param body     Email body text.
     * @return         true if the email was dispatched; false if input is invalid but
     *                 not null/empty (invalid format, blank subject+body).
     * @throws RuntimeException  If 'to' is null or empty (hard precondition violations).
     */
    public boolean sendEmail(String to, String subject, String body) {

        /* T33: Null recipient is a hard error (exception, not just false). */
        if (to == null) {
            throw new RuntimeException("Recipient email cannot be null.");
        }

        /* T32: Empty recipient is a hard error. */
        if (to.isBlank()) {
            throw new RuntimeException("Recipient email required.");
        }

        /* T30, T31: Invalid email format → return false (soft validation). */
        if (!EMAIL_PATTERN.matcher(to.trim()).matches()) {
            return false;
        }

        /* T31: Consecutive dots in domain → return false. */
        String domain = to.substring(to.indexOf('@') + 1);
        if (CONSECUTIVE_DOTS.matcher(domain).find()) {
            return false;
        }

        /* T34: Both subject and body empty → email has no content; return false. */
        boolean subjectBlank = (subject == null || subject.isBlank());
        boolean bodyBlank    = (body == null    || body.isBlank());
        if (subjectBlank && bodyBlank) {
            return false;
        }

        /*
         * T28, T29: Valid recipient + non-empty content → dispatch.
         * In production this would delegate to JavaMailSender or an SMTP gateway.
         * For the current implementation (and testing), we return true to confirm
         * the email would be sent.
         */
        return true;
    }
}
