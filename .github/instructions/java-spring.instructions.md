---
applyTo: "**/*.java"
---

# Java / Spring Boot Conventions — MasterChef Cuts

## Package Structure

All code lives under `com.masterchefcuts`:
- `controllers/` — REST endpoints, one class per domain, no business logic
- `services/` — business logic, transaction boundaries, one class per domain
- `repositories/` — Spring Data JPA interfaces only, custom queries via `@Query`
- `dto/` — separate request and response DTOs; no entity exposure over the wire
- `model/` — JPA entities; UUID PK for user-facing entities, Long for relational joins
- `enums/` — domain enumerations (`Role`, `AnimalType`, `ListingStatus`, `DisputeType`)
- `exception/` — extend `AppException`; always handled by `GlobalExceptionHandler`
- `filter/` — `JwtAuthFilter`, `RateLimitFilter`, `CorrelationIdFilter`
- `config/` — `SecurityConfig`, `JwtUtil`, `CorsConfig`, `ResendConfig`

## Entity Conventions

- UUID primary keys: use `@UuidGenerator` on String `id` field
- Long auto-increment PKs: `@GeneratedValue(strategy = GenerationType.IDENTITY)`
- Relations: `@ManyToOne` for FK references, `@OneToMany(cascade = CascadeType.ALL)` for owned collections
- No `@Transient` fields in entities — put computed logic in services

## Security

- All endpoints except `/api/auth/**` require JWT; configured in `SecurityConfig`
- Never log `Authorization` headers, passwords, `resetToken`, `verificationToken`
- Stripe webhook endpoints must verify signatures via `Stripe.setApiKey` + `Webhook.constructEvent`

## Database

- SQL Server dialect — use `NVARCHAR`, `DATETIME2`, `BIT` (not PostgreSQL types)
- Flyway: add `V{N+1}__{description}.sql` in `src/main/resources/db/migration/sqlserver/` — never edit existing
- Use `@Transactional` at service layer, not controller layer

## Testing

- JUnit 5 + Spring Boot Test
- JaCoCo coverage minimum: **90%** (enforced by Maven failsafe)
- Excluded from thresholds: `PaymentService`, `CorrelationIdFilter`
- Use `@WebMvcTest` for controller slice tests, `@DataJpaTest` for repo tests
- Mock external services (Stripe, Resend, GCS) — never call real APIs in tests
