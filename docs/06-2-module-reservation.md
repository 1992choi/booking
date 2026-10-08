# 06-2. reservation 서비스

## 역할

예약 도메인 전체를 소유. Merchant/Resource/AvailableTime CRUD + 예약 생성/조회/취소/관리 + 동시성 처리.

| 항목 | 값 |
|------|-----|
| 포트 | 8081 |
| DB | db_reservation (MySQL) + db_reservation_audit (MongoDB, 감사 로그 전용) |
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
- DailyMerchantStats (업체 일별 예약 통계 — batch 모듈이 집계, reservation 은 조회만)
- UserSync (api 서비스의 User 를 Kafka 로 동기화한 읽기 전용 로컬 사본)

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
    │   ├── ReservationRepositoryCustom.java     (QueryDSL 커스텀 조회)
    │   ├── ReservationRepositoryImpl.java       (findOverlapping — PESSIMISTIC_WRITE)
    │   └── ReservationStatus.java
    ├── merchant/
    │   ├── controller/MerchantController.java
    │   ├── service/MerchantService.java
    │   ├── domain/
    │   │   ├── Merchant.java
    │   │   ├── MerchantRepository.java
    │   │   ├── MerchantType.java
    │   │   ├── DailyMerchantStats.java          (batch 가 집계, 여기선 조회만)
    │   │   └── DailyMerchantStatsRepository.java
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
    ├── user/                                    (api 서비스 User 의 Kafka 동기화 사본)
    │   ├── domain/UserSync.java
    │   ├── domain/UserSyncRepository.java
    │   └── event/UserEventConsumer.java         (Kafka consume — user.created/updated/deleted)
    ├── event/
    │   ├── ReservationEventPublisher.java   (Kafka produce — AFTER_COMMIT)
    │   └── PaymentEventConsumer.java        (Kafka consume — payment.completed/payment.failed)
    ├── scheduler/
    │   └── ReservationReminderScheduler.java (매분 — 리마인더 대상 조회 후 발송)
    ├── system/
    │   └── PingController.java
    ├── error/
    │   └── ReservationErrorCode.java
    ├── dto/
    └── config/
        ├── SecurityConfig.java
        ├── CacheConfig.java                 (Redis 캐시 설정)
        ├── RedissonConfig.java              (Redis 분산 락 클라이언트)
        └── KafkaConfig.java
```


---

## 핵심 로직

### 예약 생성 흐름

0. (선택) 요청에 `Idempotency-Key` 헤더가 있으면 `reservation-idempotency` 캐시(키: `{userId}:{idempotencyKey}`)를 먼저 조회 — 캐시 히트 시 아래 로직을 전부 건너뛰고 저장된 응답을 그대로 반환
1. `RLock lock = redissonClient.getLock("reservation:lock:" + resourceId)` 로 tryLock(waitTime=3s, leaseTime=5s). 획득 실패 시 409 RSV_002 (LOCK_FAILED)
2. (락 보유 중) `ResourceRepository` 로 resource 조회 (가격 · maxCapacity snapshot)
3. `headCount > maxCapacity` → 422 RSV_003
4. 각 슬롯별 검증 — `findOverlapping` 은 `PESSIMISTIC_WRITE` 락을 걸고 조회:
   - `slot.status == BLOCKED` → 409 RSV_001
   - `findOverlapping().sumHeadCount + headCount > maxCapacity` → 409 RSV_001
5. Reservation INSERT. `amount` · `resourceName` 은 resource snapshot 으로 저장 (이후 변경돼도 불변)
6. `finally` 블록에서 `lock.unlock()`
7. 도메인 이벤트 발행 → `ReservationEventPublisher` 가 `AFTER_COMMIT` 에:
   - `sumHeadCountByAvailableTimeId >= maxCapacity` 이면 `AvailableTime.status` → BLOCKED
   - Kafka `reservation.created` publish
8. `core`의 `AuditService.record("RESERVATION_CREATED", ...)` 호출 — MongoDB `audit_logs` 컬렉션에 기록 (예약 취소도 동일하게 `RESERVATION_CANCELLED` 기록. 아래 "감사 로그" 참고)
9. `Idempotency-Key`가 있었다면 응답을 `reservation-idempotency` 캐시에 저장(기본 TTL 10분) 후 반환

### 주요 쿼리

```java
// 시간 겹침 조회 (QueryDSL, ReservationRepositoryImpl) — 비관적 락으로 동시 갱신 방지
@Override
public List<Reservation> findOverlapping(Long resourceId, LocalDateTime start, LocalDateTime end) {
    return queryFactory.selectFrom(r)
            .where(
                    r.resourceId.eq(resourceId),
                    r.status.ne(ReservationStatus.CANCELLED),
                    r.startTime.lt(end),
                    r.endTime.gt(start)
            )
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .fetch();
}

// 슬롯 점유 인원 합산 (CANCELLED 제외)
@Query("""
    SELECT COALESCE(SUM(r.headCount), 0) FROM Reservation r
    WHERE r.availableTimeId = :availableTimeId
      AND r.status <> ReservationStatus.CANCELLED
""")
int sumHeadCountByAvailableTimeId(@Param("availableTimeId") Long availableTimeId);
```

### 리마인더 발송 흐름

`ReservationReminderScheduler`가 `booking.reminder.cron`(기본: 매분) 주기로 `ReservationService.sendReminders(windowMinutes)`를 호출한다.

1. `findReminderTargets(now, now + windowMinutes)` — `status = CONFIRMED`, `startTime`이 윈도우 안, `reminderSentAt IS NULL`인 예약 조회 (`booking.reminder.window-minutes`, 기본 60분)
2. 대상마다 `reminderSentAt`을 즉시 채워 같은 예약이 다음 스케줄 실행에서 중복 조회되지 않도록 함
3. 도메인 이벤트 발행 → `ReservationEventPublisher`가 `AFTER_COMMIT`에 Kafka `reservation.reminder` publish

재확인(윈도우 안에서 알림이 유실됐는지 등)이나 재시도는 없음 — `reminderSentAt`이 한 번 찍히면 해당 예약은 영구히 대상에서 제외된다.

---

## Redis 캐싱

Merchant 조회 성능 개선을 위해 Spring Cache (`@Cacheable`, `@CacheEvict`) 적용. 예약 생성의 `Idempotency-Key` 응답 저장에도 같은 `CacheManager`(Redis, JSON 직렬화)를 재사용한다.

| 캐시 이름 | 키 | 대상 | 무효화 시점 | TTL |
|-----------|----|------|------------|-----|
| `merchant` | `{merchantId}` | 업체 상세 | 업체 수정 | 10분(기본) |
| `merchants` | `{type, pageable}` | 업체 목록(페이지 · type 필터별) | 업체 등록 · 수정 (전체 무효화) | 10분(기본) |
| `reservation-idempotency` | `{userId}:{idempotencyKey}` | 예약 생성 응답 | 없음(TTL 만료로만 소멸) | 10분(기본) |

### Caffeine + Redis 다중 레이어 캐싱 (`MerchantService.getById`)

업체 단건 조회(`getById`)는 캐시 히트 시에도 매번 Redis 네트워크 왕복이 발생하는 걸 줄이기 위해 로컬 JVM 메모리(L1, Caffeine) → Redis(L2) → DB 순으로 직접 조회한다. `@Cacheable` 자동 다단 캐싱 대신 서비스 메서드 안에서 명시적으로 조회/적재한다.

```
getById(merchantId)
  → L1(Caffeine) 히트 → 즉시 반환
  → L1 미스 → L2(Redis, "merchant" 캐시) 조회
      → L2 히트 → L1에 채우고 반환
      → L2 미스 → DB 조회 → L2 · L1 모두 채우고 반환
```

- L1은 `MerchantService` 안의 `com.github.benmanes.caffeine.cache.Cache<Long, Merchant>` 필드(최대 1000건, `expireAfterWrite` 1분) — L2(Redis, 10분)보다 TTL을 짧게 둬 다중 인스턴스 간 불일치 폭을 줄인다.
- 무효화: `update()`가 기존 `@CacheEvict("merchant")`(Redis)에 더해 `merchantLocalCache.invalidate(merchantId)`(L1)도 호출. 단, 이 무효화는 **같은 JVM 인스턴스 안에서만** 유효하다 — 인스턴스가 여러 개면 다른 인스턴스의 L1은 최대 1분(TTL) 동안 구 데이터를 반환할 수 있음(Redis Pub/Sub 기반 크로스 인스턴스 무효화는 미구현, 백로그 참고).
- `getAll`(업체 목록)은 기존 `@Cacheable(merchants)` 단일 레이어(Redis)를 그대로 유지 — 캐시 키가 `{type, pageable}` 조합이라 Caffeine 같은 단순 맵 캐시로 다루기 번거롭고, `getById`만큼 호출 빈도가 높지 않음.

---

## 감사 로그 (MongoDB)

사용자 공개 API 중 의미 있는 액션만 `core`의 `AuditService`(`05-module-core.md` 참고)로 기록한다. `/api/v1/internal/**`, `adminCancel`/`confirm` 등 관리자·서비스 간 호출은 대상에서 제외.

| 액션 | 기록 시점 | detail |
|------|----------|--------|
| `RESERVATION_CREATED` | `ReservationService.create()` 성공 시 | `resourceId`, `reservationIds` |
| `RESERVATION_CANCELLED` | `ReservationService.cancel()` 성공 시 (사용자 본인 취소) | `reservationId` |

MongoDB 연결은 `db_reservation`(MySQL)과 별개로 `db_reservation_audit`(MongoDB)를 사용하며, 기록 실패는 예약 생성/취소 자체를 막지 않는다(`AuditService` 내부에서 예외를 흡수하고 warn 로그만 남김).

---

## 접근 제어 (SecurityConfig)

```
GET /api/v1/merchants                           → permitAll
GET /api/v1/merchants/*                         → permitAll
GET /api/v1/resources/*/available-times         → permitAll
/ping                                           → permitAll
/actuator/**                                    → permitAll
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
| reservation.reminder | 예약 시작 전 리마인더 (`ReservationReminderScheduler`) | AFTER_COMMIT |

### consume
| 토픽 | 처리 |
|------|------|
| payment.completed | 예약 상태 → CONFIRMED |
| payment.failed | 예약 상태 → CANCELLED, reservation.cancelled 이벤트 발행 |
| user.created | `UserSync` 로컬 사본 INSERT |
| user.updated | `UserSync` 로컬 사본 UPDATE (존재할 때만) |
| user.deleted | `UserSync` 로컬 사본 DELETE |
