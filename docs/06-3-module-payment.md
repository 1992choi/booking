# 06-3. payment 서비스

## 역할

결제 처리 (Mock). `reservation.created` 이벤트를 consume 해 결제 레코드를 만들고 처리 결과를 Kafka 로 publish 한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8082 |
| DB | db_payment |
| 외부 노출 | O (path: `/payments/**`) |
| 의존 | core (라이브러리), Kafka |
| 호출하는 서비스 | 없음 (유저 정보는 로컬 UserSync 테이블 사용) |

> **아키텍처 노트**: `payment`는 이 저장소에서 유일하게 헥사고날 아키텍처(포트/어댑터)를 적용한 모듈이다. 다른 서비스는 전형적인 레이어드 구조(`controller/service/domain`)를 그대로 쓴다 — `reservation`은 핵심 도메인이라 익숙한 구조를 유지하기로 했고, `payment`는 PG 연동이라는 명확한 외부 시스템 경계와 실제 상태 전이 도메인 로직(`PENDING→COMPLETED/FAILED→REFUNDED`)이 있어 헥사고날 학습 대상으로 선택했다. 도메인 모델(`domain/Payment`)은 JPA를 전혀 모르는 순수 객체이고, `adapter/out/persistence`의 `PaymentJpaEntity` + `PaymentMapper`가 영속성 매핑을 전담한다.

---

## 책임 도메인

payment 서비스가 자체 DB 에 소유:
- Payment

---

## 패키지 구조

```
payment/
└── src/main/java/com/example/booking/payment/
    ├── PaymentApplication.java
    ├── domain/                            ★ 순수 도메인 (Spring/JPA 의존성 없음)
    │   ├── Payment.java                   (createPending/reconstruct 팩토리, complete/fail/ensureRefundable/refund)
    │   └── PaymentStatus.java
    ├── application/                       ★ 유스케이스 + 포트
    │   ├── port/
    │   │   ├── in/
    │   │   │   ├── ChargePaymentUseCase.java / ChargePaymentCommand.java
    │   │   │   ├── RefundPaymentUseCase.java
    │   │   │   ├── GetPaymentUseCase.java
    │   │   │   └── PaymentResponse.java
    │   │   └── out/
    │   │       ├── LoadPaymentPort.java / SavePaymentPort.java   (Repository를 한 덩어리로 안 씀)
    │   │       ├── PgClientPort.java
    │   │       └── PaymentDeclinedException.java
    │   ├── event/
    │   │   ├── PaymentCompletedDomainEvent.java
    │   │   └── PaymentFailedDomainEvent.java
    │   └── PaymentService.java            (in-port 3개 구현, out-port 3개 의존, ApplicationEventPublisher로 도메인 이벤트 발행)
    ├── adapter/
    │   ├── in/
    │   │   ├── web/PaymentController.java
    │   │   └── messaging/
    │   │       ├── ReservationEventConsumer.java   (역직렬화 + Command 매핑)
    │   │       └── ReservationCreatedKafkaEvent.java
    │   └── out/
    │       ├── persistence/
    │       │   ├── PaymentJpaEntity.java / PaymentJpaRepository.java
    │       │   ├── PaymentPersistenceAdapter.java  (LoadPaymentPort/SavePaymentPort 구현)
    │       │   └── PaymentMapper.java              (도메인 ↔ JPA 엔티티 변환)
    │       ├── pg/
    │       │   ├── PgGatewayAdapter.java           (PgClientPort 구현, RestClient로 pg 서버 호출)
    │       │   └── dto/
    │       └── messaging/
    │           ├── PaymentEventPublisher.java      (@TransactionalEventListener(AFTER_COMMIT) → Kafka)
    │           └── PaymentCompletedKafkaEvent.java / PaymentFailedKafkaEvent.java
    ├── user/
    │   ├── domain/UserSync.java
    │   └── event/UserEventConsumer.java  (user.created/updated/deleted 구독)
    ├── error/
    │   └── PaymentErrorCode.java
    ├── system/
    │   └── PingController.java
    └── config/
        ├── SecurityConfig.java
        ├── KafkaConfig.java
        └── RestClientConfig.java         (pg 서버 RestClient 빈)
```

Kafka 발행(`PaymentEventPublisher`)은 별도 아웃바운드 포트로 감싸지 않는다 — `ApplicationEventPublisher.publishEvent(...)`가 이미 애플리케이션 서비스와 Kafka 사이를 분리하고, `AFTER_COMMIT` 시점 관찰은 Spring 트랜잭션 이벤트 메커니즘에 본질적으로 묶이는 인프라 관심사라 포트를 씌워도 같은 인터페이스를 한 번 더 감싸는 것에 불과하기 때문이다.

---

## 핵심 로직

`reservation.created` consume → Payment 레코드 생성 (PENDING) → `PgGatewayAdapter.charge()` 로 pg 서버(`:8090`)에 HTTP 승인 요청 → 성공이면 COMPLETED, 실패(402 또는 네트워크 오류)면 FAILED 로 상태 전이 → `AFTER_COMMIT` 에 결과 이벤트 Kafka publish.

pg 서버는 요청의 20% 확률로 402를 반환한다. 실제 PG 연동 시 `PgClientPort`의 구현체(`PgGatewayAdapter`)만 교체하면 된다 — 이게 포트/어댑터 분리의 핵심 이점이다.

---

## 접근 제어 (SecurityConfig)

```
/ping           → permitAll
/actuator/**    → permitAll
그 외            → authenticated
```

---

## PaymentErrorCode

| code | HTTP | 설명 |
|------|------|------|
| PAY_001 | 422 | 결제 실패 (비즈니스 결과) |
| PAY_002 | 409 | 환불 불가 상태 |
| PAY_003 | 404 | 결제 내역 없음 |
| PAY_004 | 422 | 환불 처리 실패 |

---

## Kafka

### consume
| 토픽 | 처리 |
|------|------|
| reservation.created | Mock 결제 처리 |

### produce
| 토픽 | 시점 | phase |
|------|------|-------|
| payment.completed | 결제 성공 | AFTER_COMMIT |
| payment.failed | 결제 실패 | AFTER_COMMIT |

