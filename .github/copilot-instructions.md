# MasterChef Cuts — Copilot Instructions

Farm-to-table marketplace connecting farmers (sellers) with buyers for custom butchered meat cuts.

## Architecture

- **Backend**: `rikeroleg/MasterCheifCuts` — Spring Boot 3.4.3 / Java 17, this repo
- **Frontend**: `rikeroleg/masterchefcutsUI` — React 19 / Vite, separate repo
- **DB**: SQL Server 2022 (Azure/Cloud Run), ORM via Spring Data JPA + Hibernate
- **Migrations**: Flyway — `src/main/resources/db/migration/sqlserver/` (V-prefixed, never edit existing)
- **CI/CD**: GitHub Actions → Google Cloud Run `us-central1`

## Tech Stack Quick Reference

| Layer | Details |
|-------|---------|
| Language | Java 17, Spring Boot 3.4.3 |
| Security | Spring Security + JWT (jjwt 0.12.5), `JwtAuthFilter`, `RateLimitFilter`, `CorrelationIdFilter` |
| Payments | Stripe Java SDK 25.3.0 — PaymentIntents + Connect Express (85% farmer / 15% platform) |
| Email | Resend Java SDK 3.1.0 |
| Storage | Google Cloud Storage 2.37.0 — listing photos (GCP profile) |
| Monitoring | Sentry (auto-configured via `SENTRY_DSN` env var) |
| Build | Maven (`./mvnw`) |
| Container | Multi-stage Docker: `eclipse-temurin:17-jdk-jammy` → `eclipse-temurin:17-jre-jammy` |

## Domain Model

Entities in `src/main/java/com/masterchefcuts/model/`:

- `Participant` — single user entity, roles: `BUYER`, `FARMER`, `ADMIN`. UUID pk.
- `Listing` — animal posted by farmer. References `Participant`. OneToMany `Cut`.
- `Cut` — named portion within a listing (e.g. "Ribeye"). Claimed by a buyer.
- `Claim` — buyer reservation with expiry. `ClaimExpiryScheduler` releases unpaid claims.
- `Order` — completed payment; `stripePaymentIntentId` is idempotency key against duplicate webhooks.
- `Review` — buyer review of listing (1–5 rating).
- `Dispute` — buyer/farmer dispute on a claim.
- `AnimalRequest` — buyer's open request for a specific animal/cuts.
- `Message` — direct messaging between participants.
- `Notification` — in-app via SSE. `ConcurrentHashMap<String, SseEmitter>` keyed by user ID.

## Code Conventions

- Package root: `com.masterchefcuts`
- One service class per domain, one controller per domain
- DTOs in `dto/` — separate request/response objects
- Exceptions: extend `AppException`; handled by `GlobalExceptionHandler`
- Enums in `enums/` — `Role`, `AnimalType`, `ListingStatus`, `DisputeType`, etc.
- Security: all endpoints except auth require JWT; security config in `config/SecurityConfig.java`
- Sensitive data: never log JWT tokens, passwords, or Stripe secret keys
- Feature flags: check `features.email-verification` before gating email flows

## Build & Test

```bash
# Run tests
./mvnw test

# Build + test
./mvnw clean verify

# Run locally (requires SQL Server container running)
docker compose up sqlserver sqlserver-setup --detach
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# Backend on http://localhost:8080
# Credentials: src/main/resources/application-local-secrets.properties (gitignored)
```

- JaCoCo minimum coverage: **90%**. Tests live in `src/test/java/`.
- `PaymentService` and `CorrelationIdFilter` are excluded from coverage thresholds.
- Never skip tests. Never push to `main` without green CI.

## Flyway Migration Rules

- Files in `src/main/resources/db/migration/sqlserver/` named `V{N}__{description}.sql`
- **Never modify an existing migration** — always add a new version
- SQL Server dialect: use `NVARCHAR`, `DATETIME2`, SQL Server syntax (not PostgreSQL/MySQL)

## Stripe Webhook Safety

- Verify webhook signatures before processing (`StripeWebhookController`)
- Use `stripePaymentIntentId` as idempotency key in `Order` to prevent duplicate processing
- Never trust client-side payment amounts — always calculate server-side

## Environment Variables (production)

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, `RESEND_API_KEY`, `GCS_BUCKET_NAME`, `SENTRY_DSN`, `STRIPE_PLATFORM_ACCOUNT_ID`

See `src/main/resources/application.properties` for full list with placeholder values.
