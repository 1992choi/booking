# 02. 전체 아키텍처

## 시스템 구성 (MSA)

4개의 독립 배포 서비스 + 1개의 공통 라이브러리.

```
┌─────────────────────────────────────────────────────────────┐
│                          Client                              │
└────────────────┬────────────────────────────────────────────┘
                 │ HTTPS
                 ↓
┌─────────────────────────────────────────────────────────────┐
│  ALB / API Gateway  (path 기반 라우팅, JWT 검증은 각 서비스) │
└────────────────┬────────────────────────────────────────────┘
                 │
       ┌─────────┼─────────────────┬─────────────┐
       ↓         ↓                 ↓             ↓
   ┌────────┐ ┌──────────────┐ ┌─────────┐ ┌──────────────┐
   │  api   │ │ reservation  │ │ payment │ │ notification │
   │ :8080  │ │   :8081      │ │  :8082  │ │   :8083      │
   └───┬────┘ └──────┬───────┘ └────┬────┘ └──────┬───────┘
       │             │              │              │
       │ REST        │              │              │
       │ ←───────────┘              │              │
       │                            │              │
       ▼             ▼              ▼              ▼
   ┌────────┐  ┌──────────────┐ ┌─────────┐  ┌──────────────┐
   │ db_api │  │db_reservation│ │db_payment│ │db_notification│
   └────────┘  └──────────────┘ └─────────┘  └──────────────┘

                  ┌────────────────────────┐
                  │       Kafka            │
                  └────────────────────────┘
                            ↑↓
   reservation/payment/notification 모두 Kafka 로 비동기 통신

                  ┌────────────────────────┐
                  │   Redis (reservation)  │  분산 락 전용
                  └────────────────────────┘
```

---

## 멀티 모듈 구조 (Gradle)

```
booking/
├── core              # 라이브러리 (배포 X)
├── api               # 서비스 1
├── reservation       # 서비스 2
├── payment           # 서비스 3
└── notification      # 서비스 4
```

### 모듈 의존 관계

```
api          ─── core
reservation  ─── core
payment      ─── core
notification ─── core
```

→ 4개 서비스는 **서로 직접 의존하지 않는다**. 통신은 REST 또는 Kafka 만 사용.
→ core 만 공통 라이브러리로 임베드.

### 배포 산출물

| 모듈 | bootJar | jar | 비고 |
|------|---------|-----|------|
| core | X | O | 라이브러리. `bootJar { enabled = false }`, `jar { enabled = true }` |
| api | O | X | Spring Boot 앱 |
| reservation | O | X | Spring Boot 앱 |
| payment | O | X | Spring Boot 앱 |
| notification | O | X | Spring Boot 앱 |

---

## 서비스 책임

| 서비스 | 책임 | DB |
|--------|------|----|
| api | 인증, 회원, 업체(Merchant), 리소스, 가능시간 CRUD. 관리 API 의 진입점 | db_api |
| reservation | 예약 생성/조회/취소, 동시성 처리(Redis 분산 락 + DB 락) | db_reservation |
| payment | Mock 결제 처리. 결제 이력 조회/환불 | db_payment |
| notification | Mock 알림 발송. 발송 이력 저장 | db_notification |

---

## 서비스 간 통신

### 동기 (REST)

| 호출 방향 | 용도 |
|-----------|------|
| reservation → api | 예약 시 resource / available-time 검증, 가격 조회 |
| payment → api | 결제 응답에 사용자 정보 포함 시 |
| notification → api | 알림 메시지 생성 시 사용자 정보 |
| api → reservation | 관리 API 의 예약 조회/캘린더 뷰 위임 |

- Spring 6 `RestClient` 사용
- Resilience4j 로 서킷브레이커 / 타임아웃
- JWT 토큰을 호출 체인 전체에 전파 (각 서비스가 자체 검증)

### 비동기 (Kafka)

| 토픽 | producer | consumer | 페이로드 |
|------|----------|----------|----------|
| `reservation.created` | reservation | payment | reservationId, userId, resourceId, startTime, endTime, amount |
| `payment.completed` | payment | notification | paymentId, reservationId, userId, amount, paidAt |
| `payment.failed` | payment | reservation | paymentId, reservationId, reason |
| `reservation.cancelled` | reservation | notification | reservationId, userId, reason, cancelledAt |

### 이벤트 페이로드 예시

**reservation.created**
```json
{
  "reservationId": 1,
  "userId": 10,
  "resourceId": 5,
  "startTime": "2026-05-01T14:00:00",
  "endTime": "2026-05-01T15:00:00",
  "amount": 50000
}
```

**payment.completed**
```json
{
  "paymentId": 1,
  "reservationId": 1,
  "userId": 10,
  "amount": 50000,
  "paidAt": "2026-05-01T13:00:00"
}
```

> Kafka send 는 트랜잭션 커밋 이후 발행되어야 함 (`@TransactionalEventListener(AFTER_COMMIT)` 또는 Outbox 패턴). 그렇지 않으면 DB 롤백 시 유령 이벤트가 흘러간다.

---

## 인증/인가

- **JWT 발급**: api 서비스가 단독 발급 (HMAC-SHA256, 공유 시크릿)
- **JWT 검증**: 각 서비스가 자체 검증 (core 의 `JwtVerifier` 임베드)
- **권한**: User 의 role 컬럼(`USER`, `MERCHANT`, `ADMIN`)을 토큰 클레임에 포함

```
Client ─── (Login) ───→ api 서비스 ─── JWT 발급
Client ─── (API call) ─→ 각 서비스 ─── JWT 자체 검증
```

서비스 간 호출 시 client 의 토큰을 그대로 forward.

---

## 동시성 처리 전략 (reservation 서비스)

### 문제 시나리오
```
User A: 5/10 14:00 ~ 15:00 예약 요청
User B: 5/10 14:00 ~ 15:00 예약 요청  ← 동시에 들어옴
→ 둘 다 "예약 가능"으로 판단
→ 중복 예약 발생
```

### 다층 방어 전략

**1단계 — Redis 분산 락 (메인)**
```java
RLock lock = redissonClient.getLock("reservation:lock:" + resourceId);
try {
    if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
        throw new BusinessException(ReservationErrorCode.LOCK_FAILED);
    }
    // 시간 겹침 체크 → 예약 생성 → Kafka produce (AFTER_COMMIT)
} finally {
    if (lock.isHeldByCurrentThread()) lock.unlock();
}
```

**2단계 — DB 비관적 락 + 시간 겹침 쿼리**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT r FROM Reservation r WHERE r.resourceId = :resourceId " +
       "AND r.status <> 'CANCELLED' " +
       "AND r.startTime < :endTime AND r.endTime > :startTime")
List<Reservation> findOverlappingWithLock(...);
```

**3단계 — DB 인덱스 + 정합성 체크**
- `INDEX (resource_id, start_time, end_time)` — 겹침 쿼리 성능
- 단순 UNIQUE 제약은 부분 겹침을 막지 못함 (`14:00–15:00` vs `14:30–15:30`).
  → 진짜 마지막 방어선이 필요하면 PostgreSQL EXCLUDE 제약, 또는 별도 슬롯 테이블 단위 잠금이 필요. 현재는 1+2 단계로 방어.

### 처리 흐름
```
요청
  → JWT 검증
  → Redis 분산 락 획득 시도
      락 실패 → 409 + RSV_002 (LOCK_FAILED)
      락 성공
        → 시간 겹침 쿼리 (DB 비관적 락)
            겹침 있음 → 409 + RSV_001 (CONFLICT)
            겹침 없음
              → Reservation INSERT
              → AFTER_COMMIT 시점에 Kafka produce
  → 락 해제
```

---

## AWS 인프라 구성 (예시)

```
[Route 53]
   ↓
[ALB] (path 라우팅: /auth/* /merchants/* /resources/* → api,
       /reservations/* → reservation, ...)
   ↓
[ECS Cluster]
   ├── api task           (db_api RDS)
   ├── reservation task   (db_reservation RDS, ElastiCache Redis)
   ├── payment task       (db_payment RDS)
   └── notification task  (db_notification RDS)
       ↑↓
   [MSK Kafka]
```

---

## 테스트 전략

- 단위/통합 테스트는 각 서비스 모듈 내부에서
- E2E: 4개 서비스 + Kafka + Redis + MySQL 을 docker-compose 로 띄워 시나리오 검증
- 동시성: JMeter 또는 k6 로 동일 시간대에 100명 동시 요청
  - 1건만 성공 확인
  - 99건은 409 응답 확인
  - DB 중복 없음 확인
