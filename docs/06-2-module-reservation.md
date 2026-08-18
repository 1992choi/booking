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

---

## Redis 캐싱

Merchant 조회 성능 개선을 위해 Spring Cache (`@Cacheable`, `@CacheEvict`) 적용.

| 캐시 이름 | 키 | 대상 | 무효화 시점 |
|-----------|----|------|------------|
| `merchant` | `{merchantId}` | 업체 상세 | 업체 수정 |
| `merchants` | `all` | 전체 업체 목록 | 업체 등록 · 수정 |

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

### consume
| 토픽 | 처리 |
|------|------|
| payment.completed | 예약 상태 → CONFIRMED |
| payment.failed | 예약 상태 → CANCELLED, reservation.cancelled 이벤트 발행 |
| user.created | `UserSync` 로컬 사본 INSERT |
| user.updated | `UserSync` 로컬 사본 UPDATE (존재할 때만) |
| user.deleted | `UserSync` 로컬 사본 DELETE |
