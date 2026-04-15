package com.ipos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ipos.config.IntegrationPuProperties;
import com.ipos.entity.CommercialApplication;
import com.ipos.entity.CommercialApplicationStatus;
import com.ipos.entity.MerchantProfile;
import com.ipos.entity.User;
import com.ipos.repository.CommercialApplicationRepository;
import com.ipos.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CommercialApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CommercialApplicationService.class);

    private final CommercialApplicationRepository repository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final IntegrationPuProperties properties;
    private final RestClient integrationPuRestClient;
    private final MerchantAccountService merchantAccountService;

    private static final SecureRandom PASSWORD_RANDOM = new SecureRandom();
    private static final char[] PASSWORD_CHARS =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%".toCharArray();

    public CommercialApplicationService(
            CommercialApplicationRepository repository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            IntegrationPuProperties properties,
            RestClient integrationPuRestClient,
            MerchantAccountService merchantAccountService) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.integrationPuRestClient = integrationPuRestClient;
        this.merchantAccountService = merchantAccountService;
    }

    @Transactional
    public CommercialApplication submitFromPu(String externalReferenceId, JsonNode payload, String callbackUrl) {
        if (repository.findByExternalReferenceId(externalReferenceId).isPresent()) {
            throw new DuplicateExternalReferenceException(externalReferenceId);
        }
        CommercialApplication app = new CommercialApplication();
        app.setExternalReferenceId(externalReferenceId);
        try {
            app.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid payload", e);
        }
        if (callbackUrl != null && !callbackUrl.isBlank()) {
            app.setCallbackUrl(callbackUrl.trim());
        }
        return repository.save(app);
    }

    @Transactional(readOnly = true)
    public List<CommercialApplication> findAll(Optional<CommercialApplicationStatus> status) {
        if (status.isPresent()) {
            return repository.findByStatusOrderByCreatedAtDesc(status.get());
        }
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Optional<CommercialApplication> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Loads application with {@code decidedBy} initialized (for admin detail view).
     */
    @Transactional(readOnly = true)
    public Optional<CommercialApplication> findByIdForDetail(Long id) {
        return repository.findByIdWithDecidedBy(id);
    }

    @Transactional
    public DecisionResult approve(Long id, Optional<String> emailBodyOverride, String adminUsername) {
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new IllegalStateException("Admin user not found: " + adminUsername));
        CommercialApplication app = repository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        if (app.getStatus() != CommercialApplicationStatus.PENDING) {
            throw new InvalidStatusException("Application is not pending: " + app.getStatus());
        }
        String emailBody = emailBodyOverride
                .filter(s -> !s.isBlank())
                .orElseGet(() -> {
                    try {
                        return buildApprovalEmailBody(app.getExternalReferenceId(), app.getPayloadJson());
                    } catch (Exception e) {
                        throw new IllegalStateException("Could not build approval email body", e);
                    }
                });
        if (emailBodyOverride.isEmpty()
                && properties.isAutoCreateMerchantOnApprove()) {
            try {
                GeneratedMerchantCredentials creds = createMerchantForPuApplication(app);
                emailBody = appendIposSaMerchantCredentials(emailBody, creds.username(), creds.plainPassword());
            } catch (Exception e) {
                log.warn("Could not auto-create IPOS-SA merchant for application {}: {}",
                        app.getId(), e.getMessage());
            }
        }
        app.setGeneratedEmailBody(emailBody);
        app.setStatus(CommercialApplicationStatus.APPROVED);
        app.setDecidedAt(Instant.now());
        app.setDecidedBy(admin);
        repository.save(app);

        WebhookOutcome webhook = notifyPu(app, true);
        return new DecisionResult(app, webhook);
    }

    @Transactional
    public DecisionResult reject(Long id, String reason, String adminUsername) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        User admin = userRepository.findByUsername(adminUsername)
                .orElseThrow(() -> new IllegalStateException("Admin user not found: " + adminUsername));
        CommercialApplication app = repository.findById(id)
                .orElseThrow(() -> new ApplicationNotFoundException(id));
        if (app.getStatus() != CommercialApplicationStatus.PENDING) {
            throw new InvalidStatusException("Application is not pending: " + app.getStatus());
        }
        app.setRejectionReason(reason.trim());
        app.setStatus(CommercialApplicationStatus.REJECTED);
        app.setDecidedAt(Instant.now());
        app.setDecidedBy(admin);
        repository.save(app);

        WebhookOutcome webhook = notifyPu(app, false);
        return new DecisionResult(app, webhook);
    }

    /**
     * Generates a plain-text email body for IPOS-PU to send to the applicant.
     */
    String buildApprovalEmailBody(String externalReferenceId, String payloadJson)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode root = objectMapper.readTree(payloadJson);
        String company = firstText(root, "companyName", "company", "organisationName", "organizationName");
        String contact = firstText(root, "contactName", "applicantName", "name", "fullName");
        String email = firstText(root, "contactEmail", "email", "emailAddress");
        String phone = firstText(root, "contactPhone", "phone", "phoneNumber", "telephone");
        String summary = firstText(root, "summary", "description", "applicationSummary");

        StringBuilder sb = new StringBuilder();
        sb.append("Dear ");
        sb.append(contact.isEmpty() ? "Applicant" : contact);
        sb.append(",\n\n");
        sb.append("We are pleased to inform you that your commercial application ");
        sb.append("(reference: ").append(externalReferenceId).append(") has been approved by InfoPharma (IPOS-SA).\n\n");
        if (!company.isEmpty()) {
            sb.append("Organisation: ").append(company).append("\n");
        }
        if (!email.isEmpty()) {
            sb.append("Contact email on file: ").append(email).append("\n");
        }
        if (!phone.isEmpty()) {
            sb.append("Contact phone on file: ").append(phone).append("\n");
        }
        if (!summary.isEmpty()) {
            sb.append("\nApplication summary:\n").append(summary).append("\n");
        }
        sb.append("\nNext steps will be coordinated through IPOS-PU. ");
        sb.append("If you have questions, please reply to the contact channel you used for your original submission.\n\n");
        sb.append("Kind regards,\nInfoPharma IPOS-SA\n");
        return sb.toString();
    }

    private static String firstText(JsonNode root, String... fieldNames) {
        for (String name : fieldNames) {
            if (root.has(name) && !root.get(name).isNull() && root.get(name).isTextual()) {
                String t = root.get(name).asText().trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }
        return "";
    }

    private record GeneratedMerchantCredentials(String username, String plainPassword) {}

    private GeneratedMerchantCredentials createMerchantForPuApplication(CommercialApplication app)
            throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode root = objectMapper.readTree(app.getPayloadJson());
        String contactEmail = firstText(root, "contactEmail", "email", "emailAddress");
        if (contactEmail.isBlank()) {
            throw new IllegalStateException("Payload has no contact email");
        }
        String address = firstText(root, "address", "businessAddress", "registeredAddress");
        if (address.isBlank()) {
            address = firstText(root, "summary");
        }
        if (address.isBlank()) {
            address = "Address on file in commercial application";
        }
        String displayName = firstText(root, "contactName", "applicantName", "name", "fullName", "companyName");
        if (displayName.isBlank()) {
            displayName = "Commercial member";
        }
        String phone = firstText(root, "contactPhone", "phone", "phoneNumber", "telephone");
        if (phone.isBlank()) {
            phone = properties.getAutoMerchantPlaceholderPhone();
        }
        String username = deriveUniqueUsername(contactEmail, app.getExternalReferenceId());
        String plainPassword = randomPassword(14);
        merchantAccountService.createMerchantAccount(
                displayName,
                username,
                plainPassword,
                contactEmail,
                phone,
                address,
                properties.getAutoMerchantCreditLimit(),
                MerchantProfile.DiscountPlanType.FIXED,
                properties.getAutoMerchantFixedDiscountPercent(),
                null,
                null,
                null);
        return new GeneratedMerchantCredentials(username, plainPassword);
    }

    private String deriveUniqueUsername(String contactEmail, String externalReferenceId) {
        String local = contactEmail.contains("@")
                ? contactEmail.substring(0, contactEmail.indexOf('@'))
                : contactEmail;
        String sanitized = local.replaceAll("[^a-zA-Z0-9]", "");
        if (sanitized.isEmpty()) {
            sanitized = "merchant";
        }
        if (sanitized.length() > 24) {
            sanitized = sanitized.substring(0, 24);
        }
        String base = sanitized;
        for (int n = 0; n < 10_000; n++) {
            String candidate = n == 0 ? base : base + "-" + n;
            if (candidate.length() > 48) {
                candidate = candidate.substring(0, 48);
            }
            if (userRepository.findByUsername(candidate).isEmpty()) {
                return candidate;
            }
        }
        String fallback = "pu-" + Integer.toUnsignedString(externalReferenceId.hashCode());
        if (fallback.length() > 48) {
            fallback = fallback.substring(0, 48);
        }
        if (userRepository.findByUsername(fallback).isEmpty()) {
            return fallback;
        }
        throw new IllegalStateException("Could not allocate unique username for commercial application");
    }

    private static String randomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_CHARS[PASSWORD_RANDOM.nextInt(PASSWORD_CHARS.length)]);
        }
        return sb.toString();
    }

    private static String appendIposSaMerchantCredentials(String baseBody, String username, String plainPassword) {
        return MerchantCredentialsEmailFormatter.appendCredentialsToApprovalBody(baseBody, username, plainPassword);
    }

    private WebhookOutcome notifyPu(CommercialApplication app, boolean approved) {
        String url = Optional.ofNullable(app.getCallbackUrl())
                .filter(s -> !s.isBlank())
                .orElse(properties.getWebhookUrl());
        if (url == null || url.isBlank()) {
            return WebhookOutcome.skipped();
        }
        ObjectNode body = objectMapper.createObjectNode();
        body.put("internalId", app.getId());
        body.put("externalReferenceId", app.getExternalReferenceId());
        body.put("status", approved ? "APPROVED" : "REJECTED");
        if (approved) {
            body.put("emailBody", app.getGeneratedEmailBody() != null ? app.getGeneratedEmailBody() : "");
        } else {
            body.put("rejectionReason", app.getRejectionReason() != null ? app.getRejectionReason() : "");
        }
        try {
            String json = objectMapper.writeValueAsString(body);
            RestClient.RequestBodySpec req = integrationPuRestClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON);
            if (properties.getWebhookApiKey() != null && !properties.getWebhookApiKey().isBlank()) {
                req = req.header("Authorization", "Bearer " + properties.getWebhookApiKey());
            }
            req.body(json).retrieve().toBodilessEntity();
            return WebhookOutcome.sent();
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("IPOS-PU webhook failed for application {}: {}", app.getId(), e.getMessage());
            return WebhookOutcome.failed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    public static final class DecisionResult {
        private final CommercialApplication application;
        private final WebhookOutcome webhook;

        public DecisionResult(CommercialApplication application, WebhookOutcome webhook) {
            this.application = application;
            this.webhook = webhook;
        }

        public CommercialApplication getApplication() {
            return application;
        }

        public WebhookOutcome getWebhook() {
            return webhook;
        }
    }

    public static final class WebhookOutcome {
        private final String status;
        private final String errorDetail;

        private WebhookOutcome(String status, String errorDetail) {
            this.status = status;
            this.errorDetail = errorDetail;
        }

        public static WebhookOutcome skipped() {
            return new WebhookOutcome("SKIPPED", null);
        }

        public static WebhookOutcome sent() {
            return new WebhookOutcome("SENT", null);
        }

        public static WebhookOutcome failed(String detail) {
            return new WebhookOutcome("FAILED", detail);
        }

        public String getStatus() {
            return status;
        }

        public String getErrorDetail() {
            return errorDetail;
        }
    }

    public static class ApplicationNotFoundException extends RuntimeException {
        public ApplicationNotFoundException(Long id) {
            super("Commercial application not found: " + id);
        }
    }

    public static class InvalidStatusException extends RuntimeException {
        public InvalidStatusException(String message) {
            super(message);
        }
    }

    public static class DuplicateExternalReferenceException extends RuntimeException {
        private final String externalReferenceId;

        public DuplicateExternalReferenceException(String externalReferenceId) {
            super("Duplicate external reference: " + externalReferenceId);
            this.externalReferenceId = externalReferenceId;
        }

        public String getExternalReferenceId() {
            return externalReferenceId;
        }
    }
}
