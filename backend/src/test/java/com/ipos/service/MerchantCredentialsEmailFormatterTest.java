package com.ipos.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantCredentialsEmailFormatterTest {

    @Test
    void newMerchantWelcomeEmailBody_containsCredentials() {
        String body = MerchantCredentialsEmailFormatter.newMerchantWelcomeEmailBody("merchant1", "Secret!1");
        assertThat(body).contains("merchant1").contains("Secret!1").contains("IPOS-SA");
    }

    @Test
    void appendCredentialsToApprovalBody_appendsBlock() {
        String out = MerchantCredentialsEmailFormatter.appendCredentialsToApprovalBody(
                "Hello", "u", "p");
        assertThat(out).startsWith("Hello").contains("Username: u").contains("Password: p");
    }
}
