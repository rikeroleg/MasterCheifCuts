# MasterChef Cuts — Security Audit Report

**Date**: 2025-07-31  
**Scope**: Full-stack — Spring Boot 3.4.3 backend + React 19 / Vite frontend  
**Branch scanned**: `feat/backend-prod-ready` (backend) · `feat/prod-ready` (frontend)  
**Framework**: OWASP Top 10 (2021 edition)

---

## Executive Summary

The application has a solid security foundation: httpOnly / SameSite=Strict JWT cookies, parameterised queries throughout, BCrypt password hashing, CSRF cookie mitigation, Stripe webhook signature verification, locked-down Actuator, MIME-validated file uploads, and no XSS sinks in the frontend. Two medium-severity gaps and several low/informational issues require remediation before production launch.

---

## Findings Overview

| ID | Severity | Title | Location |
|----|----------|-------|----------|
| B-01 | 🟠 MEDIUM | Reset / verification tokens stored plaintext in DB | `AuthService.java` |
| B-02 | 🟠 MEDIUM | No rate limiting on mutation endpoints | `RateLimitFilter.java` |
| F-01 | 🟠 MEDIUM | `mc_token` never set — SSE and license upload silently broken | `NotificationContext.jsx`, `Profile.jsx` |
| B-03 | 🟡 LOW | Weak password policy (8-char min only) | `RegisterRequest.java`, `ResetPasswordRequest.java` |
| B-04 | 🟡 LOW | Missing security response headers | `SecurityConfig.java` |
| B-05 | 🟡 LOW | CORS wildcard `allowedHeaders("*")` | `CorsConfig.java` |
| F-02 | 🟡 LOW | SSE URL `?token=` pattern would expose JWT in logs if fixed incorrectly | `NotificationContext.jsx` |
| B-06 | ℹ️ INFO | No catch-all exception handler | `GlobalExceptionHandler.java` |
| F-03 | ℹ️ INFO | `VITE_LOG_LEVEL=debug` only in dev env | `.env.development` |
| F-04 | ℹ️ INFO | `localStorage` profile cache (`mc_user`) | `AuthContext.jsx` |

---

## Detailed Findings

---

### B-01 🟠 MEDIUM — Reset / Verification Tokens Stored as Plaintext in Database

**OWASP**: A02:2021 – Cryptographic Failures

**Description**  
Password-reset tokens (`reset_token`) and email-verification tokens (`verification_token`) are stored as plain UUID strings in the `participants` table. If the database is compromised, an attacker has all pending tokens and can immediately reset any account or verify any email without knowing user passwords.

**Location**  
`src/main/java/com/masterchefcuts/services/AuthService.java` — `forgotPassword()`, `register()`, `resendVerification()`

**Evidence**
```java
String token = UUID.randomUUID().toString();
participant.setResetToken(token);          // stored plain
participant.setResetTokenExpiry(…);
```

**Recommendation**  
Hash tokens before persisting. On verification, hash the incoming token and compare against the stored hash:
```java
// Generate token
String rawToken = UUID.randomUUID().toString();
String hashedToken = DigestUtils.sha256Hex(rawToken);   // Apache Commons Codec
participant.setResetToken(hashedToken);

// Verify
String incoming = DigestUtils.sha256Hex(rawTokenFromEmail);
if (!incoming.equals(participant.getResetToken())) { throw … }
```
SHA-256 is sufficient here (tokens are random, high entropy) — bcrypt is unnecessary overhead.

---

### B-02 🟠 MEDIUM — Mutation Endpoints Not Rate-Limited

**OWASP**: A05:2021 – Security Misconfiguration / A07:2021 – Identification and Authentication Failures

**Description**  
`RateLimitFilter` limits POST requests only on five auth paths. Several user-facing mutation endpoints are entirely unthrottled, enabling spam, abuse, and enumeration:

| Endpoint | Issue |
|----------|-------|
| `POST /api/reviews` | Review spam; each IP can post unlimited reviews per listing |
| `POST /api/animal-requests` | Request flooding |
| `POST /api/waitlist/{id}/join` | Waitlist spam |
| `POST /api/comments` | Comment flood |
| `POST /api/contact` | Contact form abuse / email bombing |

**Location**  
`src/main/java/com/masterchefcuts/filter/RateLimitFilter.java`

**Recommendation**  
Extend the existing sliding-window rate limiter to cover mutation endpoints. Suggested limits (adjust to traffic):

```java
private static final Map<String, int[]> ENDPOINT_LIMITS = Map.of(
    "/api/auth/login",                new int[]{10, 60},
    "/api/auth/register",             new int[]{10, 60},
    "/api/auth/forgot-password",      new int[]{10, 60},
    "/api/auth/resend-verification",  new int[]{10, 60},
    "/api/auth/refresh",              new int[]{10, 60},
    "/api/reviews",                   new int[]{5,  60},   // 5 reviews/min
    "/api/animal-requests",           new int[]{3,  60},
    "/api/waitlist",                  new int[]{10, 60},
    "/api/comments",                  new int[]{20, 60},
    "/api/contact",                   new int[]{3,  60}
);
```

---

### F-01 🟠 MEDIUM — `mc_token` Referenced in `localStorage` but Never Set (SSE + License Upload Broken)

**OWASP**: A07:2021 – Identification and Authentication Failures

**Description**  
Two components read `localStorage.getItem('mc_token')` but this key is never written anywhere in the application. The JWT lives exclusively in the `mc_auth` httpOnly cookie. As a result:

1. **`NotificationContext.jsx` line 33** — `token` is always `null`, so the `if (token && …)` guard is always false. SSE is never established; all users always fall back to 30-second polling. The SSE infrastructure is dead code.
2. **`Profile.jsx` line 402** — `handleLicenseUpload()` sends `Authorization: Bearer null`. The backend's `JwtAuthFilter` receives a literal `null` string as the bearer token, which fails JWT validation, so all license uploads fail for all users with a 401.

**Location**  
`src/context/NotificationContext.jsx:33`  
`src/pages/Profile.jsx:402`

**Recommendation**  
For SSE, the correct fix is `withCredentials: true` so the browser sends the httpOnly cookie automatically (CORS is already configured with `allowCredentials: true`):

```js
// NotificationContext.jsx — remove mc_token entirely
const es = new EventSource(`${BASE_URL}/api/notifications/stream`, { withCredentials: true })
```

For the license upload, remove the manual token header and rely on the httpOnly cookie via `credentials: 'include'` (use the existing `api.upload()` helper which already sets this):

```js
// Profile.jsx — use the api.upload helper instead of raw fetch
const updated = await api.upload('/api/auth/me/license', formData)
```

> ⚠️ **Do not** fix this by storing the JWT in `localStorage` — that would reintroduce XSS-accessible token storage.

---

### B-03 🟡 LOW — Weak Password Policy

**OWASP**: A07:2021 – Identification and Authentication Failures

**Description**  
Password validation requires only `@Size(min=8)`. There are no complexity requirements (uppercase, digit, special character). An 8-character all-lowercase password is accepted, making credential-stuffing and dictionary attacks easier.

**Location**  
`src/main/java/com/masterchefcuts/dto/RegisterRequest.java`  
`src/main/java/com/masterchefcuts/dto/ResetPasswordRequest.java`

**Recommendation**  
Add a `@Pattern` constraint aligned with NIST SP 800-63B (focus on length, optionally add complexity):

```java
@Size(min = 10, message = "Password must be at least 10 characters")
@Pattern(
    regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
    message = "Password must contain at least one letter and one number"
)
private String password;
```

Also add the constraint to the frontend `RegisterForm` and the reset-password page for a consistent user experience.

---

### B-04 🟡 LOW — Missing HTTP Security Response Headers

**OWASP**: A05:2021 – Security Misconfiguration

**Description**  
Spring Security's defaults provide `X-Content-Type-Options: nosniff` and disable `X-XSS-Protection`. However, several important headers are absent:

| Header | Risk if Missing |
|--------|----------------|
| `Content-Security-Policy` | No mitigation of script injection |
| `Strict-Transport-Security` (HSTS) | Allows HTTP downgrade attacks |
| `X-Frame-Options` | Clickjacking possible |
| `Referrer-Policy` | URL path leaked to third parties |
| `Permissions-Policy` | Browser features (mic, camera, geolocation) unrestricted |

**Location**  
`src/main/java/com/masterchefcuts/config/SecurityConfig.java`

**Recommendation**  
Add a `HeadersConfigurer` block in `SecurityConfig`:

```java
http.headers(h -> h
    .contentSecurityPolicy(c -> c.policyDirectives(
        "default-src 'self'; " +
        "script-src 'self' 'nonce-{nonce}' https://js.stripe.com; " +
        "frame-src https://js.stripe.com; " +
        "img-src 'self' data: https://storage.googleapis.com; " +
        "connect-src 'self' https://api.stripe.com https://*.sentry.io"
    ))
    .frameOptions(f -> f.deny())
    .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
    .permissionsPolicy(p -> p.policy("geolocation=(), microphone=(), camera=()"))
);
```

HSTS is best enforced at the load balancer / Cloud Run ingress level (or use Spring's `hstsConfig` with `includeSubDomains`).

---

### B-05 🟡 LOW — CORS Wildcard `allowedHeaders("*")`

**OWASP**: A05:2021 – Security Misconfiguration

**Description**  
`CorsConfig.java` sets `config.setAllowedHeaders(List.of("*"))`, accepting any request header. While not directly exploitable, it removes an enforcement layer and could allow unusual headers to be proxied in unexpected ways.

**Location**  
`src/main/java/com/masterchefcuts/config/CorsConfig.java`

**Recommendation**  
Restrict to the known required set:

```java
config.setAllowedHeaders(List.of(
    "Content-Type", "Authorization", "X-XSRF-TOKEN", "X-Requested-With"
));
```

---

### F-02 🟡 LOW — SSE `?token=` URL Pattern Would Expose JWT in Logs if Incorrectly Fixed

**OWASP**: A02:2021 – Cryptographic Failures

**Description**  
`NotificationContext.jsx` constructs `new EventSource(url + '?token=' + token)`. If finding F-01 were "fixed" by storing the JWT in `localStorage` and reading it here, the token would appear in:
- HTTP server access logs (full URL)
- Browser history
- Referrer headers to third parties
- Sentry breadcrumbs / replay

This is currently not exercised (F-01 blocks it), but the pattern is dangerous.

**Recommendation**  
Use `withCredentials: true` on `EventSource` as described in F-01. Remove the `?token=` query parameter construction entirely. The backend `JwtAuthFilter` already reads the httpOnly cookie if no `Authorization` header is present, and browsers automatically send cookies on credentialed `EventSource` requests.

---

### B-06 ℹ️ INFO — No Catch-All Exception Handler

**OWASP**: A05:2021 – Security Misconfiguration

**Description**  
`GlobalExceptionHandler` handles specific exception types but has no `@ExceptionHandler(Exception.class)` catch-all. Unhandled exceptions fall through to Spring Boot's `BasicErrorController` (the `/error` endpoint). Spring Boot 3.x defaults to `server.error.include-message=never` and `server.error.include-stacktrace=never`, so no sensitive data is exposed by default — but this relies on the defaults being unchanged.

Additionally, `handleIllegalState` and `handleRuntime` return `ex.getMessage()` directly to the client. Internal messages may include implementation details (e.g., file paths, class names) for unexpected inputs.

**Location**  
`src/main/java/com/masterchefcuts/exception/GlobalExceptionHandler.java:44,50`

**Recommendation**  
1. Explicitly set in `application.properties` (doesn't currently set these):
   ```properties
   server.error.include-message=never
   server.error.include-stacktrace=never
   server.error.include-exception=false
   ```
2. Add a catch-all handler:
   ```java
   @ExceptionHandler(Exception.class)
   public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
       log.error("Unhandled exception", ex);
       return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
   }
   ```
3. For `RuntimeException` and `IllegalStateException`, return a generic message rather than `ex.getMessage()`.

---

### F-03 ℹ️ INFO — `VITE_LOG_LEVEL=debug` in `.env.development`

**Description**  
`.env.development` sets `VITE_LOG_LEVEL=debug`, enabling verbose console output including all API requests and response bodies. This file is committed to the repository.

**Assessment**  
✅ Low risk — `VITE_LOG_LEVEL=debug` only activates in Vite dev server mode (`npm run dev`), not in production builds. `logger.js` defaults to `warn` when `import.meta.env.PROD` is true. No action required, but confirm CI/CD builds do not accidentally set `NODE_ENV=development`.

---

### F-04 ℹ️ INFO — `mc_user` Profile Cache in `localStorage`

**Description**  
`AuthContext.jsx` caches `{ id, name, email, role, ... }` in `localStorage` as `mc_user`. This is not a JWT; no token is stored in `localStorage`.

**Assessment**  
✅ Intentional design. Profile data is non-sensitive (already visible to the authenticated user). The JWT lives only in the `mc_auth` httpOnly cookie. The cache is cleared on 401 (session expiry). No remediation needed.

---

## What Was Confirmed Secure

| Area | Status | Notes |
|------|--------|-------|
| SQL Injection | ✅ No risk | All queries use Spring Data / parameterized JPQL |
| JWT storage | ✅ Secure | httpOnly, SameSite=Strict, Secure cookie only |
| JWT algorithm | ✅ Secure | JJWT 0.12.5, HMAC-SHA256, `alg:none` rejected by default |
| CSRF | ✅ Mitigated | Cookie-based CSRF (`XSRF-TOKEN` non-httpOnly + `X-XSRF-TOKEN` header) |
| Password hashing | ✅ BCrypt | Spring Security BCryptPasswordEncoder |
| Stripe webhook | ✅ Verified | `Webhook.constructEvent()` with secret; rejects on blank secret |
| Actuator exposure | ✅ Locked | Only `health` (public) + `metrics` (ADMIN); `/actuator/**` blocked |
| File upload | ✅ Validated | MIME allowlist (JPEG/PNG/WebP), 5MB limit, extension from MIME not filename |
| XSS (frontend) | ✅ None found | No `dangerouslySetInnerHTML`, no `eval`, no `innerHTML` assignments |
| Open redirects | ✅ None | Only redirect is Stripe Connect (server-controlled URL) |
| Error response sanitization | ✅ Filtered | `sanitizeErrorBody()` strips `timestamp`, `status`, `path`, `trace` |
| Rate limiting (auth) | ✅ Active | 10 req/60s per IP on all auth paths, rightmost X-Forwarded-For |
| CORS origins | ✅ Env-driven | Production allows only `masterchefcuts.com` + Cloud Run URL |
| Refresh token rotation | ✅ Implemented | New refresh token issued on each use |
| Forgot-password enumeration | ✅ Silent | Returns 200 regardless of whether email exists |
| Source maps | ✅ Disabled | `build.sourcemap: false` in `vite.config.js` |
| SSRF | ✅ No risk | No external URL fetching from user input; Stripe SDK manages its own HTTP |
| SQL logging in prod | ✅ Disabled | `spring.jpa.show-sql=false` in `application-prod.properties` |
| `.env` secrets in Git | ✅ Gitignored | `.env`, `.env.local` excluded; Stripe test key not committed |

---

## Dependency Versions

| Dependency | Version | Status |
|-----------|---------|--------|
| Spring Boot | 3.4.3 | ✅ Maintained |
| JJWT | 0.12.5 | ✅ Current stable |
| stripe-java | 25.3.0 | ✅ Current |
| google-cloud-storage | 2.37.0 | ✅ Maintained |
| sentry-spring-boot | 7.14.0 | ✅ Current |
| React | 19.1.1 | ✅ Current |
| react-router-dom | 7.8.2 | ✅ Current |
| @stripe/stripe-js | 4.10.0 | ✅ Current |
| Vite | 7.1.2 | ✅ Current |

> Run `mvn dependency-check:check` (OWASP Dependency Check plugin) and `npm audit` as part of CI/CD to catch future CVEs automatically.

---

## Remediation Priority

| Priority | ID | Action |
|----------|----|--------|
| **P1 — Before launch** | F-01 | Fix `mc_token` bug — use `withCredentials: true` for SSE, `api.upload()` for license |
| **P1 — Before launch** | B-01 | Hash reset/verification tokens with SHA-256 before DB storage |
| **P1 — Before launch** | B-06 | Add `server.error.include-message=never` and catch-all handler |
| **P2 — Shortly after** | B-02 | Extend rate limiter to mutation endpoints |
| **P2 — Shortly after** | B-04 | Add security response headers (CSP, X-Frame-Options, Referrer-Policy) |
| **P3 — Backlog** | B-03 | Strengthen password policy (min 10 chars + letter + digit) |
| **P3 — Backlog** | B-05 | Narrow CORS `allowedHeaders` to known set |
| **P3 — Backlog** | F-02 | Remove `?token=` SSE URL pattern after F-01 fix |

---

*Report generated by GitHub Copilot security scan using workspace source analysis. All findings are based on static review of the committed source code. Dynamic / penetration testing (fuzzing, runtime exploitation) is out of scope.*
