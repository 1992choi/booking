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

### 해결 방향

- `@RetryableTopic`(또는 `DefaultErrorHandler` + `DeadLetterPublishingRecoverer`)로 N회 재시도 후 실패 메시지를 `.DLT` 토픽으로 이동
- 재시도 횟수/backoff는 이벤트 성격에 맞게 서비스별로 조정(예: payment는 결제 승인 실패가 비쌀 수 있으니 더 신중하게)
- DLT로 넘어간 메시지를 조회/재처리할 수 있는 최소한의 수단 마련(운영 도구 없이 `kafka-console-consumer`로 조회하는 정도부터 시작 가능)

### 적용 대상

| 서비스 | 토픽 | 비고 |
|--------|------|------|
| reservation | payment.completed, payment.failed, user.* | 에러 핸들러 신규 추가 |
| payment | reservation.created, user.* | 기존 무한 재시도 → DLT로 교체 |
| notification | payment.completed, reservation.cancelled, user.* | 에러 핸들러 신규 추가 |

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

## springdoc-openapi (Swagger UI)

### 배경

API 스펙은 지금 `04-api-spec.md`에 손으로 작성/유지된다. 코드가 바뀌어도 문서 갱신을 깜빡하면 실제 동작과 어긋날 수 있고, 실행 중인 API를 직접 호출해보며 확인할 수단이 없다(Postman 컬렉션도 없음).

### 해결 방향

- 외부 노출되는 서비스(api/reservation/payment/notification)에 `springdoc-openapi-starter-webmvc-ui` 추가
- `/swagger-ui.html`, `/v3/api-docs` 노출 — 단, `SecurityConfig`에서 permitAll 처리 필요(운영에서는 막거나 인증 뒤에 두는 것도 고려)
- 기존 `04-api-spec.md`는 서비스 소유권/도메인 설명 등 코드에서 안 드러나는 맥락 위주로 남기고, 엔드포인트 상세는 Swagger가 실제 소스가 되도록 역할 재정리

### 적용 대상

api, reservation, payment, notification (pg/batch는 내부/HTTP 없음이라 대상 아님)

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
