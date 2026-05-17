# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status

This is a **range-based reservation platform** designed as MSA. The repo currently holds a **single-module Spring Boot skeleton** (`BookingApplication` only) — modules and features described in `docs/` are the **target design**, not yet implemented. Read `docs/01-overview.md` first to orient on the gap between current code and target.

## Build / run

| Command | Purpose |
|---------|---------|
| `./gradlew build` | Full build + tests |
| `./gradlew bootRun` | Run the app locally |
| `./gradlew test` | All tests |
| `./gradlew test --tests <FQCN>` | Single test class |
| `./gradlew test --tests <FQCN.method>` | Single test method |

Gradle wrapper is pinned to **9.4.1**; toolchain is **Java 25**; Spring Boot **4.0.5**. Don't downgrade without a reason — this combo was deliberately chosen as the latest-stable trio (see prior conversation context in `docs/01-overview.md`).

## Architecture (target — see `docs/02-architecture.md` for full detail)

Four independent Spring Boot services + one shared library:

```
api (8080)          ─ HTTP entry point, auth (JWT issuance), User/Merchant/Resource/AvailableTime CRUD
reservation (8081)  ─ Reservation domain, Redis distributed lock, DB pessimistic lock
payment (8082)      ─ Mock payment processing
notification (8083) ─ Mock notification dispatch
core (library)      ─ BaseEntity, ErrorCode interface, ProblemDetail handler, JwtVerifier
```

Services communicate via:
- **Synchronous**: Spring 6 `RestClient` + Resilience4j (e.g., `reservation → api` for resource validation)
- **Asynchronous**: Kafka (`reservation.created`, `payment.completed`, `payment.failed`, `reservation.cancelled`)

Database-per-service: each service owns its own DB (`db_api`, `db_reservation`, `db_payment`, `db_notification`). **No cross-service FK constraints** — other services' identifiers are stored as plain `BIGINT` columns.

## Conventions that bite if missed

**Package layout**: `com.example.booking.{module}.{layer}`. The base is `com.example.booking` (depth 3), module sits at depth 4 — this is the standard Spring layout (`org.springframework.boot.{module}`). Don't flatten it.

**Response shape** (no envelope):
- Success → return DTO directly with appropriate 2xx (`UserResponse`, etc.) — there is no `{success, data, error}` wrapper
- Failure → throw `BusinessException(errorCode)`; `GlobalExceptionHandler` in core converts to RFC 9457 `application/problem+json` with the right HTTP status
- HTTP status is the source of truth, not a body field

**ErrorCode is split**:
- `core` defines the `ErrorCode` interface and `CommonErrorCode` (only truly cross-cutting: `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `BAD_REQUEST`, `INTERNAL_ERROR`)
- Each service defines its own enum (`ApiErrorCode`, `ReservationErrorCode`, `PaymentErrorCode`, `NotificationErrorCode`) implementing `ErrorCode`
- Don't dump domain codes (e.g., `RESERVATION_CONFLICT`) into core

**Status codes that surprise people**:
- `PAYMENT_FAILED` is **422 Unprocessable Entity**, not 500 — it's a business outcome
- `LOCK_FAILED` is **409 Conflict**, not 429/503

**Kafka publish must be after commit**: always use `@TransactionalEventListener(phase = AFTER_COMMIT)` (or Outbox pattern). Publishing inside `@Transactional` causes ghost events on rollback.

**Concurrency in reservation service** uses Redis distributed lock (Redisson) + DB pessimistic lock + overlap query. **Do NOT add `UNIQUE INDEX (resource_id, start_time, end_time)`** — it only blocks exact matches, not partial overlaps (`14:00–15:00` vs `14:30–15:30` would both pass), so it's not a real defense layer. The index on those columns is for query performance only, not uniqueness.

**JWT issuance vs verification**:
- Issuance lives in api service only (`JwtIssuer` with shared HMAC secret)
- Verification is in `core/JwtVerifier` and embedded in every service's filter chain
- Tokens are forwarded across service-to-service REST calls; each callee re-verifies independently

**`amount` columns are not duplicates**:
- `Reservation.amount` = price snapshot at booking time (immutable, billing baseline)
- `Payment.amount` = actually charged amount (may differ due to discount/partial payment)

**Internal endpoints**: `/api/v1/internal/**` is for service-to-service calls. It must be blocked from external exposure at the gateway/security-group layer. Never put `permitAll()` on it.

**Notification types**: only `CONFIRMED` and `CANCELLED` exist. Earlier drafts mentioned `RESERVED` — there is no such type in the spec.

## Documentation index

`docs/` is the source of truth for design decisions:
- `01-overview.md` — requirements, tech stack, current vs target state
- `02-architecture.md` — service topology, communication, concurrency, AWS layout
- `03-erd.md` — per-service ERD (logical FK relationships, no physical FKs)
- `04-api-spec.md` — REST endpoints with service ownership table, ProblemDetail format
- `05-module-core.md` — core library scope (entities/repos NOT here)
- `06-1` through `06-4` — per-service module specs

When implementing a feature, find its module spec under `06-*`, cross-reference the API in `04-api-spec.md`, and check the relevant ERD section in `03-erd.md`.
