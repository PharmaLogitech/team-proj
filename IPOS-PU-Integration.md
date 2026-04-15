# IPOS-PU-Integration — Complete guide (AI & developer replication)

**Purpose:** This document is the **single source of truth** for reproducing the **IPOS-SA ↔ IPOS-PU commercial membership integration** on any machine. It targets **AI coding agents** and **human developers** who must apply the same behaviour without drift.

**Verbatim replication:** **Appendix A** contains **complete** `application.properties` files. **Appendix B** lists the **Maven / JDK** settings that must match. **Appendix C** contains **full source listings** for every IPOS-PU integration file — copy-paste or diff against these to implement on another machine.

**Scope:** Commercial (merchant) applications only — **IPOS-PU** changes live under `tempdir_ipos_pu/IPOS/` (backend). **Do not** change JavaFX FXML/controllers for this integration unless product explicitly requires it.

**Companion docs (repo root):**

| Document | Role |
|----------|------|
| [`IPOS_PU_integration.md`](IPOS_PU_integration.md) | Contract-level API description (SA side authoritative for HTTP shapes). |
| [`CLAUDE.md`](CLAUDE.md) | Monorepo overview (ports, stack). |

---

## 1. Repository layout (where code lives)

| System | Path in this repo |
|--------|-------------------|
| **IPOS-SA** (Spring Boot API + React) | `backend/`, `frontend/` |
| **IPOS-PU** (JavaFX + embedded Spring) | **`tempdir_ipos_pu/IPOS/`** only |

All integration-specific PU code paths below are relative to **`tempdir_ipos_pu/IPOS/`**.

---

## 2. Non-negotiable product rules (must not be “improved away”)

1. **Credential generation for IPOS-SA (merchant login)** happens **only on IPOS-SA** (`CommercialApplicationService` + `MerchantAccountService`). The approval email body **including** those credentials is stored as `generatedEmailBody` and sent to PU in the webhook field **`emailBody`**.
2. **IPOS-PU must not invent** IPOS-SA usernames/passwords. PU **only relays** the SA-generated string via SMTP (`EmailService`).
3. **Stable correlation ID:** `externalReferenceId` **must** be `PU-MEMBER-{member.id}` **after** the member row is persisted (numeric id from PU database).
4. **Inbound authentication header name** to SA is exactly **`X-IPOS-Integration-Key`** (see SA filter constant — snippet below).
5. **Webhook URL path on PU** used in this implementation: **`POST /api/integration-pu/sa-decision`** (class-level `@RequestMapping("/api/integration-pu")` + method `@PostMapping("/sa-decision")`).

---

## 3. Runtime topology

| Process | Default port | Notes |
|---------|--------------|--------|
| IPOS-SA backend | **8080** | `server.port` in `backend/src/main/resources/application.properties` |
| IPOS-PU embedded Tomcat | **8082** | `server.port` in PU `application.properties` |
| IPOS-SA React (Vite) | **5173** | Not required for §3a server-to-server paths |

**Startup order for approve → webhook → email:** **PU must be listening on 8082** *before* an SA admin clicks **Approve**, or SA will record webhook `FAILED` (decision still saved on SA).

---

## 4. Configuration — full matrix

### 4.1 IPOS-PU (`tempdir_ipos_pu/IPOS/src/main/resources/application.properties`)

**Prefix:** `ipos.pu.integration` (Spring maps `ipos.pu.integration.sa-api-key` → `IposPuIntegrationProperties.saApiKey`).

| Property | Required | Meaning |
|----------|----------|---------|
| `ipos.pu.integration.sa-base-url` | Yes | SA base URL, no trailing slash (e.g. `http://localhost:8080`). |
| `ipos.pu.integration.sa-api-key` | **Yes** for submit + **§3b** | **Must equal** SA `ipos.integration-pu.inbound-api-key`. Used on PU→SA inbound (**§7.1**) and validated on **`POST /api/integration-sa/relay-email`** (**§7.4**). If blank, `IposSaService` throws on submit; relay returns **503** if hit. |
| `ipos.pu.integration.public-base-url` | Recommended | Used to build `callbackUrl` = `publicBaseUrl` + `webhookPath`. |
| `ipos.pu.integration.webhook-path` | Recommended | Default `/api/integration-pu/sa-decision`. |
| `ipos.pu.integration.webhook-bearer-secret` | Optional | If non-blank, PU webhook **requires** `Authorization: Bearer <exact value>`. **Must equal** SA `ipos.integration-pu.webhook-api-key` when that is set. |

**SMTP (existing):** `spring.mail.username` / `spring.mail.password` — `EmailService` and **`IntegrationSaRelayEmailController`** set **From** from `spring.mail.username` when non-empty.

### 4.2 IPOS-SA (`backend/src/main/resources/application.properties`)

**Prefix:** `ipos.integration-pu`.

| Property | Meaning |
|----------|---------|
| `ipos.integration-pu.inbound-api-key` | Must match PU `ipos.pu.integration.sa-api-key`. If blank, inbound POST returns **503** (filter). |
| `ipos.integration-pu.webhook-url` | Default URL SA uses when the application has no per-request `callbackUrl`. Should be PU’s full webhook URL. |
| `ipos.integration-pu.webhook-api-key` | If set, SA sends `Authorization: Bearer <value>` to PU; must match PU `webhook-bearer-secret`. |
| `ipos.integration-pu.auto-create-merchant-on-approve` | When `true`, SA creates MERCHANT user and appends credentials block to email body (no PU involvement). |
| `ipos.integration-pu.auto-merchant-credit-limit` | BigDecimal string for auto-created merchant. |
| `ipos.integration-pu.auto-merchant-fixed-discount-percent` | Fixed plan % for auto-created merchant. |
| `ipos.integration-pu.auto-merchant-placeholder-phone` | Used when payload has no phone. |
| `ipos.integration-pu.pu-base-url` | **§3b** — IPOS-PU base URL (no trailing slash), e.g. `http://localhost:8082`. Used for `POST …/api/integration-sa/relay-email`. |
| `ipos.integration-pu.relay-email-enabled` | **§3b** — When `true`, successful **`POST /api/merchant-accounts`** sends welcome email via PU SMTP (does **not** affect commercial approval webhook). |

### 4.3 Spring Boot environment overrides (optional)

You can set properties without editing files, e.g.:

- `IPOS_PU_INTEGRATION_SA_API_KEY` → binds to `ipos.pu.integration.sa-api-key`
- `IPOS_INTEGRATION_PU_INBOUND_API_KEY` → binds to `ipos.integration-pu.inbound-api-key`

Use the same **logical** values as in the files if you need per-machine secrets.

### 4.4 IPOS-SA MySQL: `commercial_applications` (payload size)

IPOS-SA persists each inbound submit in table **`commercial_applications`**. The column **`payload_json`** holds the **full JSON string** of the PU `payload` object (plus whatever SA serializes). Real payloads (company summary, address, etc.) **exceed VARCHAR(255)**.

**Entity (authoritative mapping):** `backend/src/main/java/com/ipos/entity/CommercialApplication.java` — `payloadJson`, `generatedEmailBody`, and `rejectionReason` use **`@Column(columnDefinition = "LONGTEXT")`** so Hibernate targets MySQL **`LONGTEXT`**.

**If the table was created earlier** (e.g. narrow `VARCHAR`), Hibernate `ddl-auto=update` may **not** widen those columns. You then get MySQL **error 1406** / *Data too long for column 'payload_json'* on insert.

**Fix (run once against the SA database, e.g. `ipos_sa`):**

```sql
USE ipos_sa;

ALTER TABLE commercial_applications
  MODIFY payload_json LONGTEXT NOT NULL,
  MODIFY generated_email_body LONGTEXT NULL,
  MODIFY rejection_reason LONGTEXT NULL;
```

Verify: `SHOW CREATE TABLE commercial_applications;` — the three columns should show **`longtext`**.

**IPOS-PU:** No code change is required for this; PU already sends a JSON object whose serialized size is naturally large. The constraint is **only on the IPOS-SA database column type**.

---

## 5. Canonical `application.properties` blocks (copy-paste for local dev)

These values are **intentionally aligned** across both systems. Replace secrets in production.

### 5.1 IPOS-PU — integration section only

**File:** `tempdir_ipos_pu/IPOS/src/main/resources/application.properties`

```properties
# IPOS-SA <-> IPOS-PU commercial applications (section 3a)
# Must match ipos.integration-pu.inbound-api-key on IPOS-SA (same string).
ipos.pu.integration.sa-base-url=http://localhost:8080
ipos.pu.integration.sa-api-key=dev-pu-sa-shared
ipos.pu.integration.public-base-url=http://localhost:8082
ipos.pu.integration.webhook-path=/api/integration-pu/sa-decision
# Must match ipos.integration-pu.webhook-api-key on IPOS-SA when that is set.
ipos.pu.integration.webhook-bearer-secret=dev-webhook-bearer
# §3b: same sa-api-key is checked on POST /api/integration-sa/relay-email (no extra property).
```

### 5.2 IPOS-SA — integration section only

**File:** `backend/src/main/resources/application.properties`

```properties
ipos.integration-pu.inbound-api-key=dev-pu-sa-shared
ipos.integration-pu.webhook-url=http://localhost:8082/api/integration-pu/sa-decision
ipos.integration-pu.webhook-api-key=dev-webhook-bearer
ipos.integration-pu.auto-create-merchant-on-approve=true
ipos.integration-pu.auto-merchant-credit-limit=10000.00
ipos.integration-pu.auto-merchant-fixed-discount-percent=5.00
ipos.integration-pu.auto-merchant-placeholder-phone=0000000000
# §3b: relay welcome email after POST /api/merchant-accounts (set relay-email-enabled=true when PU is up).
ipos.integration-pu.pu-base-url=http://localhost:8082
ipos.integration-pu.relay-email-enabled=false
```

**Equality rule:**

- `dev-pu-sa-shared` = **PU** `sa-api-key` = **SA** `inbound-api-key`
- `dev-webhook-bearer` = **PU** `webhook-bearer-secret` = **SA** `webhook-api-key`

### 5.3 Entry points (JavaFX and REST use the same service)

Commercial registration always calls **`MemberService.registerCommercial`** (single code path):

| Entry | Location |
|-------|----------|
| **JavaFX UI** | `RegisterCommercialController` → `MemberService.registerCommercial` — **do not change** FXML/controllers for §3a integration work. |
| **REST API** | `POST /api/members/register/commercial` on `MemberController` → **same** `MemberService.registerCommercial`. |

Use either path to test; integration behaviour is identical.

---

## 6. IPOS-PU — file manifest (files that implement integration)

Create or verify these paths exist with the behaviours below. **Full file contents** for each integration file are in **Appendix C** (line-for-line replication).

| File | Responsibility |
|------|----------------|
| `src/main/java/com/ipos/pu/config/IposPuIntegrationProperties.java` | `@ConfigurationProperties(prefix = "ipos.pu.integration")` — binds all `ipos.pu.integration.*` keys. |
| `src/main/java/com/ipos/pu/config/HttpClientConfig.java` | `@EnableConfigurationProperties(IposPuIntegrationProperties.class)` + `@Bean RestClient`. |
| `src/main/java/com/ipos/pu/service/IposSaService.java` | HTTP **POST** to SA inbound endpoint; builds JSON payload from `Member`; sets `externalReferenceId` = `PU-MEMBER-{id}`; stores `saApplicationId` on 201. |
| `src/main/java/com/ipos/pu/controller/SaCommercialWebhookController.java` | Receives SA webhook; Bearer validation; dispatches to `MemberService`. |
| `src/main/java/com/ipos/pu/dto/SaCommercialDecisionPayload.java` | JSON DTO for webhook body (`@JsonIgnoreProperties(ignoreUnknown = true)`). |
| `src/main/java/com/ipos/pu/service/MemberService.java` | `registerCommercial` → save then `IposSaService` (rollback delete on failure); `onCommercialApplicationApprovedFromSa` / `onCommercialApplicationRejectedFromSa`. |
| `src/main/java/com/ipos/pu/controller/MemberController.java` | `POST /api/members/register/commercial` delegates to `MemberService.registerCommercial` (same path as JavaFX). |
| `src/main/java/com/ipos/pu/model/Member.java` | Field `saApplicationId` (Long, optional). |
| `src/main/java/com/ipos/pu/service/EmailService.java` | `From` = `spring.mail.username` when set. |
| `src/main/java/com/ipos/pu/controller/IntegrationSaRelayEmailController.java` | **§3b** — `POST /api/integration-sa/relay-email`; sends mail via `JavaMailSender` (SA-generated content). |
| `src/main/java/com/ipos/pu/security/IntegrationSaInboundApiKeyFilter.java` | **§3b** — validates `X-IPOS-Integration-Key` for relay endpoint (same secret as PU→SA). |
| `src/main/java/com/ipos/pu/config/IntegrationSaRelayFilterConfig.java` | **§3b** — registers filter for `/api/integration-sa/relay-email` only. |
| `src/main/java/com/ipos/pu/dto/RelayEmailRequest.java` | **§3b** — JSON body for relay (`to`, `subject`, `body`). |
| `src/main/java/com/ipos/pu/config/SecurityConfig.java` | `permitAll` + CSRF disabled; webhook protection is **Bearer check in controller**, not URL-level security. |

**Supplementary:** `INTEGRATION_COMMERCIAL_SA_PU.md` (inside `tempdir_ipos_pu/IPOS/`) — short operator checklist.

---

## 7. Wire protocol (must match across repos)

### 7.1 PU → SA: inbound submit

- **Method / path:** `POST {saBaseUrl}/api/integration-pu/inbound/applications`
- **Header:** `X-IPOS-Integration-Key: <same as SA ipos.integration-pu.inbound-api-key>`
- **Content-Type:** `application/json`
- **Body fields:** `externalReferenceId` (string), `payload` (JSON **object**), optional `callbackUrl` (absolute URL)
- **Persistence:** SA stores the serialized `payload` as **`payload_json`** on MySQL table **`commercial_applications`** — must be **`LONGTEXT`** (see **§4.4** if inserts fail with “data too long”).
- **HTTP 409 Conflict:** SA already has this `externalReferenceId`. **`IposSaService`** treats **409 as success** (idempotent retry): it returns without throwing; `saApplicationId` may remain unset if the duplicate response is not re-read.

**Non-negotiable construction in PU** (`IposSaService`):

```text
externalReferenceId = "PU-MEMBER-" + member.getId()
Header: X-IPOS-Integration-Key = properties.getSaApiKey()
POST path suffix: /api/integration-pu/inbound/applications
```

**SA filter constant (do not rename header on either side):**

```java
// backend/src/main/java/com/ipos/security/IntegrationPuInboundApiKeyFilter.java
public static final String INTEGRATION_KEY_HEADER = "X-IPOS-Integration-Key";
```

### 7.2 SA → PU: outbound webhook

SA builds JSON in `CommercialApplicationService.notifyPu` — field names are fixed:

**Approve:**

```json
{
  "internalId": 42,
  "externalReferenceId": "PU-MEMBER-7",
  "status": "APPROVED",
  "emailBody": "…plain text from SA…"
}
```

**Reject:**

```json
{
  "internalId": 42,
  "externalReferenceId": "PU-MEMBER-7",
  "status": "REJECTED",
  "rejectionReason": "…"
}
```

If SA `ipos.integration-pu.webhook-api-key` is non-blank, SA sends:

```http
Authorization: Bearer <ipos.integration-pu.webhook-api-key>
```

PU **must** accept that token when `ipos.pu.integration.webhook-bearer-secret` is set (see `SaCommercialWebhookController.validateBearer`).

### 7.3 PU webhook: `externalReferenceId` parsing

**Must** match regex (case-insensitive): `^PU-MEMBER-(\\d+)$`

Extracted group **1** is the `Member` primary key. If this diverges from how `IposSaService` builds the id, webhooks will **400** and emails will not send.

### 7.4 SA → PU-COMMS: relay email (§3b)

Independent of §3a commercial flow. When an SA admin creates a merchant via **`POST /api/merchant-accounts`**, SA may ask PU to send the applicant a **pre-rendered** welcome email (including IPOS-SA login credentials). SA **generates** subject and body; PU **only** sends via SMTP.

- **Method / path:** `POST {puBaseUrl}/api/integration-sa/relay-email` where `puBaseUrl` = SA `ipos.integration-pu.pu-base-url`.
- **Header:** `X-IPOS-Integration-Key: <same as SA ipos.integration-pu.inbound-api-key>` — must equal PU `ipos.pu.integration.sa-api-key` (same shared secret as **§7.1**, reversed direction).
- **Content-Type:** `application/json`
- **Body:** `{ "to": "<email>", "subject": "<string>", "body": "<plain text>" }`
- **Success:** **204 No Content** (or 200). **401** if key missing/wrong; **503** if PU has no `sa-api-key` configured.

**SA implementation:** `PuCommsRelayService` → `MerchantAccountController` only (not `MerchantAccountService`, so commercial-approval auto-create does **not** double-send — that path still uses **§7.2** webhook only).

**PU implementation:** `IntegrationSaInboundApiKeyFilter` + `IntegrationSaRelayEmailController` (`tempdir_ipos_pu/IPOS/…`).

---

## 8. Payload mapping (PU → SA `payload` object)

Implemented in `IposSaService.buildPayload` — **no UI fields added**; mapping is backend-only.

| JSON key | Source |
|----------|--------|
| `companyName` | `"Commercial applicant (" + companyRegistrationNumber + ") — " + businessType` |
| `contactName` | First line of `directorDetails` (or `"Applicant"`) |
| `contactEmail`, `email` | `member.getEmail()` |
| `contactPhone` | `""` |
| `summary` | Multiline: business type, Companies House no., address |
| `address` | `member.getAddress()` |

SA email generation reads these keys (and variants) in `buildApprovalEmailBody` / merchant creation — **do not rename** `contactEmail` without updating SA `firstText(...)` fallbacks.

---

## 9. Behavioural contracts (code snippets — keep in sync)

### 9.1 Empty API key guard (PU)

```java
// IposSaService.submitCommercialApplication
if (properties.getSaApiKey() == null || properties.getSaApiKey().isBlank()) {
    throw new IllegalStateException(
            "IPOS-SA integration is not configured (set ipos.pu.integration.sa-api-key)");
}
```

### 9.2 Registration compensation (PU)

```java
// MemberService.registerCommercial — after save
try {
    iposSaService.submitCommercialApplication(saved);
} catch (RuntimeException e) {
    memberRepository.delete(saved);
    throw e;
}
```

### 9.3 HTTP 409 handling (PU inbound retry — idempotent)

```java
// IposSaService.submitCommercialApplication — RestClientResponseException branch
if (code == 409) {
    return;
}
```

### 9.4 SA outbound webhook JSON construction (field names)

```java
// CommercialApplicationService.notifyPu
body.put("internalId", app.getId());
body.put("externalReferenceId", app.getExternalReferenceId());
body.put("status", approved ? "APPROVED" : "REJECTED");
if (approved) {
    body.put("emailBody", app.getGeneratedEmailBody() != null ? app.getGeneratedEmailBody() : "");
} else {
    body.put("rejectionReason", app.getRejectionReason() != null ? app.getRejectionReason() : "");
}
```

### 9.5 PU approval email (first email = SA body verbatim)

```java
// MemberService.onCommercialApplicationApprovedFromSa
emailService.sendEmail(
        member.getEmail(),
        "InfoPharma — commercial membership approved",
        saEmailBody);
```

A **second** email sends a **PU-only** temporary password for portal login (not part of SA’s `emailBody`).

---

## 10. Build & replication pitfalls (especially on a fresh clone)

1. **Edit `src/main/resources/application.properties`, then run Maven** so the classpath copy updates:
   ```bash
   cd tempdir_ipos_pu/IPOS
   mvn -q compile
   ```
   Running from an IDE may still use stale `target/classes/application.properties` if resources were not copied — **symptom:** empty `sa-api-key` at runtime even though the editor shows a value.

2. **PU `pom.xml`** uses **`java.version` 17** and JavaFX **17.0.x** — align the JDK with **17** if the build reports “release version not supported”.

3. **SA** requires MySQL database `ipos_sa` (or your configured URL) and credentials in `backend/.../application.properties` before `mvn spring-boot:run`. Ensure **`commercial_applications.payload_json`** is **`LONGTEXT`** if you see insert failures — see **§4.4**.

---

## 11. End-to-end verification checklist

1. Set **both** property files using **§5** or paste **Appendix A** (or equivalent env vars from §4.3).
2. `mvn compile` in `tempdir_ipos_pu/IPOS` after any PU property change.
3. Start **SA** on **8080** (`cd backend && mvn spring-boot:run`).
4. Start **PU** on **8082** (JavaFX app or Spring context that opens embedded Tomcat).
5. Submit commercial registration on PU → should **not** show “not configured”; SA admin UI should list a new **PENDING** application.
6. Approve in SA → PU webhook should return **200**; applicant receives email whose **first** body matches SA `emailBody` (plus separate PU password email).

---

## 12. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|----------------|-----|
| *IPOS-SA integration is not configured (set ipos.pu.integration.sa-api-key)* | Blank key or stale `target/classes` | Set `ipos.pu.integration.sa-api-key`, run `mvn compile`, restart PU. |
| HTTP **503** from SA on inbound | SA `inbound-api-key` blank | Set SA key and restart SA. |
| HTTP **401** from SA | Key mismatch | Make PU `sa-api-key` **character-identical** to SA `inbound-api-key`. |
| Webhook **401** from PU | Bearer mismatch | Align SA `webhook-api-key` and PU `webhook-bearer-secret`, or clear both to disable bearer. |
| Webhook **FAILED** on SA UI | PU not on 8082 / wrong URL | Start PU before approve; fix `webhook-url` / `publicBaseUrl`+`webhookPath`. |
| **400** on PU webhook | `externalReferenceId` not `PU-MEMBER-{id}` | Ensure `IposSaService` and SA stored row use same format. |
| SA log: **Data too long for column 'payload_json'** (SQL 1406) | `payload_json` still `VARCHAR` / too small from an old schema | Run **`ALTER TABLE`** in **§4.4**; restart SA after migration. |
| Merchant created on SA but **no** welcome email (§3b) | `relay-email-enabled=false`, blank `pu-base-url`, or PU/SMTP down | Set **§4.2** flags; ensure PU is up and `sa-api-key` matches; check SA logs for *IPOS-PU email relay failed*. |

---

## 13. IPOS-SA files (reference — not under `tempdir_ipos_pu`)

For parity when auditing or extending:

- `backend/src/main/java/com/ipos/controller/IntegrationPuController.java` — inbound + admin APIs.
- `backend/src/main/java/com/ipos/service/CommercialApplicationService.java` — approve/reject, webhook, merchant auto-create.
- `backend/src/main/java/com/ipos/entity/CommercialApplication.java` — JPA entity for `commercial_applications`; **`payload_json` / email / rejection** columns must be **`LONGTEXT`** in MySQL (see **§4.4**).
- `backend/src/main/java/com/ipos/config/IntegrationPuProperties.java` — `ipos.integration-pu.*` binding.
- `backend/src/main/java/com/ipos/security/IntegrationPuInboundApiKeyFilter.java` — inbound API key.
- `backend/src/main/java/com/ipos/service/PuCommsRelayService.java` — **§3b** SA → PU relay HTTP client.
- `backend/src/main/java/com/ipos/service/MerchantCredentialsEmailFormatter.java` — shared credential wording for §3a append + §3b welcome email.

---

## 14. Change policy (for AI agents)

- **Allowed in IPOS-PU integration work:** `tempdir_ipos_pu/IPOS/` backend — services, controllers, config, `application.properties`, DTOs, entity fields as documented.
- **Avoid:** Editing JavaFX FXML / `RegisterCommercialController` for §3a unless the product owner explicitly expands scope.
- **When changing protocol:** Update **this file**, **`IPOS_PU_integration.md`**, and run **both** `backend` and PU tests.

---

## Appendix A — Complete `application.properties` files

Use these as **full-file** templates. Replace placeholders (`<...>`) for your environment. **Do not** commit real database or SMTP passwords to public repos.

### A.1 IPOS-PU — `tempdir_ipos_pu/IPOS/src/main/resources/application.properties`

```properties
server.port=8082
spring.application.name=ipos-pu
spring.datasource.url=jdbc:h2:file:./ipospu-db;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
spring.h2.console.enabled=true
spring.jpa.show-sql=true

# Gmail SMTP (COMMS package)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:your-sender@gmail.com}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# IPOS-SA <-> IPOS-PU commercial applications (section 3a)
# Must match ipos.integration-pu.inbound-api-key on IPOS-SA (same string).
ipos.pu.integration.sa-base-url=http://localhost:8080
ipos.pu.integration.sa-api-key=dev-pu-sa-shared
ipos.pu.integration.public-base-url=http://localhost:8082
ipos.pu.integration.webhook-path=/api/integration-pu/sa-decision
# Optional: must match ipos.integration-pu.webhook-api-key on IPOS-SA when that is set.
ipos.pu.integration.webhook-bearer-secret=dev-webhook-bearer
```

Set environment variables `MAIL_USERNAME` and `MAIL_PASSWORD` (or edit defaults) so SMTP works. **`ipos.pu.integration.sa-api-key` must be non-blank** or commercial submit fails.

### A.2 IPOS-SA — `backend/src/main/resources/application.properties` (representative full template)

MySQL URL, username, and password must match your local MySQL. Create database `ipos_sa` before first run.

```properties
server.port=8080
spring.application.name=ipos-sa

spring.datasource.url=jdbc:mysql://localhost:3306/ipos_sa
spring.datasource.username=root
spring.datasource.password=<YOUR_MYSQL_PASSWORD>
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect

ipos.bootstrap.enabled=true

# IPOS-PU integration (commercial applications)
ipos.integration-pu.inbound-api-key=dev-pu-sa-shared
ipos.integration-pu.webhook-url=http://localhost:8082/api/integration-pu/sa-decision
ipos.integration-pu.webhook-api-key=dev-webhook-bearer
ipos.integration-pu.auto-create-merchant-on-approve=true
ipos.integration-pu.auto-merchant-credit-limit=10000.00
ipos.integration-pu.auto-merchant-fixed-discount-percent=5.00
ipos.integration-pu.auto-merchant-placeholder-phone=0000000000
```

**Cross-check:** `dev-pu-sa-shared` and `dev-webhook-bearer` must match Appendix A.1.

---

## Appendix B — Maven / JDK (IPOS-PU `pom.xml` fragments)

The PU module **must** compile with **Java 17** and JavaFX **17.0.x** (not Java 21) unless you intentionally upgrade all tooling.

**File:** `tempdir_ipos_pu/IPOS/pom.xml` — extract or verify:

```xml
<properties>
    <java.version>17</java.version>
</properties>
```

JavaFX dependencies (versions must stay aligned with `java.version`):

```xml
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-controls</artifactId>
    <version>17.0.11</version>
</dependency>
<dependency>
    <groupId>org.openjfx</groupId>
    <artifactId>javafx-fxml</artifactId>
    <version>17.0.11</version>
</dependency>
```

After editing `pom.xml` or `application.properties`, run:

```bash
cd tempdir_ipos_pu/IPOS
mvn -q compile
```

---

## Appendix C — Full IPOS-PU source listings (integration-related files)
Paths are relative to the repository root. File contents are copied verbatim from this repository snapshot.

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/IposPuIntegrationProperties.java`

```java
package com.ipos.pu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * IPOS-PU ↔ IPOS-SA commercial application integration.
 */
@ConfigurationProperties(prefix = "ipos.pu.integration")
public class IposPuIntegrationProperties {

    /**
     * IPOS-SA base URL (no trailing slash), e.g. http://localhost:8080
     */
    private String saBaseUrl = "http://localhost:8080";

    /**
     * Must match {@code ipos.integration-pu.inbound-api-key} on IPOS-SA.
     */
    private String saApiKey = "";

    /**
     * Public base URL of this PU instance (scheme + host + port), used to build {@code callbackUrl} for SA webhooks.
     */
    private String publicBaseUrl = "http://localhost:8082";

    /**
     * Path on PU for SA outbound webhook POSTs (appended to {@link #publicBaseUrl}).
     */
    private String webhookPath = "/api/integration-pu/sa-decision";

    /**
     * If non-blank, webhook requests must send {@code Authorization: Bearer <this value>}.
     * Must match {@code ipos.integration-pu.webhook-api-key} on IPOS-SA when that property is set.
     */
    private String webhookBearerSecret = "";

    public String getSaBaseUrl() {
        return saBaseUrl;
    }

    public void setSaBaseUrl(String saBaseUrl) {
        this.saBaseUrl = saBaseUrl != null ? saBaseUrl : "";
    }

    public String getSaApiKey() {
        return saApiKey;
    }

    public void setSaApiKey(String saApiKey) {
        this.saApiKey = saApiKey != null ? saApiKey : "";
    }

    public String getPublicBaseUrl() {
        return publicBaseUrl;
    }

    public void setPublicBaseUrl(String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl != null ? publicBaseUrl : "";
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath != null ? webhookPath : "";
    }

    public String getWebhookBearerSecret() {
        return webhookBearerSecret;
    }

    public void setWebhookBearerSecret(String webhookBearerSecret) {
        this.webhookBearerSecret = webhookBearerSecret != null ? webhookBearerSecret : "";
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/HttpClientConfig.java`

```java
package com.ipos.pu.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(IposPuIntegrationProperties.class)
public class HttpClientConfig {

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/service/IposSaService.java`

```java
package com.ipos.pu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ipos.pu.config.IposPuIntegrationProperties;
import com.ipos.pu.model.Member;
import com.ipos.pu.repository.MemberRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

/**
 * Submits commercial membership applications to IPOS-SA for staff review (§3a).
 */
@Service
public class IposSaService {

    private final IposPuIntegrationProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MemberRepository memberRepository;

    public IposSaService(
            IposPuIntegrationProperties properties,
            RestClient restClient,
            ObjectMapper objectMapper,
            MemberRepository memberRepository) {
        this.properties = properties;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.memberRepository = memberRepository;
    }

    /**
     * POST application to IPOS-SA. Updates {@link Member#setSaApplicationId(Long)} on 201.
     * Duplicate external reference (409) is treated as success (idempotent retry).
     */
    public void submitCommercialApplication(Member member) {
        if (member.getId() == null) {
            throw new IllegalStateException("Member must be persisted before IPOS-SA submit");
        }
        if (member.getSaApplicationId() != null) {
            return;
        }
        if (properties.getSaApiKey() == null || properties.getSaApiKey().isBlank()) {
            throw new IllegalStateException(
                    "IPOS-SA integration is not configured (set ipos.pu.integration.sa-api-key)");
        }
        String base = properties.getSaBaseUrl().replaceAll("/+$", "");
        String url = base + "/api/integration-pu/inbound/applications";

        ObjectNode root = objectMapper.createObjectNode();
        root.put("externalReferenceId", "PU-MEMBER-" + member.getId());
        root.set("payload", buildPayload(member));
        String callback = buildCallbackUrl();
        if (callback != null && !callback.isBlank()) {
            root.put("callbackUrl", callback);
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(root);
            String responseBody = restClient.post()
                    .uri(url)
                    .header("X-IPOS-Integration-Key", properties.getSaApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            JsonNode resp = objectMapper.readTree(responseBody);
            if (resp.has("id") && !resp.get("id").isNull()) {
                member.setSaApplicationId(resp.get("id").asLong());
                memberRepository.save(member);
            }
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 409) {
                return;
            }
            if (code == 401) {
                throw new IllegalStateException("IPOS-SA rejected the integration API key (HTTP 401).", e);
            }
            if (code == 503) {
                throw new IllegalStateException("IPOS-SA inbound integration is unavailable (HTTP 503).", e);
            }
            throw new IllegalStateException("IPOS-SA submission failed (HTTP " + code + ").", e);
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("IPOS-SA submission failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode buildPayload(Member member) {
        ObjectNode p = objectMapper.createObjectNode();
        String reg = nullToEmpty(member.getCompanyRegistrationNumber());
        String biz = nullToEmpty(member.getBusinessType());
        p.put("companyName", "Commercial applicant (" + reg + ") — " + biz);
        p.put("contactName", contactNameFromDirector(member.getDirectorDetails()));
        p.put("contactEmail", member.getEmail());
        p.put("email", member.getEmail());
        p.put("contactPhone", "");
        String summary = "Business type: " + biz + "\n"
                + "Companies House no.: " + reg + "\n"
                + "Address:\n" + nullToEmpty(member.getAddress());
        p.put("summary", summary);
        p.put("address", nullToEmpty(member.getAddress()));
        return p;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String contactNameFromDirector(String directorDetails) {
        if (directorDetails == null || directorDetails.isBlank()) {
            return "Applicant";
        }
        String firstLine = directorDetails.trim().split("\\R", 2)[0].trim();
        return firstLine.isEmpty() ? "Applicant" : firstLine;
    }

    private String buildCallbackUrl() {
        String pub = properties.getPublicBaseUrl();
        if (pub == null || pub.isBlank()) {
            return null;
        }
        String base = pub.replaceAll("/+$", "");
        String path = properties.getWebhookPath();
        if (path == null || path.isBlank()) {
            return base;
        }
        String p = path.startsWith("/") ? path : "/" + path;
        return base + p;
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/controller/SaCommercialWebhookController.java`

```java
package com.ipos.pu.controller;

import com.ipos.pu.config.IposPuIntegrationProperties;
import com.ipos.pu.dto.SaCommercialDecisionPayload;
import com.ipos.pu.service.MemberService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives approve/reject notifications from IPOS-SA and relays the SA-generated email via SMTP.
 */
@RestController
@RequestMapping("/api/integration-pu")
public class SaCommercialWebhookController {

    private static final Pattern PU_MEMBER_REF = Pattern.compile("^PU-MEMBER-(\\d+)$", Pattern.CASE_INSENSITIVE);

    private final IposPuIntegrationProperties properties;
    private final MemberService memberService;

    public SaCommercialWebhookController(IposPuIntegrationProperties properties, MemberService memberService) {
        this.properties = properties;
        this.memberService = memberService;
    }

    @PostMapping("/sa-decision")
    public ResponseEntity<Void> handleDecision(
            @RequestBody SaCommercialDecisionPayload body,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!validateBearer(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<Long> memberId = parseMemberId(body.getExternalReferenceId());
        if (memberId.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String status = body.getStatus() != null ? body.getStatus().trim().toUpperCase() : "";
        try {
            if ("APPROVED".equals(status)) {
                memberService.onCommercialApplicationApprovedFromSa(memberId.get(), body.getEmailBody());
            } else if ("REJECTED".equals(status)) {
                memberService.onCommercialApplicationRejectedFromSa(memberId.get(), body.getRejectionReason());
            } else {
                return ResponseEntity.badRequest().build();
            }
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok().build();
    }

    private boolean validateBearer(String authorizationHeader) {
        String secret = properties.getWebhookBearerSecret();
        if (secret == null || secret.isBlank()) {
            return true;
        }
        if (authorizationHeader == null || authorizationHeader.length() < 7) {
            return false;
        }
        if (!authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String token = authorizationHeader.substring(7).trim();
        return secret.equals(token);
    }

    private static Optional<Long> parseMemberId(String externalReferenceId) {
        if (externalReferenceId == null || externalReferenceId.isBlank()) {
            return Optional.empty();
        }
        Matcher m = PU_MEMBER_REF.matcher(externalReferenceId.trim());
        if (!m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(m.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/dto/SaCommercialDecisionPayload.java`

```java
package com.ipos.pu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * JSON body from IPOS-SA outbound webhook (approve / reject commercial application).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SaCommercialDecisionPayload {

    private long internalId;
    private String externalReferenceId;
    private String status;
    private String emailBody;
    private String rejectionReason;

    public long getInternalId() {
        return internalId;
    }

    public void setInternalId(long internalId) {
        this.internalId = internalId;
    }

    public String getExternalReferenceId() {
        return externalReferenceId;
    }

    public void setExternalReferenceId(String externalReferenceId) {
        this.externalReferenceId = externalReferenceId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEmailBody() {
        return emailBody;
    }

    public void setEmailBody(String emailBody) {
        this.emailBody = emailBody;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/service/EmailService.java`

```java
package com.ipos.pu.service;

import com.ipos.pu.model.EmailLog;
import com.ipos.pu.repository.EmailLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class EmailService {

    private final EmailLogRepository emailLogRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailService(
            EmailLogRepository emailLogRepository,
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.emailLogRepository = emailLogRepository;
        this.mailSender = mailSender;
        this.fromAddress = mailUsername != null ? mailUsername.trim() : "";
    }

    public boolean sendEmail(String to, String subject, String body) {
        EmailLog log = new EmailLog();
        log.setSentAt(LocalDateTime.now());
        log.setRecipient(to);
        log.setSubject(subject);
        log.setBody(body);
        emailLogRepository.save(log);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!fromAddress.isEmpty()) {
                message.setFrom(fromAddress);
            }
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);

            System.out.println("--- EMAIL SENT ---");
            System.out.println("To: " + to);
            System.out.println("Subject: " + subject);
            System.out.println("------------------");
            return true;
        } catch (Exception e) {
            System.err.println("--- EMAIL FAILED ---");
            System.err.println("To: " + to);
            System.err.println("Error: " + e.getMessage());
            System.err.println("--------------------");
            return false;
        }
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/SecurityConfig.java`

```java
package com.ipos.pu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/model/Member.java`

```java
package com.ipos.pu.model;

import jakarta.persistence.*;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String firstName;
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberType memberType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    private boolean passwordChangeRequired;
    private boolean admin;

    private int orderCounter;

    // Commercial members only - null for non-commercial
    private String companyRegistrationNumber;
    private String directorDetails;
    private String businessType;
    private String address;

    /** IPOS-SA commercial_applications.id after successful inbound submit (optional). */
    private Long saApplicationId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public MemberType getMemberType() { return memberType; }
    public void setMemberType(MemberType memberType) { this.memberType = memberType; }

    public MemberStatus getStatus() { return status; }
    public void setStatus(MemberStatus status) { this.status = status; }

    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }
    public void setPasswordChangeRequired(boolean passwordChangeRequired) { this.passwordChangeRequired = passwordChangeRequired; }

    public int getOrderCounter() { return orderCounter; }
    public void setOrderCounter(int orderCounter) { this.orderCounter = orderCounter; }

    public String getCompanyRegistrationNumber() { return companyRegistrationNumber; }
    public void setCompanyRegistrationNumber(String companyRegistrationNumber) { this.companyRegistrationNumber = companyRegistrationNumber; }

    public String getDirectorDetails() { return directorDetails; }
    public void setDirectorDetails(String directorDetails) { this.directorDetails = directorDetails; }

    public String getBusinessType() { return businessType; }
    public void setBusinessType(String businessType) { this.businessType = businessType; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public Long getSaApplicationId() { return saApplicationId; }
    public void setSaApplicationId(Long saApplicationId) { this.saApplicationId = saApplicationId; }

    public boolean isAdmin() { return admin; }
    public void setAdmin(boolean admin) { this.admin = admin; }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/service/MemberService.java`

```java
package com.ipos.pu.service;

import com.ipos.pu.model.Member;
import com.ipos.pu.model.MemberStatus;
import com.ipos.pu.model.MemberType;
import com.ipos.pu.repository.MemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class MemberService {

    private static final String PASSWORD_LETTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String PASSWORD_DIGITS = "0123456789";
    private static final String PASSWORD_SPECIALS = "!@#$%^&*?-_";
    private static final String PASSWORD_ALL = PASSWORD_LETTERS + PASSWORD_DIGITS + PASSWORD_SPECIALS;
    private static final SecureRandom RANDOM = new SecureRandom();

    // Basic email format check (local-part@domain.tld)
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    // UK Companies House number: exactly 8 characters, digits or uppercase letters.
    // Covers plain numeric (e.g. 12345678), Scotland (SC123456), NI (NI123456), LLPs (OC123456), etc.
    private static final Pattern COMPANIES_HOUSE_PATTERN =
            Pattern.compile("^[A-Z0-9]{8}$");

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final IposSaService iposSaService;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder, EmailService emailService, IposSaService iposSaService) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.iposSaService = iposSaService;
    }

    // UC4 - Register a non-commercial member
    public Member registerNonCommercial(String email, String firstName, String lastName) {
        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        String temporaryPassword = generateTemporaryPassword();

        Member member = new Member();
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(temporaryPassword));
        member.setFirstName(firstName);
        member.setLastName(lastName);
        member.setMemberType(MemberType.NON_COMMERCIAL);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordChangeRequired(true);
        member.setOrderCounter(0);

        Member saved = memberRepository.save(member);

        emailService.sendEmail(
                email,
                "Welcome to IPOS-PU - Your Login Credentials",
                "Hello " + firstName + ",\n\n" +
                "Your account has been created.\n" +
                "Email: " + email + "\n" +
                "Temporary password: " + temporaryPassword + "\n\n" +
                "Please log in and change your password immediately."
        );

        return saved;
    }

    // UC2 - Register a commercial member (pending approval)
    public Member registerCommercial(String email, String companyRegistrationNumber,
                                     String directorDetails, String businessType, String address) {
        validateCommercialApplication(email, companyRegistrationNumber, directorDetails, businessType, address);

        if (memberRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("An account with this email already exists.");
        }

        Member member = new Member();
        member.setEmail(email);
        member.setPassword("");
        member.setMemberType(MemberType.COMMERCIAL);
        member.setStatus(MemberStatus.PENDING);
        member.setPasswordChangeRequired(false);
        member.setOrderCounter(0);
        member.setCompanyRegistrationNumber(companyRegistrationNumber);
        member.setDirectorDetails(directorDetails);
        member.setBusinessType(businessType);
        member.setAddress(address);

        Member saved = memberRepository.save(member);
        try {
            iposSaService.submitCommercialApplication(saved);
        } catch (RuntimeException e) {
            memberRepository.delete(saved);
            throw e;
        }
        return saved;
    }

    /**
     * Called when IPOS-SA approves a commercial application (webhook). Sends the SA-generated body
     * unchanged by SMTP, then a separate message with a temporary PU portal password.
     */
    public void onCommercialApplicationApprovedFromSa(Long memberId, String saEmailBody) {
        if (saEmailBody == null || saEmailBody.isBlank()) {
            throw new IllegalArgumentException("Approval email body is required.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found."));
        if (member.getMemberType() != MemberType.COMMERCIAL) {
            throw new IllegalStateException("Member is not commercial.");
        }
        if (member.getStatus() == MemberStatus.ACTIVE
                && member.getPassword() != null
                && !member.getPassword().isEmpty()) {
            return;
        }
        String firstLine = contactNameFromDirector(member.getDirectorDetails());
        member.setFirstName(firstLine);
        String temp = generateTemporaryPassword();
        member.setPassword(passwordEncoder.encode(temp));
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordChangeRequired(true);
        memberRepository.save(member);

        emailService.sendEmail(
                member.getEmail(),
                "InfoPharma — commercial membership approved",
                saEmailBody);
        emailService.sendEmail(
                member.getEmail(),
                "IPOS-PU — portal login",
                "Your temporary password for the IPOS-PU portal is:\n\n"
                        + temp
                        + "\n\nPlease log in and change your password immediately.");
    }

    /**
     * Called when IPOS-SA rejects a commercial application (webhook).
     */
    public void onCommercialApplicationRejectedFromSa(Long memberId, String rejectionReason) {
        if (rejectionReason == null || rejectionReason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required.");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found."));
        if (member.getMemberType() != MemberType.COMMERCIAL) {
            throw new IllegalStateException("Member is not commercial.");
        }
        if (member.getStatus() == MemberStatus.INACTIVE) {
            return;
        }
        member.setStatus(MemberStatus.INACTIVE);
        memberRepository.save(member);

        emailService.sendEmail(
                member.getEmail(),
                "Commercial membership application",
                "We regret to inform you that your commercial membership application could not be approved at this time.\n\n"
                        + "Reason:\n"
                        + rejectionReason.trim());
    }

    private static String contactNameFromDirector(String directorDetails) {
        if (directorDetails == null || directorDetails.isBlank()) {
            return "Member";
        }
        String firstLine = directorDetails.trim().split("\\R", 2)[0].trim();
        return firstLine.isEmpty() ? "Member" : firstLine;
    }

    // UC6 - Login
    public Member login(String email, String rawPassword) {
        Member member = memberRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password."));

        if (member.getStatus() == MemberStatus.SUSPENDED || member.getStatus() == MemberStatus.INACTIVE) {
            throw new IllegalStateException("This account is suspended or inactive.");
        }

        if (member.getStatus() == MemberStatus.PENDING) {
            throw new IllegalStateException("This account is pending approval.");
        }

        if (!passwordEncoder.matches(rawPassword, member.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password.");
        }

        return member;
    }

    // UC7 - Change password
    public void changePassword(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found."));

        if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }

        member.setPassword(passwordEncoder.encode(newPassword));
        member.setPasswordChangeRequired(false);
        memberRepository.save(member);
    }

    public Optional<Member> findByEmail(String email) {
        return memberRepository.findByEmail(email);
    }

    // Brief (p.19): commercial applications must include email, Companies House
    // registration number, director details, business type and address.
    // Every field is checked here; the first failure throws with a message
    // that surfaces directly to the applicant in the UI.
    private void validateCommercialApplication(String email, String companyRegistrationNumber,
                                                String directorDetails, String businessType,
                                                String address) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Email address format is invalid.");
        }

        if (companyRegistrationNumber == null || companyRegistrationNumber.isBlank()) {
            throw new IllegalArgumentException("Companies House registration number is required.");
        }
        if (!COMPANIES_HOUSE_PATTERN.matcher(companyRegistrationNumber.toUpperCase()).matches()) {
            throw new IllegalArgumentException(
                    "Companies House number must be 8 characters (digits or letters), e.g. 12345678 or SC123456.");
        }

        if (directorDetails == null || directorDetails.isBlank()) {
            throw new IllegalArgumentException("Company director details are required.");
        }
        if (directorDetails.trim().length() < 3) {
            throw new IllegalArgumentException("Company director details look too short.");
        }

        if (businessType == null || businessType.isBlank()) {
            throw new IllegalArgumentException("Type of business is required.");
        }

        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("Business address is required.");
        }
        if (address.trim().length() < 10) {
            throw new IllegalArgumentException("Business address looks too short.");
        }
    }

    // Brief: 10 symbols including letters, numbers and special symbols.
    // Guarantee at least one of each, then fill and shuffle.
    private String generateTemporaryPassword() {
        char[] chars = new char[10];
        chars[0] = PASSWORD_LETTERS.charAt(RANDOM.nextInt(PASSWORD_LETTERS.length()));
        chars[1] = PASSWORD_DIGITS.charAt(RANDOM.nextInt(PASSWORD_DIGITS.length()));
        chars[2] = PASSWORD_SPECIALS.charAt(RANDOM.nextInt(PASSWORD_SPECIALS.length()));
        for (int i = 3; i < chars.length; i++) {
            chars[i] = PASSWORD_ALL.charAt(RANDOM.nextInt(PASSWORD_ALL.length()));
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }
}
```

### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/controller/MemberController.java`

```java
package com.ipos.pu.controller;

import com.ipos.pu.dto.*;
import com.ipos.pu.model.Member;
import com.ipos.pu.service.MemberService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // UC4 - Non-commercial registration
    @PostMapping("/register/non-commercial")
    public ResponseEntity<?> registerNonCommercial(@RequestBody NonCommercialRegistrationRequest request) {
        try {
            Member member = memberService.registerNonCommercial(
                    request.getEmail(),
                    request.getFirstName(),
                    request.getLastName()
            );
            return ResponseEntity.ok(MemberResponse.from(member));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // UC2 - Commercial registration
    @PostMapping("/register/commercial")
    public ResponseEntity<?> registerCommercial(@RequestBody CommercialRegistrationRequest request) {
        try {
            Member member = memberService.registerCommercial(
                    request.getEmail(),
                    request.getCompanyRegistrationNumber(),
                    request.getDirectorDetails(),
                    request.getBusinessType(),
                    request.getAddress()
            );
            return ResponseEntity.ok(MemberResponse.from(member));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // UC6 - Login
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            Member member = memberService.login(request.getEmail(), request.getPassword());
            return ResponseEntity.ok(MemberResponse.from(member));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // UC7 - Change password
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            memberService.changePassword(
                    request.getMemberId(),
                    request.getCurrentPassword(),
                    request.getNewPassword()
            );
            return ResponseEntity.ok("Password changed successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
```

---

## Appendix D — Regenerating Appendix C from source (optional)

After changing any file listed in Appendix C, refresh the Java listings by running this script from the **repository root** (Python 3). It overwrites `_ipos_pu_appendix_c_fragment.md`; paste the result into this document or concatenate in place of the old Appendix C section.

```python
from pathlib import Path
base = Path(".")
paths = [
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/IposPuIntegrationProperties.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/HttpClientConfig.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/service/IposSaService.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/controller/SaCommercialWebhookController.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/dto/SaCommercialDecisionPayload.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/service/EmailService.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/SecurityConfig.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/model/Member.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/service/MemberService.java",
    "tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/controller/MemberController.java",
]
out = []
fence = "```"
out.append("## Appendix C — Full IPOS-PU source listings (integration-related files)\n\n")
out.append("Paths are relative to the repository root.\n\n")
for p in paths:
    text = (base / p).read_text(encoding="utf-8")
    out.append("### `" + p + "`\n\n" + fence + "java\n")
    out.append(text.rstrip() + "\n" + fence + "\n\n")
Path("_ipos_pu_appendix_c_fragment.md").write_text("".join(out), encoding="utf-8")
print("Wrote _ipos_pu_appendix_c_fragment.md")
```

---

## Warnings

- **Templates vs checked-in files:** Appendix A (and §5 snippets) use **example** values and placeholders (`<YOUR_MYSQL_PASSWORD>`, mail env vars). Your clone’s real `application.properties` files may differ; always align **keys and shared secrets** with this guide rather than assuming the file on disk matches Appendix A verbatim.
- **Appendix C drift:** Embedded Java is a **snapshot**. After changing any integration-related source file, **regenerate Appendix C** (Appendix D) or diff against the repo — stale listings mislead reviewers and AI tools.
- **PU build output:** If `ipos.pu.integration.sa-api-key` looks set in the editor but the app still says it is unset, run `mvn compile` in `tempdir_ipos_pu/IPOS` and restart — **`target/classes`** may be stale.
- **Environment variables:** If you configure integration via env vars instead of files, confirm names against your **Spring Boot** version’s relaxed-binding rules; wrong names silently leave properties empty.
- **Secrets:** Do not commit real database or SMTP passwords into this document or into shared `application.properties` in public repositories.
- **Markdown rendering:** Appendix D’s Python sample assigns a string containing backticks (`fence = "```"`). Some minimal Markdown previews may render oddly; the script is still valid Python.

---

*Document version: aligned with the integration implementation in this repository. Appendices A and B are templates (use placeholders for secrets). Appendix C is a verbatim export of the listed Java paths. Use Appendix D to refresh Appendix C after code changes. The closing section **Merchant Account automated email generation with login** documents §3b IPOS-PU changes for SA → PU relay.*

---

## Merchant Account automated email generation with login (§3b — IPOS-PU)

This section is the **replication guide for IPOS-PU** changes that support **IPOS-SA → IPOS-PU-COMMS**: when an SA administrator creates a merchant account via **`POST /api/merchant-accounts`** on IPOS-SA, SA generates a **welcome email** (including **IPOS-SA login username and password**) and **calls IPOS-PU** only to **send** that email through PU’s existing **SMTP** configuration. IPOS-PU does **not** invent credentials; it is a **mail transport** for SA-authored content.

**Relationship to other integration work:**

| Topic | §3a (commercial membership) | §3b (this section) |
|-------|----------------------------|----------------------|
| Direction | Mostly PU → SA (inbound application); SA → PU webhook on approve/reject | **SA → PU** (relay email only) |
| Trigger | PU member registers commercially; SA staff approves/rejects | SA staff creates merchant **directly** on SA |
| Email content authored by | SA (`emailBody` / rejection in webhook) | SA (`subject` + `body` in relay JSON) |
| PU role | Receive webhook, send SMTP | Receive relay POST, send SMTP |

**IPOS-PU must implement:** authenticated REST endpoint, API-key filter, DTO, and (if not already present) **`spring-boot-starter-validation`** so `jakarta.validation` resolves in the IDE and at compile time. **IPOS-SA** implements the HTTP client and feature flag; that logic lives under `backend/` and is summarized only where needed for alignment.

---

### M1. Prerequisites (both systems)

1. **Network:** IPOS-SA can reach IPOS-PU’s HTTP port (default PU **`8082`**).
2. **Shared secret:** The value of PU `ipos.pu.integration.sa-api-key` **must equal** SA `ipos.integration-pu.inbound-api-key` (same string as PU→SA inbound — one key, two directions).
3. **SMTP on PU:** `spring.mail.*` is configured so `JavaMailSender` can send (same stack as commercial webhook emails).
4. **SA feature flag:** On IPOS-SA, `ipos.integration-pu.relay-email-enabled=true` and `ipos.integration-pu.pu-base-url` set to PU’s base URL (e.g. `http://localhost:8082`). If the flag is **`false`**, SA **never** calls PU for this flow (merchant is still created).

---

### M2. IPOS-PU — new and modified artifacts (paths)

All paths are under **`tempdir_ipos_pu/IPOS/`**.

| Path | Action |
|------|--------|
| `src/main/java/com/ipos/pu/dto/RelayEmailRequest.java` | **New** — JSON body: `to`, `subject`, `body`. |
| `src/main/java/com/ipos/pu/controller/IntegrationSaRelayEmailController.java` | **New** — `POST /api/integration-sa/relay-email`; uses `JavaMailSender`. |
| `src/main/java/com/ipos/pu/security/IntegrationSaInboundApiKeyFilter.java` | **New** — Validates `X-IPOS-Integration-Key` for the relay path. |
| `src/main/java/com/ipos/pu/config/IntegrationSaRelayFilterConfig.java` | **New** — Registers the filter **only** for `/api/integration-sa/relay-email`. |
| `pom.xml` | **Modify** — add `spring-boot-starter-validation` if not already present (required for `@Valid` / `@NotBlank`). |
| `src/main/resources/application.properties` | **Verify** — `ipos.pu.integration.sa-api-key` matches SA (usually already set for §3a). |

**Do not** change §3a webhook controller (`SaCommercialWebhookController`), PU→SA client (`IposSaService`), or commercial registration flow unless you are fixing an unrelated bug.

---

### M3. Maven (`pom.xml`)

Add **`spring-boot-starter-validation`** next to `spring-boot-starter-web` so `jakarta.validation.constraints` and `@Valid` resolve (IDE and compiler).

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

No version tag — inherit from `spring-boot-starter-parent`. After editing, run:

```bash
cd tempdir_ipos_pu/IPOS
mvn -q compile
```

---

### M4. Configuration (`application.properties` on IPOS-PU)

No **new** property is strictly required for §3b beyond what §3a already uses: **`ipos.pu.integration.sa-api-key`** must be non-blank and match SA’s inbound key (SA calls PU **with that header**).

Example fragment (align with your environment; **use env vars or secrets for mail passwords**, not committed literals):

```properties
# Existing §3a key — also used to authenticate SA -> PU relay (§3b)
ipos.pu.integration.sa-api-key=dev-pu-sa-shared

# SMTP (required for any outbound mail from PU, including relay)
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME:your-sender@gmail.com}
spring.mail.password=${MAIL_PASSWORD:}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true
```

---

### M5. Security model on IPOS-PU

1. **API key header:** SA sends **`X-IPOS-Integration-Key: <same as ipos.pu.integration.sa-api-key>`** (constant name matches SA’s `IntegrationPuInboundApiKeyFilter.INTEGRATION_KEY_HEADER`).
2. **Filter scope:** `FilterRegistrationBean` applies **`IntegrationSaInboundApiKeyFilter`** only to URL pattern **`/api/integration-sa/relay-email`** — other PU routes are unaffected.
3. **CSRF:** If your `SecurityConfig` uses **`csrf.disable()`** globally (as in the stock PU template in this guide), **no extra CSRF exemption** is required for the relay POST. If you enable CSRF later, you must exempt this endpoint for server-to-server calls.
4. **Responses:** Wrong or missing key → **401** JSON; blank `sa-api-key` in PU config → **503** JSON; validation failure on body → **400** (Spring MVC); SMTP failure in controller → **502 Bad Gateway** in the reference implementation.

---

### M6. HTTP contract (what SA sends to PU)

| Item | Value |
|------|--------|
| Method | `POST` |
| Path | `{puBaseUrl}/api/integration-sa/relay-email` |
| Header | `X-IPOS-Integration-Key: <shared secret>` |
| `Content-Type` | `application/json` |
| Body | `{"to":"<email>","subject":"<string>","body":"<plain text>"}` |
| Success | **204 No Content** (reference implementation) |

The **`body`** plain text is generated entirely on **IPOS-SA** (welcome message + username + password + change-password reminder). PU **must not** append credentials on its own.

---

### M7. Full source listings (IPOS-PU — copy as-is)

#### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/dto/RelayEmailRequest.java`

```java
package com.ipos.pu.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

/**
 * JSON body for IPOS-SA → IPOS-PU relay: SA generates content; PU sends via SMTP only.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RelayEmailRequest {

    @NotBlank
    private String to;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
```

#### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/controller/IntegrationSaRelayEmailController.java`

```java
package com.ipos.pu.controller;

import com.ipos.pu.dto.RelayEmailRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * §3b: IPOS-SA sends pre-rendered email content; IPOS-PU-COMMS sends via configured SMTP.
 */
@RestController
@RequestMapping("/api/integration-sa")
public class IntegrationSaRelayEmailController {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public IntegrationSaRelayEmailController(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String mailUsername) {
        this.mailSender = mailSender;
        this.fromAddress = mailUsername != null ? mailUsername.trim() : "";
    }

    @PostMapping("/relay-email")
    public ResponseEntity<Void> relayEmail(@Valid @RequestBody RelayEmailRequest body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            if (!fromAddress.isEmpty()) {
                message.setFrom(fromAddress);
            }
            message.setTo(body.getTo());
            message.setSubject(body.getSubject());
            message.setText(body.getBody());
            mailSender.send(message);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
```

#### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/security/IntegrationSaInboundApiKeyFilter.java`

```java
package com.ipos.pu.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Validates {@code X-IPOS-Integration-Key} for {@code POST /api/integration-sa/relay-email}.
 * Must match {@code ipos.pu.integration.sa-api-key} (same value as IPOS-SA {@code ipos.integration-pu.inbound-api-key}).
 */
public class IntegrationSaInboundApiKeyFilter extends OncePerRequestFilter {

    public static final String INTEGRATION_KEY_HEADER = "X-IPOS-Integration-Key";

    private static final String RELAY_PATH = "/api/integration-sa/relay-email";

    private final String expectedKey;

    public IntegrationSaInboundApiKeyFilter(String expectedKey) {
        this.expectedKey = expectedKey != null ? expectedKey : "";
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!request.getMethod().equalsIgnoreCase("POST")
                || uri == null
                || !uri.endsWith(RELAY_PATH)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (expectedKey.isBlank()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"IPOS-SA relay is not configured (set ipos.pu.integration.sa-api-key).\"}");
            return;
        }

        String provided = request.getHeader(INTEGRATION_KEY_HEADER);
        if (provided == null || !expectedKey.equals(provided)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid or missing integration API key.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

#### `tempdir_ipos_pu/IPOS/src/main/java/com/ipos/pu/config/IntegrationSaRelayFilterConfig.java`

```java
package com.ipos.pu.config;

import com.ipos.pu.security.IntegrationSaInboundApiKeyFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Runs the API-key filter only for the SA → PU relay endpoint (not global).
 */
@Configuration
public class IntegrationSaRelayFilterConfig {

    @Bean
    public FilterRegistrationBean<IntegrationSaInboundApiKeyFilter> integrationSaInboundApiKeyFilterRegistration(
            @Value("${ipos.pu.integration.sa-api-key:}") String saApiKey) {
        FilterRegistrationBean<IntegrationSaInboundApiKeyFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new IntegrationSaInboundApiKeyFilter(saApiKey));
        bean.addUrlPatterns("/api/integration-sa/relay-email");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }
}
```

---

### M8. IPOS-SA alignment (reference only — not IPOS-PU code)

Replicating teams need SA configured so it **calls** PU when a merchant is created from the admin UI:

- **`ipos.integration-pu.relay-email-enabled=true`**
- **`ipos.integration-pu.pu-base-url=http://<pu-host>:<pu-port>`** (no trailing slash)
- **`ipos.integration-pu.inbound-api-key`** matches PU `sa-api-key`

SA implementation files (for cross-repo audits): `PuCommsRelayService`, `MerchantAccountController`, `MerchantCredentialsEmailFormatter`, `IntegrationPuProperties` under `backend/src/main/java/com/ipos/`.

---

### M9. Verification checklist (IPOS-PU)

1. `mvn compile` succeeds in `tempdir_ipos_pu/IPOS`.
2. PU is running; `POST` to `http://localhost:8082/api/integration-sa/relay-email` **without** header → **401** (or filter behaviour as above).
3. Same POST with correct `X-IPOS-Integration-Key` and valid JSON body → mail sent (check recipient inbox / SMTP logs).
4. §3a commercial flow still works (inbound + webhook) — relay files do not replace those endpoints.

---

### M10. Troubleshooting

| Symptom | Check |
|---------|--------|
| **401** on relay | Header name spelling; key byte-for-byte match with SA. |
| **503** on relay | `ipos.pu.integration.sa-api-key` empty on PU. |
| IDE red under `@NotBlank` / `@Valid` | Add **`spring-boot-starter-validation`**; Maven reload. |
| Merchant created on SA but no email | SA side: **`relay-email-enabled=false`**; or PU down; or SMTP misconfigured on PU — see SA logs for *IPOS-PU email relay failed*. |
| Duplicate email for commercial approval | §3b must **not** be triggered from `MerchantAccountService` — only from `MerchantAccountController` (direct admin create). Commercial path uses webhook only. |
