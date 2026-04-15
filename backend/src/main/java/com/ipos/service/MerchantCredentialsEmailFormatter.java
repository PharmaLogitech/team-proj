package com.ipos.service;

/**
 * Shared wording for IPOS-SA merchant login credentials (§3a approval email append + §3b PU relay).
 */
public final class MerchantCredentialsEmailFormatter {

    private MerchantCredentialsEmailFormatter() {
    }

    public static String newMerchantWelcomeEmailSubject() {
        return "InfoPharma — IPOS-SA merchant account";
    }

    /**
     * Full body for direct merchant onboarding (admin UI) — relayed to applicant via PU SMTP (§3b).
     */
    public static String newMerchantWelcomeEmailBody(String username, String plainPassword) {
        return "Your IPOS-SA merchant account has been created.\n\n"
                + credentialBlock(username, plainPassword);
    }

    public static String appendCredentialsToApprovalBody(String baseBody, String username, String plainPassword) {
        return baseBody + "\n\n" + credentialBlock(username, plainPassword);
    }

    private static String credentialBlock(String username, String plainPassword) {
        return "--- IPOS-SA merchant account ---\n"
                + "Log in to the IPOS-SA wholesale portal with the credentials below.\n"
                + "Username: " + username + "\n"
                + "Password: " + plainPassword + "\n"
                + "Please change your password after first login.\n";
    }
}
