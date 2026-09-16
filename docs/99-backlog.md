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

## Kafka DLQ / 재시도 전략

### 배경

서비스마다 Kafka consumer 에러 처리가 제각각이다. `payment`만 `DefaultErrorHandler(FixedBackOff(5000ms, Long.MAX_VALUE))`로 무한 재시도를 걸어뒀는데, 포이즌 필(계속 실패하는 메시지) 하나가 해당 파티션 처리를 영원히 막을 수 있다. `reservation`/`notification`은 에러 핸들러 자체가 없어 Spring Kafka 기본값(제한된 재시도 후 스킵)에 맡겨져 있고, 이 경우 실패한 메시지가 별도 흔적 없이 조용히 유실된다.

**더 근본적인 문제(재확인)**: `reservation`/`payment`/`notification`의 `@KafkaListener` 메서드 대부분(`UserEventConsumer` 3곳, `reservation`의 `PaymentEventConsumer`, `notification`의 `PaymentEventConsumer`/`ReservationEventConsumer`)이 리스너 본문 전체를 `try { ... } catch (Exception e) { log.error(...) }`로 감싸고 재던지지 않는다. Spring Kafka의 에러 핸들러(재시도/DLQ)는 리스너가 **예외를 던져야만** 개입하는데, 지금은 예외가 컨슈머 내부에서 소비되고 오프셋은 정상 커밋돼버린다. 즉 **아래 해결 방향(DLT 도입)을 그대로 적용해도 이 catch 블록들 때문에 대부분 컨슈머에서 아무 효과가 없다** — 예외적으로 `payment`의 `ReservationEventConsumer`(reservation.created 처리)만 예외를 잡지 않고 그대로 던져서 정상적으로 에러 핸들러까지 도달한다.

### 해결 방향

- **선행 작업**: 위 컨슈머들의 `catch (Exception e) { log.error(...) }`를 제거(또는 최소한 재던지기)해서 예외가 컨테이너 에러 핸들러까지 전파되도록 수정 — 이게 없으면 DLQ를 붙여도 무의미
- `@RetryableTopic`(또는 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`)로 N회 재시도 후 실패 메시지를 `.DLT` 토픽으로 이동
- 재시도 횟수/backoff는 이벤트 성격에 맞게 서비스별로 조정(예: payment는 결제 승인 실패가 비쌀 수 있으니 더 신중하게)
- DLT로 넘어간 메시지를 조회/재처리할 수 있는 최소한의 수단 마련(운영 도구 없이 `kafka-console-consumer`로 조회하는 정도부터 시작 가능)
- (선택, 곁다리 정리) 4개 서비스의 `KafkaConfig`가 수동 `@Bean`으로 `KafkaTemplate`/`ConcurrentKafkaListenerContainerFactory`를 만드는데, 이미 yml에 있는 `spring.kafka.producer/consumer.*` 프로퍼티와 내용이 겹친다 — Boot 자동구성으로 교체하고 payment의 커스텀 에러 핸들러만 `ContainerCustomizer` 빈으로 유지하는 것도 이 작업과 함께 정리하면 좋음

### 적용 대상

| 서비스 | 토픽 | 비고 |
|--------|------|------|
| reservation | payment.completed, payment.failed, user.* | catch 제거 + 에러 핸들러 신규 추가 |
| payment | reservation.created, user.* | user.* 는 catch 제거 필요. reservation.created 는 기존 무한 재시도 → DLT로 교체 |
| notification | payment.completed, reservation.cancelled, user.* | catch 제거 + 에러 핸들러 신규 추가 |

---

## Testcontainers 도입

### 배경

지금 모든 테스트는 로컬 docker-compose로 띄운 실제 MySQL/Redis/Kafka/MongoDB에 직접 연결해서 돈다. 감사 로그 기능 작업 중 이 방식의 부작용을 실제로 겪었다 — Mongo 데이터가 테스트 트랜잭션 롤백 대상이 아니라 실행할 때마다 누적되고, 처음엔 테스트 이메일을 고정 문자열로 써서 반복 실행 시 실패했다(`AuthAuditTest`, `UUID` 기반 이메일로 우회). 인프라가 안 떠 있으면 테스트 자체가 실패하고, 여러 세션이 같은 로컬 인프라를 공유하면 서로 데이터가 섞일 위험도 있다.

### 해결 방향

- 각 서비스 테스트에 Testcontainers(MySQL/Redis/Kafka/MongoDB 모듈)를 도입해 테스트마다 격리된 컨테이너를 뜨고 내리도록 전환
- `@ServiceConnection` 또는 `@DynamicPropertySource`로 커넥션 정보를 자동 주입
- 로컬 docker-compose 인프라와 병행 가능(개발 중 빠른 반복은 기존 방식, CI/정식 테스트 실행은 Testcontainers)

### 적용 대상

| 서비스 | 필요 컨테이너 |
|--------|--------------|
| api | MySQL, Redis, Kafka, MongoDB |
| reservation | MySQL, Redis, Kafka, MongoDB |
| payment | MySQL, Kafka |
| notification | MySQL, Kafka |
| review | MySQL(reservation과 공유) |

---

## Flyway 도입 (DB 마이그레이션)

### 배경

지금 스키마는 `docker/mysql/init`의 raw SQL 스크립트로 초기화되고, 서비스별로 `jpa.hibernate.ddl-auto: none`(운영/기본) 또는 `update`(일부 테스트)로 관리된다. 버전 관리되는 마이그레이션 이력이 없어 스키마 변경 시점과 내용을 코드 히스토리 밖에서는 추적할 수 없고, `ddl-auto: update`는 운영에서 쓰면 위험한 설정이라 테스트에서만 쓰고 있는 상태다.

### 해결 방향

- 서비스별로 `src/main/resources/db/migration/V1__init.sql`부터 시작해 `docker/mysql/init`의 기존 스키마를 마이그레이션 파일로 이관
- `ddl-auto: none` + Flyway가 스키마를 전담하도록 정리(테스트의 `update`도 걷어내고 동일하게 Flyway로 통일)
- 이후 스키마 변경은 전부 새 마이그레이션 파일로 추가

### 적용 대상

api, reservation, payment, notification, batch/review(db_reservation 공유 — reservation 마이그레이션에 포함)

---

## GitHub Actions CI

### 배경

`.github/` 자체가 없어 PR/커밋마다 빌드·테스트가 자동으로 도는 장치가 전혀 없다. 지금은 로컬에서 수동으로 `./gradlew build`/`test`를 돌리는 것에 전적으로 의존한다.

### 해결 방향

- push/PR 트리거로 `./gradlew build` 실행하는 워크플로부터 시작
- 테스트에 필요한 MySQL/Redis/Kafka/MongoDB는 GitHub Actions의 `services:` 컨테이너로 띄우거나(위 Testcontainers 도입 시 별도 services 설정 없이도 가능해짐)
- 이후 필요하면 커버리지 리포트, 브랜치 보호 규칙 연동 등으로 확장

### 적용 대상

레포 전체 (`.github/workflows/ci.yml` 신규)

---

## Elasticsearch 검색

### 배경

업체(Merchant)/리소스(Resource) 조회는 지금 MySQL 쿼리(+ Redis 캐싱)로만 처리된다. 이름/설명 기반 검색이나 다중 조건 필터링처럼 관계형 쿼리로는 번거로운 기능은 아직 없다. RDB + 검색엔진 이원화는 실무에서 흔한 조합이라 학습 가치가 크다.

### 해결 방향

- Merchant/Resource 데이터를 Elasticsearch에 색인(최초엔 Kafka 이벤트 없이 동기 이중 쓰기로 시작 가능, 이후 필요하면 CDC/이벤트 기반으로 발전)
- 업체명/리소스명 검색, 카테고리·가격대 필터링 등을 Elasticsearch 쿼리로 제공하는 신규 검색 엔드포인트 추가
- 기존 `GET /api/v1/merchants` 등 목록 조회는 그대로 두고, 검색은 별도 엔드포인트로 분리(Elasticsearch 장애가 기존 기능에 영향 주지 않도록)

### 적용 대상

reservation (Merchant/Resource 소유 서비스)

---

## Virtual Threads 적용

### 배경

Java 25(가상 스레드가 정식 기능인 버전)를 쓰고 있는데 정작 활성화는 안 돼 있다. Spring Boot 4.0.5에서 `spring.threads.virtual.enabled` 프로퍼티로 지원 확인됨(`spring-boot-autoconfigure` 설정 메타데이터에 존재). 블로킹 I/O(JDBC, RestClient 등)가 많은 이 프로젝트 구조상 가상 스레드 도입 효과를 체감하기 좋은 조건이다.

### 해결 방향

- 서비스별로 `spring.threads.virtual.enabled: true` 설정 후 동작 확인(Tomcat 요청 처리 스레드가 가상 스레드로 전환)
- HikariCP 커넥션 풀 크기 등 기존에 플랫폼 스레드 기준으로 잡았던 설정이 가상 스레드 환경에서도 적절한지 재검토(가상 스레드는 수가 많아지므로 커넥션 풀 같은 진짜 제한 자원의 병목이 오히려 더 잘 드러남)
- 부하 테스트로 전/후 비교(간단하게는 동시 요청 처리량 차이 확인 정도)

### 적용 대상

api, reservation, payment, notification (전부 Tomcat + 블로킹 I/O 기반)

---

## Structured Logging (JSON) 적용

### 배경

로그가 지금은 평문 텍스트로 Loki에 쌓이고 있어서, 특정 필드(예: userId, reservationId) 기준으로 정확히 필터링하려면 텍스트 파싱에 의존해야 한다. Spring Boot 4.0.5는 별도 라이브러리 없이 `logging.structured.format.console`/`logging.structured.format.file`로 JSON 구조화 로그(ecs/gelf/logstash 포맷)를 내장 지원한다(설정 메타데이터에서 확인).

### 해결 방향

- `logging.structured.format.console: ecs` 등으로 콘솔 로그를 JSON으로 전환(각 서비스 `logback-spring.xml`이 Boot 기본 `base.xml`을 include하는 구조라 큰 개조 없이 적용 가능할 것으로 보임 — 실제 적용 시 LOKI appender와의 상호작용 확인 필요)
- `logging.structured.json.include`/`context.include`로 MDC에 있는 값(예: traceId — Tempo 연동 시 이미 MDC에 들어감) 로그에 포함시켜 Loki-Tempo 연계 강화
- Loki 쿼리에서 JSON 필드 기반 필터링(`| json` 파이프라인)으로 전환

### 적용 대상

api, reservation, payment, notification (Loki 로깅 대상과 동일)

---

## Idempotency Key 패턴

### 배경

예약 생성(`POST /api/v1/reservations`)이나 결제 관련 API가 네트워크 재시도/중복 클릭으로 같은 요청이 두 번 들어와도 지금은 구분할 방법이 없다(Redis 분산 락은 동시 요청 간 경합만 막을 뿐, 같은 클라이언트의 중복 제출 자체를 막지는 않음). Stripe 등에서 흔히 쓰는 `Idempotency-Key` 헤더 패턴으로, 최근 API 설계에서 자주 요구되는 방식이다.

### 해결 방향

- 클라이언트가 `Idempotency-Key` 헤더로 요청마다 고유 키를 보내면, 서버는 (key, 응답) 쌍을 짧은 TTL로 Redis에 저장
- 동일 키로 재요청이 오면 실제 로직을 다시 실행하지 않고 저장해둔 응답을 그대로 반환
- reservation은 이미 Redis(Redisson)를 쓰고 있어 인프라 추가 없이 적용 가능

### 적용 대상

reservation (`POST /api/v1/reservations`), 필요하면 payment 쪽 결제 요청 API에도 확장

---

## 업체 목록 조회 페이징 적용

### 배경

`GET /api/v1/merchants`가 `MerchantService.getAll()` → `merchantRepository.findAll()`로 전체 업체를 한 번에 조회해 반환한다. 예약 목록 조회(`GET /api/v1/reservations/me` 등)는 이미 `PageResponse` 기반 페이징이 적용돼 있는데, 업체 목록만 페이징 없이 남아있어 업체 수가 늘어나면 응답 크기와 쿼리 비용이 그대로 선형으로 커진다.

### 해결 방향

- `MerchantController.getMerchants()`가 `Pageable`을 받아 `MerchantService.getAll()`도 `Page<Merchant>`를 반환하도록 변경
- 예약 목록 조회에 이미 쓰고 있는 `PageResponse` DTO를 그대로 재사용

### 적용 대상

reservation (`merchant/controller/MerchantController.java`, `merchant/service/MerchantService.java`)

---

## Kafka 메시지 Avro + Schema Registry 전환

### 배경

지금 Kafka 이벤트(`user.created`/`user.updated`/`user.deleted`, `reservation.created`, `payment.completed`/`payment.failed`, `reservation.cancelled`)는 전부 `JsonSerializer`로 발행된다. 스키마가 코드(DTO)에만 존재해서, 프로듀서가 필드를 추가/변경/삭제해도 컨슈머가 역직렬화에 실패하는지는 배포 후 런타임에 가서야 드러난다.

### 해결 방향

- Confluent Schema Registry(또는 Apicurio Registry) 컨테이너를 docker-compose에 추가
- 이벤트별 `.avsc` 스키마 정의, 호환성 모드는 `BACKWARD`로 설정
- `KafkaAvroSerializer`/`KafkaAvroDeserializer`로 교체 — 프로듀서는 발행 전 레지스트리에 스키마를 등록/검증하고, 컨슈머는 메시지에 실린 스키마 ID로 레지스트리에서 스키마를 조회해 디코딩
- 기존 이벤트 중 하나(예: `reservation.created`)부터 먼저 전환해 파이프라인 검증 후 나머지로 확장

### 적용 대상

api, reservation, payment, notification (Kafka 발행/구독 전체)

---

## WebSocket/SSE 실시간 알림

### 배경

지금은 결제 완료/예약 취소 등이 Kafka를 통해 `notification`까지만 전달되고, 그 뒤로는 mock 발송(로그만 남김)에서 끝난다. 클라이언트가 예약 상태 변화를 알려면 폴링 외엔 방법이 없다. 이 프로젝트엔 별도 프론트엔드가 없으므로, 최소한의 데모 페이지(`EventSource`/`WebSocket` 브라우저 API로 메시지를 화면에 출력하는 정도)로 동작을 확인하는 것을 전제로 한다.

### 해결 방향

- `notification`에 WebSocket(STOMP) 또는 SSE 엔드포인트 추가, 유저ID ↔ 세션 매핑 관리
- `payment.completed`/`payment.failed`/`reservation.cancelled` Kafka 컨슈머가 처리 후 해당 유저의 활성 세션에 실시간 push
- 최소 데모 HTML 페이지에서 로그인 후 연결 → 이벤트 발생 시 새로고침 없이 알림 수신 확인

### 적용 대상

notification

---

## 배치 통계 지표의 Micrometer 노출

### 배경

`batch`가 업체 일별 통계를 집계하지만 결과가 DB 테이블에만 남고, Prometheus/Grafana로는 노출되지 않는다(`batch`는 actuator 미적용이라 현재 스크랩 대상에서도 빠져 있다). 지금 Prometheus가 보는 지표는 `http_server_requests_seconds_count` 등 요청 처리량/JVM 지표뿐이라, "오늘 예약이 몇 건이었나" 같은 도메인 지표는 대시보드에서 전혀 보이지 않는다.

### 해결 방향

- `batch`에 actuator + micrometer-registry-prometheus 의존성 추가, `docker/prometheus/prometheus.yml` 스크랩 대상에 편입
- 일별 통계 집계 결과를 `Gauge`로 노출(예: `merchant_daily_reservation_count`, `merchant_daily_revenue`)
- Grafana에 비즈니스 지표 전용 패널 추가

### 적용 대상

batch (업체 일별 통계 집계 잡)

---

## Caffeine + Redis 다중 레이어 캐싱

### 배경

지금 업체 조회 캐싱은 Redis 단일 레이어(Spring Cache)만 쓴다. 캐시가 히트해도 매번 로컬→Redis 네트워크 왕복이 발생한다.

### 해결 방향

- `reservation`의 업체 조회 캐싱 지점에 Caffeine(로컬 JVM 메모리, L1)을 앞단에 추가
- `@Cacheable` 자동 다단 캐싱 대신, 서비스 메서드 안에서 직접 Caffeine 조회 → miss 시 Redis(L2) 조회 → miss 시 DB 조회 후 양쪽 캐시를 채우는 흐름으로 구현(학습 목적상 동작을 명시적으로 다루기 위함)
- (선택, 난이도 높음) 한 인스턴스에서 업체 정보가 갱신됐을 때 다른 인스턴스의 L1을 무효화하는 문제 — Redis Pub/Sub으로 무효화 신호를 전파하는 방식 검토

### 적용 대상

reservation (업체 조회 캐싱 지점)
