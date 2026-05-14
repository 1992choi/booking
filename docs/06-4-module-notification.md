# 06-4. notification 서비스

## 역할

알림 발송 (Mock). `payment.completed` / `reservation.cancelled` 이벤트를 consume 해 사용자에게 알림을 발송하고 발송 이력을 저장한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8083 |
| DB | notification_db |
| 외부 노출 | △ (이력 조회만. 발송은 Kafka 트리거) |
| 의존 | core (라이브러리), Kafka |
| 호출하는 서비스 | api (REST, 사용자 정보) |

---

## 책임 도메인

notification 서비스가 자체 DB 에 소유:
- Notification

---

## 패키지 구조

```
notification/
└── src/main/java/com/example/booking/notification/
    ├── NotificationApplication.java
    ├── service/
    │   ├── NotificationService.java
    │   └── channel/
    │       ├── NotificationChannel.java     (interface — 확장 포인트)
    │       └── LogChannel.java              (Mock)
    ├── domain/
    │   ├── Notification.java
    │   └── NotificationRepository.java
    ├── client/
    │   └── UserClient.java                  (api 서비스 REST)
    ├── event/
    │   ├── PaymentEventConsumer.java
    │   └── ReservationEventConsumer.java
    ├── error/
    │   └── NotificationErrorCode.java
    └── config/
        └── KafkaConfig.java
```

---

## 핵심 로직

이벤트 consume → `UserClient` 로 api 서비스에서 사용자 정보 조회 → `NotificationChannel.send()` 호출 → Notification 이력 저장 (SENT / FAILED).

`NotificationChannel` 은 인터페이스로 분리돼 있어, 이메일·SMS·카카오 알림톡 등 채널 추가 시 구현체만 추가하면 된다. 현재는 `LogChannel` (로그 출력) 만 구현돼 있다.

알림 타입은 `CONFIRMED` / `CANCELLED` 두 가지만 존재한다.

---

## Kafka

### consume
| 토픽 | 처리 |
|------|------|
| payment.completed | 예약 확정 알림 발송 |
| reservation.cancelled | 예약 취소 알림 발송 |