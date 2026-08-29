# 99. Backlog

## Outbox 패턴 + 보상 트랜잭션

### 배경
현재 `@TransactionalEventListener(AFTER_COMMIT)`으로 Kafka를 발행하는데, 발행 직전 프로세스가 죽으면 이벤트가 유실된다.
또한 `payment.completed`를 reservation과 notification이 각각 독립 consume하므로, reservation 확정이 실패해도 notification이 발송되는 불일치가 발생한다.

### 해결 방향

**1. Outbox 패턴**

비즈니스 로직과 outbox INSERT를 같은 트랜잭션으로 묶어 유실을 방지한다.
`@Scheduled` 폴러가 PENDING 레코드를 읽어 Kafka에 발행 후 PUBLISHED 처리.

```
트랜잭션 커밋
  ├── 비즈니스 테이블 변경 (e.g. Reservation INSERT)
  └── outbox INSERT (status=PENDING)   ← 원자적

[@Scheduled]
  → outbox WHERE status=PENDING 조회
  → Kafka publish
  → status = PUBLISHED
```

**2. 이벤트 체인 재설계**

notification이 `payment.completed` 대신 `reservation.confirmed` 를 구독하도록 변경.
reservation 확정이 실제로 성공한 이후에만 알림이 나간다.

```
현재
payment.completed ──┬── reservation: CONFIRMED
                    └── notification: 알림 발송  (reservation 결과 무관)

개선
payment.completed ──── reservation: CONFIRMED + outbox(reservation.confirmed)
                                └── notification: 알림 발송
```

**3. 보상 트랜잭션**

결제 성공 후 예약 확정이 실패한 경우, 결제를 환불하고 슬롯을 복원한다.

```
payment.completed → reservation confirm 실패
                        ↓ 보상
                    payment: 환불 처리 (REFUNDED)
                    reservation: CANCELLED
                    available_time: OPEN 복원
```

### 적용 대상

| 서비스 | 이벤트 | 비고 |
|--------|--------|------|
| reservation | `reservation.created` | outbox 교체 |
| payment | `payment.completed` / `payment.failed` | outbox 교체 |
| reservation | `reservation.confirmed` | 신규 이벤트 + outbox |
| reservation | `reservation.cancelled` | outbox 교체 |

---

## CQRS

예약 조회(Read)와 생성/취소(Write) 모델 분리. 현재는 동일 엔티티로 읽기/쓰기를 모두 처리.

- Write 모델: 기존 JPA 엔티티 유지
- Read 모델: 조회 전용 DTO/Repository 분리 (QueryDSL 또는 별도 Read DB)
- 적용 대상: 예약 목록 조회, 업체별 캘린더 뷰 — 조회 빈도가 높고 Write와 요구사항이 달라 분리 효과가 큼

---

## 사용자 활동 감사 로그 (MongoDB)

명시적 호출(`auditService.record(...)`) 방식으로 의미 있는 사용자 액션만 기록. `core`에 MongoDB 기반 감사 기록 공용 컴포넌트를 두고 각 서비스가 필요한 지점에서 호출하는 방식. 각각 독립적으로 구현 가능.

### 1. ~~reservation 예약 생성/취소 감사~~ — **완료.**

`core`에 `AuditAutoConfiguration`/`AuditService`(`MongoTemplate` 기반, `MongoTemplate` 클래스패스에 있을 때만 활성화)를 추가하고, `reservation`의 `ReservationService.create()`/`cancel()`(사용자 공개 API만, `/internal`·`adminCancel`·`confirm` 등은 제외)에서 `auditService.record(...)`를 호출해 `audit_logs` 컬렉션에 기록. `reservation` 전용 MongoDB 연결(`db_reservation_audit`, JPA/MySQL과 별개)을 추가하고, `docker-compose.yml`에 `mongodb` 컨테이너를 추가.

Spring Boot 4에서 Mongo 연결 프로퍼티가 `spring.data.mongodb.*`가 아니라 `spring.mongodb.*`로 이동한 걸 모르고 처음엔 `spring.data.mongodb.*`로 설정해서 조용히 기본 `test` DB로 연결되는 문제를 겪었다 — `spring.mongodb.host/port/database`(또는 `uri`)로 고쳐서 해결.

`ReservationAuditTest`를 새로 추가해 실제 MongoDB에 감사 로그가 기록되는지 검증(기존 88개 테스트 전체 통과 확인).

### 2. api 로그인 감사

- 대상: 로그인 API
- api에 동일한 core 감사 컴포넌트 적용

### 적용 대상

| 서비스 | 비고 |
|--------|------|
| core | MongoDB 기반 감사 기록 공용 컴포넌트 추가 |
| reservation | 예약 생성/취소 지점에 감사 로그 기록, MongoDB 컨테이너/의존성 추가 |
| api | 로그인 지점에 감사 로그 기록 |
