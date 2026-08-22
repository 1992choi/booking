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

## 서비스 간 분산추적 연결 (Kafka/RestClient observation propagation 누락)

### 배경

Tempo 전환 후 실제로 서비스 간 흐름이 하나의 트레이스로 이어지는지 검증한 결과, **끊어져 있음을 확인**했다. `/api/v1/auth/signup` 호출로 `user.created`를 발행시키고 Tempo에서 추적해보면, `api`의 스팬만 담긴 트레이스로 끝나고 `reservation`이 그 이벤트를 소비하며 만든 스팬은 전혀 다른(연결 안 된) traceId로 찍힌다. Kafka 토픽을 직접 consume해서 헤더를 까보면 `traceparent`가 아예 없다(`NO_HEADERS`). Zipkin이었을 때도 동일 원인으로 동작하지 않았을 것이므로 이번 Tempo 전환으로 생긴 회귀가 아니라 기존부터 있던 문제다.

**원인**: `api`/`reservation`/`payment`/`notification` 4개 서비스 모두 `config/KafkaConfig.java`에서 `KafkaTemplate`/`ConcurrentKafkaListenerContainerFactory`를 직접 `new`로 생성해 `@Bean` 등록한다. `application.yml`에는 이미 `spring.kafka.template.observation-enabled: true` / `listener.observation-enabled: true`가 설정돼 있지만, 이 프로퍼티는 Spring Boot가 자동구성한 `KafkaTemplate`/컨테이너 팩토리에만 적용된다 — 수동 `@Bean`이 존재하면 Boot 자동구성이 통째로 back off 되어 프로퍼티가 조용히 무시된다. 그 결과 `ObservationRegistry`가 전혀 주입되지 않고, `TracingAutoConfiguration`이 등록한 `PropagatingSenderTracingObservationHandler`/`PropagatingReceiverTracingObservationHandler`도 Kafka 송수신에는 관여하지 못한다.

동일한 패턴으로 `api`의 `RestClientConfig`(`notificationRestClient`)와 `payment`의 `RestClientConfig`(`pgRestClient`)도 Spring이 주입하는 `RestClient.Builder` 빈 대신 static `RestClient.builder()`를 직접 호출해서 Boot의 observation 자동구성(`ObservationRestClientCustomizer`)을 우회한다 — `api→notification`(관리자 메시지 발송), `payment→pg` 동기 호출도 트레이스가 이어지지 않는다.

**현재 상태 정리**
- 단일 서비스 내부(동일 요청 스레드) 흐름: 정상 연결됨
- 서비스 간 Kafka 이벤트 체인(`reservation.created`→payment, `payment.completed`→reservation/notification, `user.*` 등): 끊김
- `api→notification`, `payment→pg` 동기 REST 호출: 끊김

### 해결 방향

- Kafka: 각 서비스 `KafkaConfig`의 수동 `KafkaTemplate`/`ConcurrentKafkaListenerContainerFactory`에 `ObservationRegistry`를 주입하고 `setObservationEnabled(true)`를 명시적으로 호출 (또는 payment의 커스텀 `CommonErrorHandler`처럼 yml만으로 표현 안 되는 설정은 유지하되, 나머지는 수동 빈을 걷어내고 이미 존재하는 `spring.kafka.*` yml 프로퍼티 기반 Boot 자동구성에 맡기는 방법도 검토)
- RestClient: `RestClient.builder()` static 호출 대신 Spring이 주입하는 `RestClient.Builder` 빈을 받아 `.baseUrl(...)`만 얹는 방식으로 변경

### 적용 대상

| 서비스 | 파일 |
|--------|------|
| api | `config/KafkaConfig.java`, `config/RestClientConfig.java` |
| reservation | `config/KafkaConfig.java` |
| payment | `config/KafkaConfig.java`, `config/RestClientConfig.java` |
| notification | `config/KafkaConfig.java` |

---

## CQRS

예약 조회(Read)와 생성/취소(Write) 모델 분리. 현재는 동일 엔티티로 읽기/쓰기를 모두 처리.

- Write 모델: 기존 JPA 엔티티 유지
- Read 모델: 조회 전용 DTO/Repository 분리 (QueryDSL 또는 별도 Read DB)
- 적용 대상: 예약 목록 조회, 업체별 캘린더 뷰 — 조회 빈도가 높고 Write와 요구사항이 달라 분리 효과가 큼
