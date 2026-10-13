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

