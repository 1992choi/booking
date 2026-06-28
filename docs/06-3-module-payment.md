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

---

## Consumer Lag 진단 및 해소

### Lag 이 발생하는 원인

컨슈머가 이벤트를 처리하는 도중 예외를 던지면 오프셋이 커밋되지 않는다. `KafkaConfig` 에 설정한 `DefaultErrorHandler(FixedBackOff(5000, MAX_VALUE))` 는 5 초 간격으로 무한 재시도하므로, 처리 불가능한 이벤트(poison message)가 하나라도 있으면 해당 파티션 전체가 멈추고 lag 이 계속 쌓인다.

### Lag 확인

```bash
docker exec booking-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payment-group \
  --describe
```

| 컬럼            | 의미                                                                    |
|----------------|-------------------------------------------------------------------------|
| `CURRENT-OFFSET` | 컨슈머가 마지막으로 커밋한 오프셋. `-` 이면 아직 한 번도 커밋되지 않음 |
| `LOG-END-OFFSET` | 브로커에 쌓인 마지막 오프셋                                             |
| `LAG`            | 두 값의 차이. `CURRENT-OFFSET` 이 `-` 면 계산 불가라 `-` 로 표시됨     |

### 토픽에 쌓인 이벤트 내용 확인

```bash
docker exec booking-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic reservation.created \
  --from-beginning
```

### 문제 이벤트 건너뛰기 (오프셋 리셋)

컨슈머가 활성 상태이면 리셋이 불가하므로 **payment 서비스를 먼저 중지** 한다.

```bash
docker exec booking-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payment-group \
  --topic reservation.created \
  --reset-offsets \
  --to-offset 1 \
  --execute
```

`--to-offset N` 에 건너뛰고 싶은 이벤트의 다음 오프셋을 지정한다 (오프셋은 0-based).  
실행 후 payment 서비스를 재시작하면 지정한 오프셋부터 consume 을 재개한다.

> 레코드는 브로커에 그대로 남는다. 오프셋만 이동시켜 컨슈머가 읽지 않도록 한다.