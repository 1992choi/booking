# 06-3. payment 서비스

## 역할

결제 처리 (Mock). `reservation.created` 이벤트를 consume 해 결제 레코드를 만들고 처리 결과를 `payment.completed` / `payment.failed` 로 publish 한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8082 |
| DB | payment_db |
| 외부 노출 | O (path: `/payments/**`) |
| 의존 | core (라이브러리), Kafka |
| 호출하는 서비스 | api (REST, 사용자/예약 정보) |

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
    │   ├── PaymentRefundService.java
    │   └── MockPaymentGateway.java       (실제 PG 연동 시 교체 지점)
    ├── domain/
    │   ├── Payment.java
    │   └── PaymentRepository.java
    ├── event/
    │   ├── ReservationEventConsumer.java
    │   └── PaymentEventPublisher.java
    ├── error/
    │   └── PaymentErrorCode.java
    ├── dto/
    │   └── PaymentResponse.java
    └── config/
        └── KafkaConfig.java
```

---

## 핵심 로직

### ReservationEventConsumer

```java
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final PaymentService paymentService;

    @KafkaListener(topics = "reservation.created", groupId = "payment-group")
    public void onReservationCreated(ReservationCreatedEvent event) {
        paymentService.process(event);
    }
}
```

### PaymentService — Mock 결제

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository repository;
    private final MockPaymentGateway gateway;
    private final ApplicationEventPublisher events;

    @Transactional
    public void process(ReservationCreatedEvent event) {

        // 1) 결제 레코드 생성 (PENDING)
        Payment payment = Payment.create(
            event.reservationId(),
            event.userId(),
            event.amount());
        repository.save(payment);

        // 2) Mock PG 호출
        try {
            gateway.charge(payment);
            payment.complete();
            events.publishEvent(new PaymentCompletedDomainEvent(payment));
        } catch (PaymentDeclinedException e) {
            payment.fail(e.getMessage());
            events.publishEvent(new PaymentFailedDomainEvent(payment));
        }
        // 3) Kafka publish 는 AFTER_COMMIT 에서
    }
}
```

### MockPaymentGateway

```java
@Component
public class MockPaymentGateway {
    public void charge(Payment payment) {
        // Mock: 항상 성공
        // 실제: 토스페이먼츠/Stripe SDK 호출
    }
}
```

### PaymentEventPublisher (AFTER_COMMIT)

```java
@Component
@RequiredArgsConstructor
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafka;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(PaymentCompletedDomainEvent e) {
        kafka.send("payment.completed", PaymentCompletedKafkaEvent.from(e.payment()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFailed(PaymentFailedDomainEvent e) {
        kafka.send("payment.failed", PaymentFailedKafkaEvent.from(e.payment()));
    }
}
```

### PaymentErrorCode

```java
@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "PAY_001", "결제 처리에 실패했습니다."),
    REFUND_NOT_ALLOWED(HttpStatus.CONFLICT,         "PAY_002", "환불 가능 상태가 아닙니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND,                 "PAY_003", "결제 내역을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

> `PAYMENT_FAILED` 는 422 (Unprocessable Entity) — 비즈니스 결과이므로 5xx 가 아님.

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

---

## 의존성 (build.gradle)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation project(':core')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3'

    runtimeOnly 'com.mysql:mysql-connector-j'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.kafka:spring-kafka-test'
}
```
