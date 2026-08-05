# Outbox 패턴 + 보상 트랜잭션

## 구현 범위

- **reservation 서비스**: 기존 `@TransactionalEventListener` → Outbox 교체, `reservation.confirmed` 신규 이벤트 produce, 보상 트랜잭션 시 `reservation.confirm.failed` produce
- **payment 서비스**: Outbox 교체, `reservation.confirm.failed` consume → 환불 처리
- **notification 서비스**: `payment.completed` 구독 제거 → `reservation.confirmed` 구독으로 교체

## 이벤트 체인 변경

```
[현재]
payment.completed ──┬── reservation: CONFIRMED
                    └── notification: 알림 발송  ← reservation 결과 무관

[변경 후]
payment.completed ──── reservation: CONFIRMED → outbox(reservation.confirmed)
                                                     └── notification: 알림 발송

결제 성공 후 예약 확정 실패 시:
payment.completed ──── reservation: 확정 실패 → outbox(reservation.confirm.failed)
                                                     └── payment: REFUNDED
                                                         reservation: CANCELLED
                                                         available_time: OPEN 복원
```

## 신규 Kafka 이벤트

| 토픽 | producer | consumer | 페이로드 |
|------|----------|----------|----------|
| `reservation.confirmed` | reservation | notification | reservationId, userId, resourceName, startTime, endTime |
| `reservation.confirm.failed` | reservation | payment | reservationId, paymentId, reason |

## ERD 변경

### reservation DB — outbox 테이블 추가

| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| aggregate_type | VARCHAR | 도메인 구분 (e.g. `Reservation`) |
| aggregate_id | BIGINT | 해당 도메인 ID |
| topic | VARCHAR | Kafka 토픽명 |
| payload | TEXT | JSON 직렬화 페이로드 |
| status | ENUM | PENDING / PUBLISHED |
| created_at | DATETIME | |
| published_at | DATETIME | nullable |

### payment DB — outbox 테이블 추가

reservation과 동일한 스키마.

## 예상 파일 목록

### reservation 서비스

- `outbox/domain/Outbox.java` — 엔티티
- `outbox/domain/OutboxRepository.java`
- `outbox/OutboxPoller.java` — `@Scheduled` 폴러, PENDING → Kafka publish → PUBLISHED
- `event/ReservationEventPublisher.java` — 기존 AFTER_COMMIT 방식 제거, outbox INSERT로 교체
- `event/PaymentEventConsumer.java` — `payment.completed` 수신 시 확정 처리 + 실패 시 보상 이벤트 outbox INSERT

### payment 서비스

- `outbox/domain/Outbox.java`
- `outbox/domain/OutboxRepository.java`
- `outbox/OutboxPoller.java`
- `event/PaymentEventPublisher.java` — outbox INSERT로 교체
- `event/ReservationEventConsumer.java` — `reservation.confirm.failed` consume 추가, 환불 처리

### notification 서비스

- `event/PaymentEventConsumer.java` — `payment.completed` 구독 제거
- `event/ReservationEventConsumer.java` — `reservation.confirmed` consume 추가, CONFIRMED 알림 발송

## 주의사항

- **Outbox 폴러 중복 발행**: 폴러 재시작 등으로 같은 레코드를 두 번 publish할 수 있음. Kafka producer `enable.idempotence=true` 설정 필수.
- **Outbox INSERT는 비즈니스 트랜잭션과 동일 트랜잭션**: INSERT 실패 시 비즈니스 로직도 함께 롤백됨. `@Transactional` 경계 유의.
- **보상 시 available_time 복원**: `reservation.confirm.failed` 처리 시 reservation 서비스 내에서 해당 슬롯의 `sumHeadCount` 재계산 후 OPEN 복원 필요.
- **기존 `@TransactionalEventListener` 완전 제거**: 기존 `ReservationEventPublisher`, `PaymentEventPublisher`의 AFTER_COMMIT 발행 로직을 Outbox로 전환 후 삭제.
- **docs 업데이트 필요**: `02-architecture.md` (이벤트 토픽 테이블), `06-2`, `06-3`, `06-4` 모듈 스펙, `03-erd.md` (outbox 테이블).