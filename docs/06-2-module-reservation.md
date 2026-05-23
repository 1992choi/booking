# 06-2. reservation 서비스

## 역할

예약 도메인 전체를 소유. Merchant/Resource/AvailableTime CRUD + 예약 생성/조회/취소/관리 + 동시성 처리.

| 항목 | 값 |
|------|-----|
| 포트 | 8081 |
| DB | db_reservation |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | 없음 |

---

## 책임 도메인

reservation 서비스가 자체 DB 에 소유:
- Merchant (업체)
- Resource (예약 대상)
- AvailableTime (예약 가능 시간대)
- Reservation

---

## 패키지 구조

```
reservation/
└── src/main/java/com/example/booking/reservation/
    ├── ReservationApplication.java
    ├── controller/
    │   └── ReservationController.java          (외부 — 유저용)
    ├── service/
    │   └── ReservationService.java
    ├── domain/
    │   ├── Reservation.java
    │   ├── ReservationRepository.java
    │   └── ReservationStatus.java
    ├── merchant/
    │   ├── controller/MerchantController.java
    │   ├── service/MerchantService.java
    │   ├── domain/
    │   │   ├── Merchant.java
    │   │   ├── MerchantRepository.java
    │   │   └── MerchantType.java
    │   └── dto/
    ├── resource/
    │   ├── controller/ResourceController.java
    │   ├── service/ResourceService.java
    │   ├── domain/
    │   │   ├── Resource.java
    │   │   ├── ResourceRepository.java
    │   │   ├── AvailableTime.java
    │   │   ├── AvailableTimeRepository.java
    │   │   └── AvailableTimeStatus.java
    │   └── dto/
    ├── admin/
    │   ├── controller/AdminController.java
    │   └── dto/
    ├── event/
    │   ├── ReservationEventPublisher.java   (Kafka produce — AFTER_COMMIT)
    │   └── PaymentEventConsumer.java        (Kafka consume — payment.failed)
    ├── error/
    │   └── ReservationErrorCode.java
    ├── dto/
    └── config/
        └── SecurityConfig.java
```

> Redis 분산락, DB 비관적 락은 개선 이슈로 미구현 (backlog).

---

## 핵심 로직

### 예약 생성 흐름

1. `ResourceRepository` 로 resource 검증 (가격 · 최대 인원 snapshot 취득) — cross-service REST 호출 없음
2. `AvailableTimeRepository` 로 슬롯 상태 검증 (BLOCKED 이면 RSV_001)
3. `findOverlapping` 으로 시간대 겹침 체크 — 겹치면 `RSV_001`
4. 예약 생성. `amount` · `resourceName` 은 resource 에서 취득한 snapshot 값으로 저장 (이후 resource 가 변경돼도 불변)
5. 도메인 이벤트 발행 → `ReservationEventPublisher` 가 `AFTER_COMMIT` 에 AvailableTime.status BLOCKED 처리 + Kafka publish

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

## 접근 제어 (SecurityConfig)

```
GET /api/v1/merchants                           → permitAll
GET /api/v1/merchants/*                         → permitAll
GET /api/v1/resources/*/available-times         → permitAll
/api/v1/admin/**                                → hasRole("MERCHANT")
그 외                                            → authenticated
```

---

## ReservationErrorCode

| code | HTTP | 설명 |
|------|------|------|
| RSV_001 | 409 | 시간대 중복 또는 슬롯 BLOCKED |
| RSV_002 | 409 | 동시 요청 락 실패 |
| RSV_003 | 422 | 인원 초과 (max_capacity) |
| RSV_004 | 404 | 예약 없음 |
| RSV_005 | 403 | 본인 예약 아님 |
| RSV_006 | 404 | 업체 없음 |
| RSV_007 | 404 | 예약 대상 없음 |
| RSV_008 | 404 | 가능 시간 없음 |

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
