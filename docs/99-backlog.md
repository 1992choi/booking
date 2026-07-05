# 99. Backlog

## 동시성 보강

### Redis 분산락
예약 생성(`POST /api/v1/reservations`) 진입 시 Redisson `tryLock`으로 동일 resourceId 동시 요청을 직렬화.
실패 시 409 RSV_002 반환.

```java
RLock lock = redissonClient.getLock("reservation:lock:" + resourceId);
if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
    throw new BusinessException(ReservationErrorCode.LOCK_FAILED);
}
```

### DB 비관적락
`findOverlapping` 쿼리에 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 추가.
Redis 락이 뚫렸을 때의 마지막 방어선.

---

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

## 관찰가능성 스택 고도화 (Grafana)

현재 Zipkin(분산추적)만 구성된 상태. Prometheus + Loki + Tempo + Grafana를 추가해 메트릭/로그/트레이스를 단일 화면에서 연결하여 볼 수 있도록 고도화.

- Prometheus: 각 서비스 메트릭 수집 (`spring-boot-starter-actuator` + micrometer 이미 적용)
- Loki: 로그 수집 (Promtail 또는 Loki Logback Appender)
- Tempo: Zipkin 대체 분산추적 백엔드 (OTel exporter를 OTLP로 교체)
- Grafana: 메트릭/로그/트레이스 통합 대시보드
- docker-compose에 위 4개 컨테이너 추가

---

## QueryDSL

동적 쿼리가 필요한 목록 조회에 적용. 현재 `@Query` JPQL로 작성된 정적 쿼리를 보완.

- 적용 대상: 예약 목록 (`status`, `date` 필터 조합), 업체별 예약 캘린더 조회
- `JPAQueryFactory` 빈 등록 + Q클래스 생성 설정 (각 서비스 `build.gradle`)

---

## batch 모듈 (Spring Batch)

독립 모듈(`batch`)로 추가. 구체적인 기능은 미정이나 아래 방향 중 하나 이상 적용 예정.

- **통계 처리**: 일별/월별 예약 건수, 매출 집계
- **이벤트 트리거**: 미완료 예약 자동 만료 (PENDING 상태 N시간 초과 시 CANCELLED), 슬롯 복원
- Chunk 기반 처리, JobParameter로 실행 기준 제어
- `docker-compose`에 배치 실행 환경 추가

---

## CQRS

예약 조회(Read)와 생성/취소(Write) 모델 분리. 현재는 동일 엔티티로 읽기/쓰기를 모두 처리.

- Write 모델: 기존 JPA 엔티티 유지
- Read 모델: 조회 전용 DTO/Repository 분리 (QueryDSL 또는 별도 Read DB)
- 적용 대상: 예약 목록 조회, 업체별 캘린더 뷰 — 조회 빈도가 높고 Write와 요구사항이 달라 분리 효과가 큼

---

## 업체(Merchant) 리뷰 기능

### 배경
학습 목적으로 신규 모듈을 Kotlin으로 개발하고 싶음. api/reservation은 핵심 모듈이라 계속 Java/Spring으로 유지, payment/notification은 껍데기 모듈이라 전환 의미가 없어 대상에서 제외. 간단한 CRUD 위주인 리뷰 기능이 학습용 신규 모듈로 적합.

### 모듈 구성
- **신규 `review` 모듈** (Kotlin + Spring Boot). api/reservation/payment/notification과 별개의 독립 배포 단위.
- **DB는 분리하지 않음** — `db_reservation`을 그대로 공유 (같은 인스턴스/스키마). 새 DB 프로비저닝 오버헤드를 피하기 위한 선택.
- review 모듈이 소유하는 것은 신설되는 `review` 테이블 하나뿐. Merchant/Reservation 테이블에는 쓰기 권한 없음.

### 서비스 간 호출 없음
- review 모듈에서 reservation **서비스**를 REST로 호출하는 구조는 채택하지 않음 (네트워크 홉·장애 지점 추가 대비 이득 없음 — 이미 DB를 공유하기로 한 이상 서비스 호출로 결합도를 낮추는 의미가 없음).
- 리뷰 작성 자격 검증(`본인이 CONFIRMED 예약을 했는가`)은 review 모듈이 **`db_reservation`에 직접 연결**해 `Reservation` 테이블을 read-only로 조회해서 처리 (`reservation_id` → `userId`, `merchantId`, `status` 확인).
- review 모듈 쪽에는 검증에 필요한 최소 컬럼만 매핑한 읽기 전용 엔티티(또는 native query)를 둔다. 쓰기는 자기 테이블(`review`)에만 한정.

### 리뷰 도메인
- 별점 없음 — 단순 텍스트 코멘트만 남기는 리뷰
- 작성 시점: 예약이 `CONFIRMED` 되면 즉시 작성 가능 (이용 종료 시점까지 기다리지 않음)
- `reservation_id` UNIQUE — 예약 1건당 리뷰 1개
- 수정/삭제는 작성자 본인만 가능
- 예약이 이후 `CANCELLED` 되어도 리뷰 연동 처리는 하지 않음 (별도 이벤트 구독 없이 리뷰는 그대로 유지)

### API (review 모듈이 전용 소유)
`/api/v1/merchants/**`는 이미 reservation이 소유한 prefix라 겹치지 않도록, 리뷰는 별도 prefix로 분리.

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/reviews` | 리뷰 작성 (body: `reservationId`, `content`) |
| GET | `/api/v1/reviews?merchantId={id}` | 업체별 리뷰 목록 (공개) |
| PATCH | `/api/v1/reviews/{reviewId}` | 리뷰 수정 (작성자 본인) |
| DELETE | `/api/v1/reviews/{reviewId}` | 리뷰 삭제 (작성자 본인) |

### 미정 사항 (구현 시 결정)
- 포트 번호 (현재 8080~8083, pg는 8090 — 8084 등 빈 번호 배정 필요)
- Kotlin 모듈에서 core(Java 라이브러리) 의존 방식 — JwtVerifier, BaseEntity, ErrorCode 등 재사용 가능 여부 확인
- review 전용 ErrorCode enum 신설 필요 (예: `REVIEW_001` 리뷰 없음, `REVIEW_002` 본인 리뷰 아님, `REVIEW_003` 이미 리뷰 작성됨, `REVIEW_004` 예약이 CONFIRMED 상태 아님)