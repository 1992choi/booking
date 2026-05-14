# 06-2. reservation 서비스

## 역할

예약 도메인의 핵심 비즈니스 로직 + 동시성 처리. 예약 생성/조회/취소를 담당한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8081 |
| DB | db_reservation |
| 외부 노출 | O (path: `/reservations/**`) |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | api (REST, resource 검증) |

---

## 책임 도메인

reservation 서비스가 자체 DB 에 소유:
- Reservation

---

## 패키지 구조

```
reservation/
└── src/main/java/com/example/booking/reservation/
    ├── ReservationApplication.java
    ├── controller/
    │   ├── ReservationController.java          (외부 — 유저용)
    │   └── InternalReservationController.java  (내부 — 어드민 위임용)
    ├── service/
    │   └── ReservationService.java
    ├── domain/
    │   ├── Reservation.java
    │   ├── ReservationRepository.java
    │   └── ReservationStatus.java
    ├── client/
    │   ├── ResourceClient.java              (api 서비스 REST 호출)
    │   └── ResourceSnapshot.java
    ├── event/
    │   ├── ReservationEventPublisher.java   (Kafka produce — AFTER_COMMIT)
    │   └── PaymentEventConsumer.java        (Kafka consume — payment.failed)
    ├── error/
    │   └── ReservationErrorCode.java
    ├── dto/
    └── config/
```

> Redis 분산락, DB 비관적 락은 개선 이슈로 미구현 (backlog).

---

## 핵심 로직

### 예약 생성 흐름

1. `ResourceClient` 로 api 서비스에 resource 검증 (가격 · 최대 인원 snapshot 취득)
2. `findOverlapping` 으로 시간대 겹침 체크 — 겹치면 `RSV_001`
3. 예약 생성. `amount` · `resourceName` 은 api 서비스로부터 받은 snapshot 값으로 저장 (이후 resource 가 변경돼도 불변)
4. 도메인 이벤트 발행 → `ReservationEventPublisher` 가 `AFTER_COMMIT` 에 Kafka publish

### 시간 겹침 쿼리

```java
@Query("""
    SELECT r FROM Reservation r
    WHERE r.resourceId = :resourceId
      AND r.status <> ReservationStatus.CANCELLED
      AND r.startTime < :end
      AND r.endTime > :start
""")
List<Reservation> findOverlapping(Long resourceId, LocalDateTime start, LocalDateTime end);
```

> UNIQUE INDEX 는 정확히 일치하는 시간만 막으므로 부분 겹침을 방지할 수 없다. 이 쿼리가 실질적인 중복 방어선이다.

---

## Kafka

### produce
| 토픽 | 시점 | phase |
|------|------|-------|
| reservation.created | 예약 생성 완료 | AFTER_COMMIT |
| reservation.cancelled | 예약 취소 | AFTER_COMMIT |

### consume
| 토픽 | 처리 |
|------|------|
| payment.failed | 예약 상태 → CANCELLED, reservation.cancelled 이벤트 발행 |