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
    ├── controller/
    │   └── PaymentController.java
    ├── service/
    │   ├── PaymentService.java
    │   └── MockPaymentGateway.java       (실제 PG 연동 시 교체 지점)
    ├── domain/
    │   ├── Payment.java
    │   └── PaymentRepository.java
    ├── event/
    │   ├── ReservationEventConsumer.java
    │   └── PaymentEventPublisher.java
    ├── user/
    │   ├── domain/UserSync.java
    │   └── event/UserEventConsumer.java  (user.created/updated/deleted 구독)
    ├── error/
    │   └── PaymentErrorCode.java
    ├── dto/
    │   └── PaymentResponse.java
    └── config/
        └── KafkaConfig.java
```

---

## 핵심 로직

`reservation.created` consume → Payment 레코드 생성 (PENDING) → `MockPaymentGateway.charge()` 호출 → 성공이면 COMPLETED, 실패면 FAILED 로 상태 전이 → `AFTER_COMMIT` 에 결과 이벤트 Kafka publish.

`MockPaymentGateway` 는 항상 성공을 반환한다. 실제 PG 연동 시 이 클래스만 교체하면 된다.

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