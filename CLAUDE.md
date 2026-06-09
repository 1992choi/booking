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
api (8080)          ─ Auth only: JWT issuance, User CRUD
reservation (8081)  ─ Full booking domain: Merchant/Resource/AvailableTime CRUD + Reservation, Redis distributed lock, DB pessimistic lock
payment (8082)      ─ Mock payment processing
notification (8083) ─ Mock notification dispatch
core (library)      ─ BaseEntity, ErrorCode interface, ProblemDetail handler, JwtVerifier
```

Services communicate via:
- **Synchronous**: Spring 6 `RestClient` + Resilience4j (payment/notification → api for user info only)
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

## Coding behavior

### Think before coding
- State assumptions explicitly before implementing. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so and push back when warranted.

### Simplicity first
- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Self-check: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### Surgical changes
When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that *your* changes made unused.
- Don't remove pre-existing dead code unless asked.

Every changed line should trace directly to the user's request.

### Goal-driven execution
Transform vague tasks into verifiable goals before starting:
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan with success criteria per step.

## Code style

### Class body spacing
Always add a blank line before the closing `}` of every class (mirrors the blank line that follows the opening `{`):

```java
public class Foo {

    private final Bar bar;

    public void doSomething() {
        ...
    }

}
```

### Return statement separation
`return` is a distinct concern (signalling completion). Always separate it from the preceding code with a blank line — unless the entire method body is a single `return`:

```java
// ✓
public Foo get(Long id) {
    Foo foo = repository.findById(id).orElseThrow(...);

    return foo;
}

// ✓ single-statement body — no blank line needed
public Foo get(Long id) {
    return repository.findById(id).orElseThrow(...);
}
```

### Concern-based grouping
Within a method body, group statements by concern. Add a blank line whenever the concern changes. Typical concern boundaries in this codebase:

| Concern | Examples |
|---------|---------|
| Load / find | `repo.findById(...)`, `service.getById(...)` |
| Validate / guard | `if (...) throw ...`, access checks |
| Perform operation | `entity.update(...)`, `repo.save(...)`, `service.process(...)` |
| Publish / log | `eventPublisher.publishEvent(...)`, `kafkaTemplate.send(...)`, `log.info(...)` |
| Return | `return ...` |
| Deserialize (Kafka) | `objectMapper.readValue(...)` — always its own concern before the service call |

```java
// ✓
public Foo update(Long userId, Long fooId, UpdateRequest request) {
    Foo foo = findOrThrow(fooId);
    validateAccess(userId, foo);

    foo.update(request.name());
    eventPublisher.publishEvent(new FooUpdatedEvent(foo.getId()));
    log.info("업데이트 fooId={}", fooId);

    return foo;
}
```

See `docs/07-coding-conventions.md` for rationale and more examples.

## Documentation index

`docs/` is the source of truth for design decisions:
- `01-overview.md` — requirements, tech stack, current vs target state
- `02-architecture.md` — service topology, communication, concurrency, AWS layout
- `03-erd.md` — per-service ERD (logical FK relationships, no physical FKs)
- `04-api-spec.md` — REST endpoints with service ownership table, ProblemDetail format
- `05-module-core.md` — core library scope (entities/repos NOT here)
- `06-1` through `06-4` — per-service module specs
- `07-coding-conventions.md` — code style and formatting rules

When implementing a feature, find its module spec under `06-*`, cross-reference the API in `04-api-spec.md`, and check the relevant ERD section in `03-erd.md`.
