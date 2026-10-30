# 02. 전체 아키텍처

## 시스템 구성 (MSA)

핵심 4개의 독립 배포 서비스(api/reservation/payment/notification) + 1개의 공통 라이브러리(core). 여기에 배치 전용 `batch`, Mock PG `pg`, 학습용 `review` 3개 모듈이 추가로 독립 배포된다 (아래 "멀티 모듈 구조" 참고).

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

`batch`는 REST API가 없는 배치 전용 모듈이다. `db_reservation`을 reservation 서비스와 공유하며, `@Scheduled` 트리거로 두 Job을 실행한다: `expirePendingReservationsJob`(매분, `booking.batch.pending-expiry-minutes`가 지난 `PENDING` 예약을 만료 처리) · `dailyMerchantStatsJob`(매일 새벽 1시, 전일자 업체별 예약 통계를 `daily_merchant_stats`에 집계 + `merchant_daily_reservation_count`/`merchant_daily_revenue` Gauge로 노출). reservation 서비스에 대한 REST 호출이나 Kafka 구독 없이 DB를 직접 읽고 쓴다. Prometheus 스크랩을 위해 `/actuator/prometheus`만 여는 최소한의 HTTP(`:8085`)는 띄운다.

### AI 사용법 안내 챗봇 (LiteLLM Gateway + Ollama)

`api`에 신규 엔드포인트 `POST /api/v1/assistant/chat`을 두고, Anthropic SDK를 직접 의존하지 않고 **LiteLLM**(`:4000`, OpenAI 호환 `/chat/completions`)을 거쳐 모델을 호출한다. LiteLLM 뒤에는 두 모델이 등록돼 있다: `claude`(Anthropic Claude, 클라우드) / `local`(`ollama`(`:11434`)가 서빙하는 오픈 웨이트 모델). `claude` 호출이 실패하면 LiteLLM 설정(`litellm_settings.fallbacks`)에 따라 `local`로 자동 전환된다. Anthropic API 키는 LiteLLM 컨테이너에만 주입되고 `api`는 키를 갖지 않는다.

tool-use로 실데이터 조회도 지원한다: `get_my_reservations`(reservation 서비스에 JWT forward), `find_recent_reservation_error`(Loki에서 호출자 userId로 범위를 좁힌 최근 "Business exception" 로그의 `code`만 추출 — 원본 로그 전체는 모델에게 넘기지 않음). 후자를 위해 `core`의 `GlobalExceptionHandler`가 `BusinessException` 로그를 남길 때 MDC에 `userId`를 심어, Loki에서 `{app="...", userId="..."}` 라벨로 바로 필터링할 수 있게 했다(비로그인 요청은 principal이 없어 userId가 안 남음).

호출자가 ADMIN이면 `run_sql`(DB에 SELECT 전용 임의 쿼리)·`run_logql`(Loki에 범위 제한 없는 임의 LogQL)까지 추가로 쓸 수 있다 — `AssistantTool.adminOnly()`가 true인 tool은 USER/MERCHANT 요청에는 LiteLLM에 애초에 전달되지 않아, 모델이 그런 tool의 존재조차 모른다. `run_sql`은 CS 상담 목적의 의도적 예외로 database-per-service 경계(서비스별 DB 분리)를 넘어 `db_reservation`/`db_payment` 등을 직접 조회할 수 있다. 자세한 설계는 `docs/99-backlog.md`의 "AI 사용법 안내 챗봇" 참고.

---

## 멀티 모듈 구조 (Gradle)

```
booking/
├── core              # 라이브러리 (배포 X)
├── api               # 서비스 1
├── reservation       # 서비스 2
├── payment           # 서비스 3
├── notification      # 서비스 4
├── batch             # 배치 전용 모듈 — db_reservation 공유, actuator 전용 HTTP(:8085)만 노출
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
| batch | O | X | Spring Batch 앱. REST API 없이 `@Scheduled` 로 Job 실행, `/actuator/prometheus`만 노출 |
| pg | O | X | Mock PG 서버. 실제 PG 연동 시 제거 대상 |
| review | O | X | Spring Boot 앱 (Kotlin) |

---

## 서비스 책임

| 서비스 | 책임 | DB |
|--------|------|----|
| api | 인증 전용 — 회원(User) CRUD, JWT 발급/갱신 | db_api |
| reservation | 예약 도메인 전체 — 업체(Merchant)/리소스(Resource)/가능시간(AvailableTime) CRUD, 예약 생성/조회/취소/관리, 동시성 처리(Redis 분산 락 + DB 락) | db_reservation |
| payment | 결제 처리. 결제 이력 조회/환불. pg 서버에 거래 승인/취소 요청(HTTP 기본, gRPC 옵션 — `booking.pg.protocol`) | db_payment |
| notification | Mock 알림 발송. 발송 이력 저장 | db_notification |
| pg | Mock PG 서버 (외부 시스템). 거래 승인/취소 API. 20% 확률로 실패 반환 | 없음 (stateless) |

---

## 서비스 간 통신

### 동기 (HTTP / RestClient)

| 호출 방향 | 엔드포인트 | 용도 |
|-----------|-----------|------|
| api → notification | `POST /api/v1/internal/messages` | 관리자가 특정 유저에게 메시지 발송 |
| payment → pg | `POST /pg/approve`, `POST /pg/cancel` | Mock PG 서버에 거래 승인/취소 요청 (기본값) |
| api → LiteLLM | `POST /chat/completions` (OpenAI 호환) | AI 사용법 안내 챗봇 — LiteLLM이 Claude(Anthropic)/로컬 Ollama 모델로 라우팅 |
| api → reservation | `GET /api/v1/reservations/me` | 챗봇 tool-use — 호출자 JWT를 forward해서 본인 예약 목록 조회 |
| api → Loki | `GET /loki/api/v1/query_range` | 챗봇 tool-use — 전체 사용자: 호출자 userId로 범위를 좁힌 최근 예약 실패 로그 조회 / ADMIN: 범위 제한 없는 임의 LogQL 조회(`run_logql`) |
| api → MySQL (db_reservation/db_payment 등, 자체 DB 외) | 직접 JDBC(`AdminSqlConfig`) | 챗봇 tool-use — ADMIN 전용 `run_sql`. database-per-service 경계를 의도적으로 넘는 예외, SELECT만 허용 |

> api → notification 구간에 Resilience4j 재시도(최대 3회, 500ms 간격, 네트워크 예외만 대상) + 서킷브레이커(`notification` 인스턴스)가 적용되어 있다. OPEN 시 즉시 503 (`API_004`) 반환. api → LiteLLM 구간도 동일한 패턴(별도 `litellm` 인스턴스)으로 보호된다. api → reservation/Loki(tool-use 호출)는 별도 서킷브레이커 없이, 각 tool이 실패를 잡아 에러 문자열을 Claude에게 돌려주는 방식으로 처리한다 — 어시스턴트 전체가 죽는 대신 "조회에 실패했습니다" 같은 답변으로 이어짐.

### 동기 (gRPC, 선택)

| 호출 방향 | 서비스 | 용도 |
|-----------|--------|------|
| payment → pg | `PgService.Approve`, `PgService.Cancel` (`pg.proto`) | REST 대신 gRPC로 동일 기능 호출 |

`payment`의 `booking.pg.protocol` 프로퍼티(`rest`(기본) / `grpc`)로 전환한다. `PgClientPort`의 구현체가 REST(`PgGatewayAdapter`)/gRPC(`PgGrpcGatewayAdapter`) 두 개 공존하며 `@ConditionalOnProperty`로 하나만 활성화된다 — 포트/어댑터 분리 덕분에 `PaymentService` 쪽 코드는 프로토콜 전환과 무관하다. `pg`는 REST(`:8090`)와 gRPC(`:50051`)를 동시에 노출한다. `.proto` 파일은 공유 모듈 없이 `pg`/`payment` 양쪽에 동일하게 복사돼 있다(수동 동기화 필요).

### 비동기 (Kafka)

| 토픽 | producer | consumer | 페이로드 |
|------|----------|----------|----------|
| `user.created` | api | reservation, payment, notification | userId, name, email, phone |
| `user.updated` | api | reservation, payment, notification | userId, name, email, phone |
| `user.deleted` | api | reservation, payment, notification | userId |
| `reservation.created` | reservation | payment | reservationId, userId, resourceId, amount |
| `payment.completed` | payment | reservation, notification | paymentId, reservationId, userId |
| `payment.failed` | payment | reservation | reservationId, userId |
| `reservation.cancelled` | reservation | notification | reservationId, userId |
| `reservation.reminder` | reservation | notification | reservationId, userId |

### 이벤트 페이로드 예시

**reservation.created**
```json
{
  "reservationId": 1,
  "userId": 10,
  "resourceId": 5,
  "amount": 50000
}
```

**payment.completed**
```json
{
  "paymentId": 1,
  "reservationId": 1,
  "userId": 10
}
```

> Kafka send 는 트랜잭션 커밋 이후 발행되어야 함 (`@TransactionalEventListener(AFTER_COMMIT)` 또는 Outbox 패턴). 그렇지 않으면 DB 롤백 시 유령 이벤트가 흘러간다.

### Consumer 에러 처리 (재시도 + DLT)

`@KafkaListener` 메서드는 예외를 삼키지 않고 그대로 던진다 — 삼키면 오프셋이 정상 커밋돼버려 에러 핸들러가 개입할 기회가 없다. 대부분의 리스너는 `@RetryableTopic(attempts = "3", backOff = @BackOff(delay = 1000))`으로 선언돼 있어, 처리 실패 시 `{topic}-retry` 토픽에서 1초 간격으로 재시도하고 3회 모두 실패하면 `{topic}-dlt` 토픽으로 보낸다. `@DltHandler`는 `KafkaHeaders.ORIGINAL_TOPIC` 헤더로 원본 토픽명을 로그에 남긴다.

**예외**: `payment`의 `reservation.created` 컨슈머(결제 승인)만 `reservationCreatedContainerFactory`(`DefaultErrorHandler` + `FixedBackOff(5000, Long.MAX_VALUE)`)를 명시적으로 지정해 무한 재시도를 유지한다 — `docs/99-simulations.md`의 "Kafka Consumer Lag" 시뮬레이션(`error@bookit.com`)이 이 동작에 의존하기 때문에 의도적으로 DLT 교체 대상에서 제외했다.

`@RetryableTopic`이 재시도/DLT 토픽에 원본 메시지를 재발행할 때 쓰는 템플릿은 각 서비스의 기존 `kafkaTemplate`(JsonSerializer, 도메인 이벤트 발행용)이 아니라 별도의 `retryKafkaTemplate`(키·값 모두 `StringSerializer`)이다 — 컨슈머 값이 이미 raw JSON 문자열이라 JsonSerializer를 쓰면 다시 감싸(이중 인코딩)버린다.

### DLT 재처리 — notification에만 존재

`@RetryableTopic`은 실패한 메시지를 별도 `-retry` 토픽으로 옮기고 원본 토픽은 계속 다음 메시지를 처리하는 **non-blocking 재시도**다. 그래서 같은 엔티티에 대한 연속된 이벤트(예: `user.created` → `user.updated`, `payment.completed`/`payment.failed`)가 처리 순서를 보장받지 못할 수 있다 — 실패한 메시지가 재시도되는 동안 그 뒤에 온 메시지가 먼저 성공해버릴 수 있기 때문이다.

이 때문에 **DLT에 쌓인 메시지를 원본 토픽으로 재발행하는 기능(`notification`의 `DltReplayService`, `POST /api/v1/internal/dlt/{dltTopic}/replay`)은 순서가 중요하지 않은 notification의 DLT 토픽에만 한정**한다(`user.created-dlt`/`user.updated-dlt`/`user.deleted-dlt`/`payment.completed-dlt`/`reservation.cancelled-dlt`/`reservation.reminder-dlt`). notification은 알림을 한 번 더 보내는 정도의 재배열은 문제가 되지 않지만, reservation/payment의 `user.*`·`payment.completed`/`payment.failed`는 순서가 깨지면 상태가 잘못될 수 있어 재처리 도구를 아직 두지 않았다 — 이 쪽은 blocking 재시도(같은 파티션에서 멈춰서 재시도)로 메커니즘 자체를 바꾸는 방안을 별도로 검토 중이며, 지금은 `kafka-console-consumer`로 직접 조회하는 정도로만 다룬다.

재처리 동작: 호출 시점의 DLT 토픽 끝 오프셋을 스냅샷으로 고정해 그 범위까지만 원본 토픽으로 재발행한다 — 재발행한 메시지가 다시 실패해 같은 DLT 토픽으로 돌아와도 이번 호출에서는 쫓아가지 않는다(안 그러면 영구 실패 메시지가 무한 재처리 루프를 만든다). 전용 컨슈머 그룹(`notification-dlt-replay`)으로 오프셋을 커밋하기 때문에, 다음 호출에선 그 사이에 새로 들어온 메시지만 처리한다.

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

## DB 스키마 관리 (Flyway)

`api`/`reservation`/`payment`/`notification` 4개 서비스는 각자 `src/main/resources/db/migration/V1__init.sql`로 자기 DB의 테이블을 관리한다(`jpa.hibernate.ddl-auto: none` + Flyway 전담, 테스트 프로파일도 동일). `batch`/`review`는 `db_reservation`을 공유만 할 뿐 소유하지 않으므로 Flyway를 두지 않는다 — 스키마는 reservation의 마이그레이션이 전담한다.

`docker/mysql/init/01-create-databases.sql`은 빈 DB 4개만 만든다. 테이블 생성은 각 서비스가 처음 기동(앱 실행 또는 테스트)할 때 Flyway가 수행한다.

```
docker compose up (DB만 생성, 테이블 없음)
  → 서비스 최초 기동
      → Flyway: flyway_schema_history 없음 확인
          → 스키마 비어있음 → V1__init.sql 실행 (CREATE TABLE ...)
          → 스키마에 테이블 이미 존재(기존 환경) → baseline-on-migrate로 현재 상태를 버전 1로 베이스라인 처리, V1 재실행 안 함
```

`spring.flyway.baseline-on-migrate: true`로 설정돼 있어, 기존에 raw SQL 스크립트로 이미 테이블이 만들어진 환경(이 레포를 Flyway 도입 이전부터 써온 로컬 DB)에서도 `CREATE TABLE` 충돌 없이 그대로 적용된다 — Flyway가 "이미 여기까지는 돼 있다"고 베이스라인만 찍고 넘어간다. 이후 스키마 변경은 전부 `V2__*.sql`부터 새 마이그레이션 파일로 추가한다(기존 파일은 절대 수정하지 않음 — Flyway는 체크섬으로 변조를 감지한다).

시드 데이터(`docker/mysql/03-seed-data.sql`)는 Flyway 마이그레이션에 포함하지 않았다 — `db_api.users`를 다른 서비스 DB가 교차 조회(cross-database SELECT)하는 구조라 서비스별 독립 마이그레이션으로 쪼개기 어렵고, 서비스 기동 순서에 의존하게 돼 오히려 더 취약해진다. 대신 4개 서비스를 한 번씩 기동(테이블 생성)한 뒤 수동으로 한 번 실행하는 운영 스크립트로 남겨뒀다. 상세는 `README.md` 참고.

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

> 핵심 4개 서비스만 표시한 예시다. `review`는 같은 방식으로 ECS task + ALB 라우팅을 추가하면 되고(`db_reservation` RDS 공유), `batch`는 공개 ALB 라우팅 없이 ECS Scheduled Task(또는 Lambda)로 별도 운영한다 — 다만 `/actuator/prometheus`는 VPC 내부에서 Prometheus가 스크랩할 수 있도록 서비스 디스커버리(Cloud Map 등)로만 노출한다. `pg`는 실제 PG사 연동 시 제거되는 로컬/스테이징 전용 목이므로 AWS 구성에서 제외한다.

---

## 테스트 전략

- 단위/통합 테스트는 각 서비스 모듈 내부에서
- E2E: 4개 서비스 + Kafka + Redis + MySQL 을 docker-compose 로 띄워 시나리오 검증
- 동시성: JMeter 또는 k6 로 동일 시간대에 100명 동시 요청
  - 1건만 성공 확인
  - 99건은 409 응답 확인
  - DB 중복 없음 확인

