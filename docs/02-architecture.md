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
       │  HTTP       │              │              │
       │─────────────────────────────────────────▶│
       ▼             ▼              ▼  HTTP        ▼
   ┌────────┐  ┌──────────────┐ ┌─────────┐  ┌──────────────┐
   │ db_api │  │db_reservation│ │db_payment│ │db_notification│
   └────────┘  └──────────────┘ └────┬────┘  └──────────────┘
                                      │ RestClient
                                      ▼
                              ┌───────────────┐
                              │  pg  :8090    │  Mock PG 서버
                              │  (no DB)      │  (외부 PG 시뮬레이션)
                              └───────────────┘

                  ┌────────────────────────┐
                  │       Kafka            │
                  └────────────────────────┘
                            ↑↓
   reservation/payment/notification 모두 Kafka 로 비동기 통신

                  ┌────────────────────────┐
                  │   Redis (reservation)  │  분산 락 전용
                  └────────────────────────┘
```

### review 모듈 (Kotlin, 학습용)

`review` (`:8084`)는 위 4개 서비스와 별개로 독립 배포되는 학습용 모듈이다. 새 DB를 만들지 않고 `db_reservation`을 reservation 서비스와 공유하며, 자신이 쓰기 권한을 갖는 테이블은 신설 `reviews` 하나뿐이다. 리뷰 작성 자격 검증(`본인이 CONFIRMED 예약을 했는가`)은 `reservations`/`resources` 테이블을 JDBC로 직접 읽기 전용 조회해서 처리하며, reservation 서비스에 대한 REST 호출이나 Kafka 구독은 전혀 없다. 자세한 내용은 `06-5-module-review.md` 참고.

### batch 모듈 (Spring Batch)

`batch`는 HTTP로 외부에 노출되지 않는 배치 전용 모듈이다. `db_reservation`을 reservation 서비스와 공유하며, `@Scheduled` 트리거로 두 Job을 실행한다: `expirePendingReservationsJob`(매분, `booking.batch.pending-expiry-minutes`가 지난 `PENDING` 예약을 만료 처리) · `dailyMerchantStatsJob`(매일 새벽 1시, 전일자 업체별 예약 통계를 `daily_merchant_stats`에 집계). reservation 서비스에 대한 REST 호출이나 Kafka 구독 없이 DB를 직접 읽고 쓴다.

---

## 멀티 모듈 구조 (Gradle)

```
booking/
├── core              # 라이브러리 (배포 X)
├── api               # 서비스 1
├── reservation       # 서비스 2
├── payment           # 서비스 3
├── notification      # 서비스 4
├── batch             # 배치 전용 모듈 — db_reservation 공유, HTTP 미노출
├── pg                # Mock PG 서버 (외부 PG 시뮬레이션)
└── review            # 학습용 모듈 (Kotlin) — db_reservation 공유
```

### 모듈 의존 관계

```
api          ─── core
reservation  ─── core
payment      ─── core
notification ─── core
batch        ─── core
pg           ─── (없음)   # 완전 독립. core도 사용하지 않음
review       ─── core     # Kotlin 이지만 core(Java 라이브러리)는 그대로 소비 가능
```

→ 4개 서비스는 **서로 직접 의존하지 않는다**. 통신은 Kafka 만 사용.
→ core 만 공통 라이브러리로 임베드.
→ pg 는 외부 PG 서버를 시뮬레이션하는 독립 서버. JWT/Security/DB/Kafka 없이 web + validation 만 사용.
→ review 는 reservation 서비스에 의존하지 않는다 (REST 호출 없음). `db_reservation`을 공유 DB로 직접 연결할 뿐이다.
→ batch 도 review 와 마찬가지로 reservation 서비스에 의존하지 않고 `db_reservation`을 직접 연결한다.

### 배포 산출물

| 모듈 | bootJar | jar | 비고 |
|------|---------|-----|------|
| core | X | O | 라이브러리. `bootJar { enabled = false }`, `jar { enabled = true }` |
| api | O | X | Spring Boot 앱 |
| reservation | O | X | Spring Boot 앱 |
| payment | O | X | Spring Boot 앱 |
| notification | O | X | Spring Boot 앱 |
| batch | O | X | Spring Batch 앱. HTTP 미노출, `@Scheduled` 로 Job 실행 |
| pg | O | X | Mock PG 서버. 실제 PG 연동 시 제거 대상 |
| review | O | X | Spring Boot 앱 (Kotlin) |

---

## 서비스 책임

| 서비스 | 책임 | DB |
|--------|------|----|
| api | 인증 전용 — 회원(User) CRUD, JWT 발급/갱신 | db_api |
| reservation | 예약 도메인 전체 — 업체(Merchant)/리소스(Resource)/가능시간(AvailableTime) CRUD, 예약 생성/조회/취소/관리, 동시성 처리(Redis 분산 락 + DB 락) | db_reservation |
| payment | 결제 처리. 결제 이력 조회/환불. pg 서버에 HTTP 로 거래 승인/취소 요청 | db_payment |
| notification | Mock 알림 발송. 발송 이력 저장 | db_notification |
| pg | Mock PG 서버 (외부 시스템). 거래 승인/취소 API. 20% 확률로 실패 반환 | 없음 (stateless) |

---

## 서비스 간 통신

### 동기 (HTTP / RestClient)

| 호출 방향 | 엔드포인트 | 용도 |
|-----------|-----------|------|
| api → notification | `POST /api/v1/internal/messages` | 관리자가 특정 유저에게 메시지 발송 |

> api → notification 구간에 Resilience4j 재시도(최대 3회, 500ms 간격, 네트워크 예외만 대상) + 서킷브레이커(`notification` 인스턴스)가 적용되어 있다. OPEN 시 즉시 503 (`API_004`) 반환.

### 비동기 (Kafka)

| 토픽 | producer | consumer | 페이로드 |
|------|----------|----------|----------|
| `user.created` | api | reservation, payment, notification | userId, name, email, phone |
| `user.updated` | api | reservation, payment, notification | userId, name, email, phone |
| `user.deleted` | api | reservation, payment, notification | userId |
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

### 현재 구현 (Redis 분산 락 + DB 비관적 락 + headCount 기반 검사)

슬롯에 `maxCapacity` 가 있어, 단순 겹침 여부가 아니라 현재 점유 인원 합산으로 예약 가능 여부를 판단한다. 동시 요청에 의한 오버셀링을 막기 위해 리소스 단위 Redis 분산 락(Redisson)으로 요청을 직렬화하고, 락 내부에서 겹침 조회 쿼리에 DB 비관적 락(`PESSIMISTIC_WRITE`)을 걸어 이중으로 방어한다.

```
요청
  → Redisson RLock("reservation:lock:{resourceId}") tryLock(waitTime=3s, leaseTime=5s)
      - 획득 실패/인터럽트 → 409 RSV_002 (LOCK_FAILED)
  → (락 보유 중)
      → headCount > resource.maxCapacity → 422 RSV_003
      → 각 슬롯별:
          - slot.status == BLOCKED → 409 RSV_001
          - findOverlapping()[PESSIMISTIC_WRITE].sumHeadCount + 요청 headCount > maxCapacity → 409 RSV_001
      → Reservation INSERT
  → finally: lock.unlock()
  → AFTER_COMMIT:
      - sumHeadCountByAvailableTimeId >= maxCapacity → AvailableTime BLOCKED
      - Kafka produce reservation.created
```

> `INDEX (resource_id, start_time, end_time)` — 겹침 쿼리 성능용. UNIQUE 제약은 부분 겹침(`14:00–15:00` vs `14:30–15:30`)을 막지 못하므로 사용하지 않는다.
> Redis 락은 여러 애플리케이션 인스턴스 간 요청을 직렬화하기 위한 것이고, DB 비관적 락은 락 만료(leaseTime 초과) 등으로 직렬화가 깨지는 경우를 대비한 마지막 방어선이다. 두 레이어 중 하나만으로는 오버셀링을 완전히 막을 수 없다.


---

## AWS 인프라 구성 (예시)

```
[Route 53]
   ↓
[ALB] (path 라우팅: /auth/* /users/* → api,
       /merchants/* /resources/* /reservations/* /admin/* → reservation, ...)
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

