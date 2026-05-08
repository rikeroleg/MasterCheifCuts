<!-- SPDX-License-Identifier: MIT -->
<!-- AI-CONTEXT: Comprehensive project context for all AI agents working on MasterChef Cuts -->

# MasterChef Cuts — Project Context for AI Agents

> **Last updated**: April 25, 2026
> **Purpose**: Single source of truth for any AI agent (Copilot, Claude, etc.) working on this codebase. Read this file first before touching any code.

---

## 1. What Is MasterChef Cuts?

MasterChef Cuts is a **farm-to-table marketplace** that connects local farmers (sellers) with buyers who want fresh, custom butchered meat cuts. The platform supports beef, pork, and lamb. Farmers post listings for whole or half animals broken into named cuts; buyers browse, claim individual cuts, pay through Stripe, and receive their order on a scheduled processing date.

**Core workflow:**

```
Farmer creates Listing (animal + cuts)
  → Buyer browses & claims a Cut
    → Buyer pays via Stripe (Stripe Connect — 85% to farmer, 15% platform fee)
      → Order recorded → farmer gets payout
        → Both sides get notifications + emails
```

**Subdomain**: `masterchefcuts.com` (production)  
**UI repo**: `rikeroleg/masterchefcutsUI` (separate frontend repository — React/Vite)  
**Backend repo**: `rikeroleg/MasterCheifCuts` (this repository)

---

## 2. Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.3 |
| Security | Spring Security + JWT (jjwt 0.12.5) |
| Database | Microsoft SQL Server 2022 (Azure / Cloud Run) |
| ORM | Spring Data JPA + Hibernate (SQLServerDialect) |
| Migrations | Flyway (SQL Server dialect, `classpath:db/migration/sqlserver`) |
| Payments | Stripe Java SDK 25.3.0 (PaymentIntents + Stripe Connect Express) |
| Email | Resend Java SDK 3.1.0 |
| File Storage | Google Cloud Storage (`google-cloud-storage 2.37.0`) — listing photo uploads |
| Error Monitoring | Sentry (SDK auto-configured via `SENTRY_DSN` env var) |
| Build | Maven (`./mvnw`) |
| Container | Docker (multi-stage: `eclipse-temurin:17-jdk-jammy` → `eclipse-temurin:17-jre-jammy`) |
| CI/CD | GitHub Actions → Google Cloud Run (`us-central1`) |
| Code Quality | Snyk security scanning (`.github/workflows/snyk-security.yml`) |

---

## 3. Repository Layout

```
MasterCheifCuts/                  ← root (this repo, submodule inside aidevops)
├── src/
│   └── main/
│       ├── java/com/masterchefcuts/
│       │   ├── MasterchefcutsApplication.java   ← Spring Boot entry point
│       │   ├── config/                          ← SecurityConfig, JwtUtil, CorsConfig, ResendConfig
│       │   ├── controllers/                     ← REST controllers (one per domain)
│       │   ├── dto/                             ← Request/Response DTOs
│       │   ├── enums/                           ← Domain enumerations
│       │   ├── exception/                       ← AppException + GlobalExceptionHandler
│       │   ├── filter/                          ← JwtAuthFilter, RateLimitFilter, CorrelationIdFilter
│       │   ├── model/                           ← JPA entities
│       │   ├── repositories/                    ← Spring Data JPA repositories
│       │   ├── scheduler/                       ← ClaimExpiryScheduler
│       │   └── services/                        ← Business logic (one per domain)
│       └── resources/
│           ├── application.properties           ← base config (env-var driven)
│           ├── application-prod.properties      ← production overrides
│           ├── application-gcp.properties       ← GCP profile (GCS storage)
│           └── db/migration/sqlserver/          ← Flyway SQL migrations (V1–V7)
├── masterChefCuts/                              ← secondary Maven module (same codebase structure)
├── Dockerfile                                   ← multi-stage production Docker build
├── compose.yaml                                 ← local dev: SQL Server 2022 + app
├── pom.xml                                      ← Maven POM, groupId=com.masterchefcuts
└── .github/
    └── workflows/
        ├── ci.yml                               ← PR builds + tests
        ├── deploy.yml                           ← deploy to GCP Cloud Run on main push
        └── snyk-security.yml                   ← dependency vulnerability scanning
```

---

## 4. Domain Model (JPA Entities)

### Participant (users table)
**File**: `src/main/java/com/masterchefcuts/model/Participant.java`

The single user entity. Roles: `BUYER`, `FARMER`, `ADMIN`.

| Field | Notes |
|-------|-------|
| `id` | UUID string, `@UuidGenerator` |
| `email` | unique, not null |
| `password` | BCrypt hashed |
| `role` | `Role` enum: `BUYER`, `FARMER`, `ADMIN` |
| `approved` | FARMER approval gate (default true) — admin must approve before listing |
| `shopName` | Farmer's shop/farm display name |
| `bio`, `certifications` | Farmer profile enrichment (max 500 chars each) |
| `notificationPreference` | `ALL`, `IMPORTANT_ONLY` |
| `emailPreference` | `ALL`, `IMPORTANT`, `NONE` |
| `stripeAccountId` | Stripe Connect Express account ID for farmer payouts |
| `stripeOnboardingComplete` | boolean — farmer can only receive payouts after onboarding |
| `refreshToken`, `refreshTokenExpiry` | Rotating opaque refresh tokens |
| `resetToken`, `resetTokenExpiry` | Password reset flow |
| `emailVerified`, `verificationToken` | Email verification flow (gated by `features.email-verification`) |
| `totalSpent` | Running buyer spend total |

### Listing (listings table)
**File**: `src/main/java/com/masterchefcuts/model/Listing.java`

An animal posted for sale by a farmer.

| Field | Notes |
|-------|-------|
| `id` | Long, auto-increment |
| `farmer` | ManyToOne → `Participant` |
| `animalType` | `AnimalType` enum: `BEEF`, `PORK`, `LAMB` |
| `breed` | e.g. "Angus", "Berkshire" |
| `weightLbs`, `pricePerLb` | animal weight + per-pound price |
| `sourceFarm` | farm name (display) |
| `description` | free text |
| `imageUrl` | up to 512 chars — GCS URL or local path |
| `zipCode` | buyer search / proximity filter |
| `status` | `ListingStatus`: `ACTIVE`, `FULLY_CLAIMED`, `CANCELLED` |
| `processingDate` | scheduled butcher/processing date |
| `cuts` | OneToMany → `Cut` (cascaded) |

### Cut (cuts table)
**File**: `src/main/java/com/masterchefcuts/model/Cut.java`

A single named cut portion within a listing (e.g. "Ribeye", "Shoulder").

| Field | Notes |
|-------|-------|
| `id` | Long, auto-increment |
| `listing` | ManyToOne → `Listing` |
| `label` | cut name (e.g. "Brisket") |
| `weightLbs` | optional weight |
| `claimed` | boolean — true when buyer reserves it |
| `claimedBy` | ManyToOne → `Participant` (buyer) |
| `claimedAt` | timestamp |

### Claim (claims table)
**File**: `src/main/java/com/masterchefcuts/model/Claim.java`

A buyer's reservation of a specific cut. Has an expiry (unpaid claims expire via `ClaimExpiryScheduler`).

| Field | Notes |
|-------|-------|
| `buyer` | ManyToOne → `Participant` |
| `listing` | ManyToOne → `Listing` |
| `cut` | OneToOne → `Cut` |
| `claimedAt`, `expiresAt` | expiry window for payment |
| `paid` | boolean — true after Stripe webhook confirms payment |

### Order (orders table)
**File**: `src/main/java/com/masterchefcuts/model/Order.java`

A completed payment record. UUID primary key.

| Field | Notes |
|-------|-------|
| `id` | UUID string |
| `stripePaymentIntentId` | unique — idempotency key against duplicate webhooks |
| `participantId` | buyer's participant UUID |
| `status` | e.g. `PENDING`, `PAID`, `REFUNDED` |
| `amountCents` | total in cents |
| `items` | JSON snapshot of ordered cuts |
| `deliveryStreet/City/State/Zip` | address snapshot at order time |

### Review (reviews table)
**File**: `src/main/java/com/masterchefcuts/model/Review.java`

Buyer review of a listing. Rating 1–5 + optional comment. `featured` flag for homepage display.

### Dispute (disputes table)
**File**: `src/main/java/com/masterchefcuts/model/Dispute.java`

Buyer/farmer dispute on a claim. Types via `DisputeType` enum. Status: `OPEN` → `RESOLVED`.

### AnimalRequest (animal_requests table)
**File**: `src/main/java/com/masterchefcuts/model/AnimalRequest.java`

A buyer's public request for a specific animal/breed/cuts. Farmers can fulfill by creating a listing that matches. Status: `OPEN`, `FULFILLED`, `CLOSED`. Includes `cutLabels` (ElementCollection).

### Message (messages table)
**File**: `src/main/java/com/masterchefcuts/model/Message.java`

Direct messaging between participants (buyer ↔ farmer). Thread view via `MessageService.getThreads()`.

### Notification (notifications table)
**File**: `src/main/java/com/masterchefcuts/model/Notification.java`

In-app notifications pushed via **Server-Sent Events (SSE)**. `NotificationService` keeps a `ConcurrentHashMap<String, SseEmitter>` (one per user, last-write-wins).

### WaitlistEntry (waitlist_entries table)
**File**: `src/main/java/com/masterchefcuts/model/WaitlistEntry.java`

A buyer joins a waitlist for a fully-claimed listing. Unique constraint on (buyer_id, listing_id).

### Referral (referrals table)
**File**: `src/main/java/com/masterchefcuts/model/Referral.java`

Tracks referral relationships between participants. `referrerId` → `referredId` (unique).

### AuditEvent (audit_events table)
**File**: `src/main/java/com/masterchefcuts/model/AuditEvent.java`

Immutable audit log written by `AuditService`.

### WebhookEvent (webhook_events table)
**File**: `src/main/java/com/masterchefcuts/model/WebhookEvent.java`

Idempotency table for Stripe webhooks — prevents duplicate order processing.

### CartItem / Product / Purchase
Supporting e-commerce models for cart-based checkout flow.

---

## 5. REST API Endpoints

Base path: `/api/`  
Auth: JWT Bearer token (header) or `?token=` query param (SSE only).

### Auth — `/api/auth/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/register` | Public | Register buyer or farmer |
| GET | `/verify-email?token=` | Public | Email verification |
| POST | `/resend-verification` | Public | Re-send verification email |
| POST | `/login` | Public | Returns `accessToken` + `refreshToken` |
| GET | `/me` | Bearer | Current user profile |
| PATCH | `/me` | Bearer | Update profile |
| POST | `/refresh` | Public | Rotate refresh token |
| POST | `/forgot-password` | Public | Send reset email |
| POST | `/reset-password` | Public | Apply new password with token |

### Listings — `/api/listings/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/` | Public | Browse with filters: `zip`, `animal`, `farmerId`, `maxPrice`, `q`, `breed`, pagination |
| GET | `/{id}` | Public | Single listing detail |
| GET | `/my` | FARMER | Farmer's own listings |
| POST | `/` | FARMER | Create listing (with cuts). Farmer must be approved + Stripe onboarded |
| PATCH `\|` PUT | `/{id}/processing-date` | FARMER | Set processing date |
| PATCH | `/{id}` | FARMER | Update listing (description, price, etc.) |
| POST | `/{id}/upload-image` | FARMER | Multipart image upload → GCS (up to 6MB) |
| DELETE | `/{id}` | FARMER | Cancel listing |
| GET | `/{id}/reviews` | Public | Reviews for a listing |
| POST | `/{id}/comments` | Bearer | Post a comment |
| GET | `/{id}/comments` | Public | Get listing comments |

### Claims — `/api/listings/{listingId}/cuts/{cutId}/claim`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/listings/{listingId}/cuts/{cutId}/claim` | BUYER | Claim a cut (creates `Claim`, marks cut claimed) |
| DELETE | `/api/claims/{id}` | BUYER | Release unpaid claim |

### Payments — `/api/payments/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/intent` | BUYER | Single cut PaymentIntent |
| POST | `/cart-intent` | BUYER | Cart (multi-cut) PaymentIntent |
| POST | `/webhook` | Public (Stripe sig) | Stripe payment webhook → creates `Order`, marks claims paid |
| POST | `/connect-webhook` | Public (Stripe sig) | Stripe Connect account.updated webhook |

**Platform fee**: 15% taken at PaymentIntent creation; 85% transferred to farmer's Stripe Connect account.

### Stripe Connect — `/api/connect/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/onboard` | FARMER | Create/refresh Stripe Express onboarding link |
| GET | `/dashboard` | FARMER | Stripe Express dashboard login link |
| GET | `/status` | FARMER | Current Connect onboarding status |

### Messages — `/api/messages/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/threads` | Bearer | All conversation threads (latest message per thread) |
| GET | `/?with={participantId}` | Bearer | Full conversation |
| POST | `/` | Bearer | Send message (`recipientId`, `content`) |
| POST | `/{id}/read` | Bearer | Mark message read |

### Notifications — `/api/notifications/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/stream` | Bearer (token param) | SSE stream — real-time push |
| GET | `/` | Bearer | Paginated notification list |
| POST | `/{id}/read` | Bearer | Mark read |
| POST | `/read-all` | Bearer | Mark all read |

### Reviews — `/api/reviews/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/` | BUYER | Submit review |
| GET | `/featured` | Public | Featured reviews for homepage |
| GET | `/farmer/{farmerId}` | Public | All reviews for a farmer |

### Animal Requests — `/api/animal-requests/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/` | Public | Browse open requests |
| POST | `/` | BUYER | Create request |
| DELETE | `/{id}` | BUYER | Cancel request |
| POST | `/{id}/fulfill` | FARMER | Mark request as fulfilled (links to listing) |

### Disputes — `/api/disputes/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/` | Bearer | File a dispute |
| GET | `/my` | Bearer | My disputes |
| GET | `/{id}` | Bearer | Dispute detail |

### Waitlist — `/api/waitlist/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/listings/{id}` | BUYER | Join waitlist |
| DELETE | `/listings/{id}` | BUYER | Leave waitlist |
| GET | `/listings/{id}/position` | BUYER | Position in waitlist |

### Referrals — `/api/referrals/*`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| GET | `/my` | Bearer | My referral stats |

### Contact — `/api/contact`
| Method | Path | Auth | Notes |
|--------|------|------|-------|
| POST | `/api/contact` | Public | Contact form → forwarded to `RESEND_SUPPORT_EMAIL` |

### Admin — `/api/admin/*` (ADMIN role only)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/users` | All users |
| PATCH | `/users/{id}/approve` | Approve farmer |
| PATCH | `/users/{id}/reject` | Reject/unapprove farmer |
| DELETE | `/listings/{id}` | Remove any listing |
| GET | `/users/{id}` | User detail |
| GET | `/stats` | Platform statistics |
| GET | `/orders` | All orders |
| POST | `/orders/{id}/refund` | Issue Stripe refund |
| GET | `/financials/summary` | Financial summary (`?from=&to=` date range) |
| GET | `/disputes` | All disputes |
| POST | `/disputes/{id}/resolve` | Resolve a dispute |
| GET | `/reviews` | All reviews |
| POST | `/reviews/{id}/feature` | Toggle featured status |

### Actuator
- `GET /actuator/health` — public
- `GET /actuator/metrics` — ADMIN only

---

## 6. Security Architecture

- **Stateless JWT** — access tokens signed with HS256 (`JWT_SECRET` env var, min 32 chars, no default in prod)
- **Refresh tokens** — opaque UUID, stored hashed in DB, rotated on every use, 7-day expiry
- **BCrypt** password hashing
- **CSRF** — enabled with `CookieCsrfTokenRepository` (httpOnly=false for SPA); Stripe webhook endpoints explicitly excluded
- **CORS** — profile-driven: `localhost:5173/5174` in local, `masterchefcuts.com` + Cloud Run URL in prod
- **Role-based**: `@PreAuthorize("hasRole('FARMER')")`, `@PreAuthorize("hasRole('ADMIN')")`, `@AuthenticationPrincipal String participantId`
- **Rate limiting**: `RateLimitFilter` on requests
- **Correlation IDs**: `CorrelationIdFilter` adds `correlationId` to every log line (MDC)
- **JWT in SSE**: `JwtAuthFilter` falls back to `?token=` query param for EventSource connections (browsers cannot set Authorization headers on SSE)

---

## 7. Configuration & Environment Variables

All config is environment-variable driven (no secrets in source). Key vars:

| Variable | Required | Description |
|----------|----------|-------------|
| `JWT_SECRET` | **YES** | Min 32 chars. No default in prod — app fails fast without it |
| `STRIPE_SECRET_KEY` | YES | `sk_test_...` dev / `sk_live_...` prod |
| `STRIPE_WEBHOOK_SECRET` | YES | Webhook signature secret |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASS` | YES | SQL Server connection |
| `RESEND_API_KEY` | YES (prod) | Resend transactional email API key |
| `RESEND_FROM` | Optional | Sender address (must be verified domain in prod) |
| `RESEND_SUPPORT_EMAIL` | Optional | Contact form destination |
| `GCS_BUCKET` | Prod (gcp profile) | GCS bucket name for listing images |
| `GCP_PROJECT_ID` | Prod (gcp profile) | GCP project ID |
| `SENTRY_DSN` | Optional | Sentry error monitoring (disabled when empty) |
| `APP_BASE_URL` | Optional | Frontend URL for email links (default: `http://localhost:5173`) |
| `CORS_ALLOWED_ORIGINS` | Optional | Comma-separated CORS origins |
| `EMAIL_VERIFICATION_ENABLED` | Optional | Feature flag — default false locally |
| `SPRING_PROFILES_ACTIVE` | Optional | `local` or `prod` (or `prod,gcp`) |
| `STRIPE_CONNECT_RETURN_URL` | Optional | Post-onboarding redirect |
| `STRIPE_CONNECT_REFRESH_URL` | Optional | Onboarding refresh redirect |
| `STRIPE_CONNECT_REQUIRED` | Optional | Default true — skip in dev by setting false |

### Profiles
- **local** — default. `trustServerCertificate=true`, email verification disabled, localhost CORS
- **prod** — GCS storage, email verification required, Flyway validate-only DDL, production CORS
- **gcp** — enables `GcsStorageService` instead of `LocalStorageService` for image uploads

---

## 8. Database & Migrations

- **Database**: Microsoft SQL Server 2022
- **ORM**: Hibernate with `SQLServerDialect`
- **Migrations**: Flyway at `classpath:db/migration/sqlserver/`

| Migration | Description |
|-----------|-------------|
| V1 | Baseline schema (all core tables) |
| V2 | Missing tables added |
| V3 | Missing columns |
| V4 | More missing columns |
| V5 | Refresh token columns on `participants` |
| V6 | `email_preference` column |
| V7 | `featured` column on `reviews` |

Local dev: `compose.yaml` spins up SQL Server 2022 container + creates `olegtest` database automatically.

---

## 9. Business Logic Highlights

### Claim Expiry (ClaimExpiryScheduler)
Unpaid claims expire after a configured window. `ClaimExpiryScheduler` runs on a schedule, finds expired unpaid claims, releases the cut (sets `claimed=false`, clears `claimedBy`), and notifies the farmer.

### Stripe Payments — 15/85 Split
`PaymentService.createCartIntent()` / `createIntent()`:
1. Validates cut availability
2. Calculates total (sum of `cut.weightLbs * listing.pricePerLb`)
3. Creates Stripe PaymentIntent with `application_fee_amount` = 15% of total
4. Routes funds to farmer's `stripeAccountId` via `transfer_data.destination`
5. Webhook (`payment_intent.succeeded`) uses `WebhookEvent` idempotency table to prevent duplicate order creation

### Stripe Connect Onboarding
- Farmer calls `POST /api/connect/onboard` → gets Stripe Express onboarding URL
- After completing, Stripe sends `account.updated` event to `/api/payments/connect-webhook`
- `StripeConnectService` marks `stripeOnboardingComplete=true` on the Participant
- Config flag `stripe.connect.required` can be set false in dev to bypass check

### Storage — Photo Uploads
- `StorageService` interface with two implementations:
  - `GcsStorageService` — Google Cloud Storage (prod/gcp profile)
  - `LocalStorageService` — local filesystem (dev fallback)
- Max upload: 6MB (Spring multipart config)
- `imageUrl` stored on `Listing` (max 512 chars)

### Real-time Notifications (SSE)
- `NotificationService` holds `ConcurrentHashMap<String, SseEmitter>` (one per user)
- On notification creation → persists to DB + pushes SSE event
- Frontend subscribes to `GET /api/notifications/stream?token={jwt}` (EventSource)
- Emitters auto-cleaned on connection close/error

### Email Service (Resend)
- All email methods are `@Async`
- Respects `emailPreference` (`ALL`, `IMPORTANT`, `NONE`) per participant
- Emails sent: claim confirmation, order confirmation, claim expiry, listing sold, password reset, email verification, contact form

### Animal Request Marketplace
- Buyers post requests for specific animals/breeds/cuts
- Farmers browse `GET /api/animal-requests` and can fulfill with `POST /{id}/fulfill`
- Status transitions: `OPEN` → `FULFILLED` → `CLOSED`

### Admin Controls
- User approval workflow for farmers (new farmers default `approved=true` but can be toggled)
- Listing moderation (delete any listing)
- Dispute resolution
- Review featuring for homepage
- Financial summary with date range
- Full order management including Stripe refunds

---

## 10. Frontend Integration Notes

- **Frontend repo**: `rikeroleg/masterchefcutsUI` (React + Vite, port 5173 in dev)
- **Background**: Warm golden/orange farm photo — **always use strong text-shadow and/or dark overlays** for readability
- **Auth flow**: SPA stores `accessToken` (short-lived) + `refreshToken` (7-day), calls `/api/auth/refresh` on 401
- **SSE**: Frontend uses `EventSource` with `?token=` param for notifications stream
- **CSRF**: SPA reads CSRF cookie and sends `X-XSRF-TOKEN` header on mutation requests
- **Pagination**: Listings support `page` + `size` query params (default 0/20)
- **PR base**: Backend PRs → `https://github.com/rikeroleg/MasterCheifCuts/pull/<number>`
- **PR base**: Frontend PRs → `https://github.com/rikeroleg/masterchefcutsUI/pull/<number>`

---

## 11. CI/CD Pipeline

### ci.yml — PR Checks
Runs `./mvnw test` with placeholder secrets on every pull request.

### deploy.yml — Production Deploy (push to `main`)
1. `./mvnw test`
2. Authenticate to GCP via `GCP_SA_KEY` secret
3. Build Docker image → push to `us-central1-docker.pkg.dev/gen-lang-client-0273518275/mastercheifcuts/masterchefcuts`
4. Deploy to Cloud Run (us-central1, project `gen-lang-client-0273518275`)

### snyk-security.yml — Dependency Scanning
Snyk vulnerability scan on dependencies.

---

## 12. Enumerations Reference

| Enum | Values |
|------|--------|
| `Role` | `BUYER`, `FARMER`, `ADMIN` |
| `AnimalType` | `BEEF`, `PORK`, `LAMB` |
| `ListingStatus` | `ACTIVE`, `FULLY_CLAIMED`, `CANCELLED` |
| `AnimalRequestStatus` | `OPEN`, `FULFILLED`, `CLOSED` |
| `NotificationType` | `CUT_CLAIMED`, `REQUEST_FULFILLED`, + others |
| `NotificationPreference` | `ALL`, `IMPORTANT_ONLY` |
| `EmailPreference` | `ALL`, `IMPORTANT`, `NONE` |
| `DisputeType` | Defined in `DisputeType.java` |
| `OrderStatus` | Defined in `OrderStatus.java` |

---

## 13. Key Patterns & Conventions

- **Constructor injection via Lombok `@RequiredArgsConstructor`** everywhere (no `@Autowired` on fields)
- **`@AuthenticationPrincipal String participantId`** — JwtAuthFilter sets principal to the participant UUID string
- **`@PreAuthorize` at controller method level** — method security enabled (`@EnableMethodSecurity`)
- **DTOs for all API boundaries** — entities are never returned directly (except some admin endpoints)
- **`@Transactional` on service methods** that modify multiple entities
- **Idempotent webhook processing** — `WebhookEventRepository` stores processed Stripe event IDs
- **Optional storage bean** — `@Autowired(required = false) StorageService storageService` in `ListingService` — gracefully absent in local dev without a storage profile
- **Flyway migrations only** — `spring.jpa.hibernate.ddl-auto=validate` in prod (never `update` or `create`)

---

## 14. Known TODOs / Areas to Improve

(Check `TODO.md` in the aidevops workspace root for the current task list)

- `masterChefCuts/` subfolder appears to be an older/secondary Maven module — may need reconciliation
- Order model uses `String items` (JSON) rather than a proper `@OneToMany` collection — potential refactor
- `Order.status` is a plain String rather than using the `OrderStatus` enum
- SSE emitter map is in-memory — not horizontally scalable (would need Redis pub/sub for multi-instance)
- `totalSpent` on `Participant` is updated manually — could drift from actual orders

---

## 15. Local Development Quickstart

```bash
# 1. Start SQL Server
docker compose up sqlserver -d

# 2. Copy and fill secrets
cp src/main/resources/application-local-secrets.properties.example \
   src/main/resources/application-local-secrets.properties
# Edit and set: DB_PASS, JWT_SECRET, STRIPE_SECRET_KEY, etc.

# 3. Run the app
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# App runs on http://localhost:8080
# Frontend expected on http://localhost:5173
```

Required env vars for local (can use `.env` loaded by compose.yaml):
- `DB_PASS` — SQL Server SA password
- `JWT_SECRET` — at least 32 characters
- `STRIPE_SECRET_KEY` — Stripe test key
- Optional: `RESEND_API_KEY`, `GCS_BUCKET` (skip for local)

---

## 16. Agent-Specific Guidance

### For all agents working in this repo:
1. **Always run Codacy analysis** after editing any Java file (`codacy_cli_analyze` with `rootPath` = workspace path)
2. **Always run security scan** (`codacy_cli_analyze` with `tool=trivy`) after adding any Maven dependency
3. **Never commit secrets** — all secrets are env-var driven; never add default values for sensitive keys
4. **Use Flyway for schema changes** — never modify `spring.jpa.hibernate.ddl-auto`; always create a new `V{n}__description.sql` migration
5. **Maintain DTO separation** — don't expose JPA entities through REST responses (except admin endpoints where intentional)
6. **Test with JWT** — endpoints require valid JWT; local dev has `features.email-verification=false` to allow registration without Resend

### For code changes:
- Package: `com.masterchefcuts`
- Java version: **17** (use Java 17 syntax, no Java 21+ features)
- Spring Boot: **3.4.3**
- ORM: always use `@Transactional` for multi-step DB operations
- Avoid `@Autowired` on fields — use constructor injection
- New controllers → `@RequestMapping("/api/{resource}")` at class level
- New services → annotate with `@Service`, inject via constructor

### For database changes:
- Add migration file: `src/main/resources/db/migration/sqlserver/V{next}__description.sql`
- SQL dialect: T-SQL (SQL Server 2022)
- Never use MySQL/PostgreSQL-specific syntax
- Always test migration locally with `docker compose up sqlserver -d` first

### For payment-related changes:
- Test Stripe webhooks locally with Stripe CLI: `stripe listen --forward-to localhost:8080/api/payments/webhook`
- Always check idempotency — use `WebhookEventRepository` pattern for new webhook handlers
- Platform fee = 15% (`PLATFORM_FEE_RATE = 0.15` in `PaymentService`)

### For frontend-related changes (in `masterchefcutsUI` repo):
- Background is warm golden/orange farm photo — text needs strong shadow or dark card backing
- Always push a PR link after every commit: `https://github.com/rikeroleg/masterchefcutsUI/pull/<number>`

---

## 17. AI DevOps Framework — Parent Workspace

This project lives inside the **AI DevOps Framework** workspace at `c:\Tools\AI\AIdevOps\aidevops\` (deployed to `~/.aidevops/` on the host). All agents working on MasterChef Cuts have access to the full framework tool library. This section maps the most relevant framework capabilities to this project.

### Framework Structure

```
.agents/
├── AGENTS.md                  ← master user guide (read first)
├── prompts/
│   ├── build.txt              ← core quality rules, security, write-time discipline
│   └── worker-efficiency-protocol.md
├── workflows/                 ← named slash-command workflows (/feature, /bug-fix, /pr, etc.)
├── tools/                     ← domain tool guides (git, security, deployment, UI, testing…)
├── services/                  ← third-party service integrations (Stripe, email, hosting…)
├── scripts/                   ← shell helpers deployed to ~/.aidevops/agents/scripts/
└── templates/                 ← brief and task templates
```

### Core Quality Rules (prompts/build.txt)
- Never write to `main`/`master` directly — all edits on a branch/worktree
- Pre-edit gate: `git branch --show-current` — if `main`, STOP and create a branch
- ShellCheck + Secretlint run before every PR (`.agents/scripts/linters-local.sh`)
- Conventional commits: `feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`
- Version bumps happen **after** a feature is confirmed working, not during development
- Never commit secrets — env-var driven config only

### Workflow Commands Relevant to MasterChef Cuts

| Workflow file | Slash command | Purpose |
|---------------|---------------|---------|
| `workflows/git-workflow.md` | `/git` | Branch creation, worktrees, pre-edit gate |
| `workflows/feature-development.md` | `/feature` | Full feature dev lifecycle |
| `workflows/bug-fixing.md` | `/bug-fix` | Minimal-change bug fix with regression test |
| `workflows/pr.md` | `/pr` | PR creation, linting, code audit, merge |
| `workflows/sql-migrations.md` | `/sql-migrations` | Flyway migration authoring (relevant: SQL Server T-SQL) |
| `workflows/preflight.md` | `/preflight` | Quality checks before release |
| `workflows/full-loop.md` | `/full-loop` | End-to-end: task → branch → impl → PR → merge → close |
| `workflows/code-audit-remote.md` | `/code-audit-remote` | CodeRabbit + Codacy + SonarCloud analysis |
| `workflows/changelog.md` | `/changelog` | CHANGELOG.md entry authoring |
| `workflows/release.md` | `/release` | Version bump + GitHub Release |
| `workflows/pulse.md` | internal | Autonomous supervisor dispatch loop |

### Branch Naming for MasterChef Cuts

Use the branch type sub-workflows from `workflows/branch/`:

| Type | File | Branch pattern |
|------|------|---------------|
| Feature | `branch/feature.md` | `feature/{issue}-{slug}` |
| Bug fix | `branch/bugfix.md` | `bugfix/{issue}-{slug}` |
| Hotfix | `branch/hotfix.md` | `hotfix/{issue}-{slug}` |
| Chore | `branch/chore.md` | `chore/{issue}-{slug}` |
| Refactor | `branch/refactor.md` | `refactor/{issue}-{slug}` |

### Tool Guides Directly Applicable to MasterChef Cuts

#### Payments — `services/payments/stripe.md`
- Framework has a full Stripe tool guide (Connect, webhooks, subscriptions, PaymentIntents)
- MasterChef Cuts uses **Stripe Java SDK** (not Node.js) — adapt JS examples to Java
- Key reference: webhook signature verification, idempotency, Connect transfer_data

#### Email — `services/email/email-agent.md`, `services/email/ses.md`
- MasterChef Cuts uses **Resend** (not SES); same concepts apply
- `EmailService` is `@Async` — never call transactional email synchronously
- Email provider config: `configs/email-agent-config.json`

#### Database — `workflows/sql-migrations.md`
- MasterChef Cuts uses **Flyway + SQL Server T-SQL** (not Postgres/MySQL)
- Always create `V{n}__description.sql` in `src/main/resources/db/migration/sqlserver/`
- Never use `IF NOT EXISTS` PostgreSQL syntax — use T-SQL: `IF NOT EXISTS (SELECT * FROM sys.columns WHERE ...)`

#### Security — `tools/security/security-review.md`, `tools/security/security-audit.md`
- Prompt injection defender: `tools/security/prompt-injection-defender.md`
- Security scan: `tools/security/security-scan.md` — maps to Snyk (already in CI)
- After any dependency change: run Codacy/Trivy + `tools/security/security-deps.md`

#### Git / GitHub Actions — `tools/git/github-actions.md`, `tools/git/github-cli.md`
- CI secrets needed: `JWT_SECRET`, `STRIPE_SECRET_KEY`, `GCP_SA_KEY` (already in repo)
- Retry pattern for concurrent pushes (see github-actions.md "Full retry")

#### Deployment — `tools/deployment/coolify.md` (alternative), current: GCP Cloud Run
- MasterChef Cuts deploys to GCP Cloud Run via `.github/workflows/deploy.yml`
- Docker image: `us-central1-docker.pkg.dev/gen-lang-client-0273518275/mastercheifcuts/masterchefcuts`
- Coolify is available as a self-hosted alternative if Cloud Run costs need to be reduced

#### Frontend — `tools/ui/`
- `tools/ui/react-context.md` — React state patterns (relevant to frontend repo)
- `tools/ui/frontend-debugging.md` — HTTP 200 ≠ frontend working; use browser screenshot tool
- `tools/ui/tailwind-css.md` — frontend uses Tailwind (check `masterchefcutsUI` repo)

### Task Lifecycle in This Framework

Every non-trivial change to MasterChef Cuts should follow this lifecycle:

```
1. /define or /new-task → creates brief at todo/tasks/{id}-brief.md
2. Claim task ID via claim-task-id.sh
3. Create branch: git checkout -b feature/{id}-{slug}
4. Implement on branch (NEVER on main)
5. /preflight → linters-local.sh passes
6. /pr create → CodeRabbit + Codacy review
7. Address review findings
8. /pr merge --squash
9. Deploy auto-triggers via deploy.yml (push to main)
```

### Helper Scripts Available

Scripts live at `~/.aidevops/agents/scripts/` (deployed copy) or `.agents/scripts/` (source):

| Script | Relevant use |
|--------|-------------|
| `linters-local.sh` | ShellCheck + Secretlint + markdownlint before PR |
| `worktree-helper.sh add feature/name` | Create linked worktree off main |
| `version-manager.sh release [major\|minor\|patch]` | Bump VERSION + tag |
| `code-audit-helper.sh audit` | Run CodeRabbit + Codacy + SonarCloud |
| `review-bot-gate-helper.sh check {PR#}` | Verify bot review before merge |

### Model Tier Assignment for MasterChef Cuts Tasks

| Task type | Tier | Model |
|-----------|------|-------|
| Simple string/config change, single file, verbatim old/new provided | `tier:simple` | Haiku |
| Standard feature, bug fix, refactor | `tier:standard` | Sonnet |
| Security audit, architecture decision, novel design, DB schema design | `tier:thinking` | Opus |

**Default to `tier:standard` when uncertain.** Haiku will fail on any task requiring judgment calls, cross-file changes, or files > 500 lines.
