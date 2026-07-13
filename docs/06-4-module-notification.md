# 06-4. notification 서비스

## 역할

알림 발송 (Mock). `payment.completed` / `reservation.cancelled` 이벤트를 consume 하거나 api 서비스로부터 HTTP 요청을 받아 사용자에게 알림을 발송하고 발송 이력을 저장한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8083 |
| DB | db_notification |
| 외부 노출 | △ (이력 조회만) |
| 의존 | core (라이브러리), Kafka |
| 호출받는 서비스 | api (HTTP — 관리자 메시지 발송) |

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
    ├── controller/
    │   └── NotificationController.java
    ├── internal/
    │   ├── InternalNotificationController.java (api 서비스 전용 — 관리자 메시지)
    │   └── dto/AdminMessageRequest.java
    ├── service/
    │   ├── NotificationService.java
    │   └── channel/
    │       ├── NotificationSender.java      (interface — 확장 포인트)
    │       └── LogNotificationSender.java   (Mock)
    ├── domain/
    │   ├── Notification.java
    │   ├── NotificationChannel.java         (enum: EMAIL/SMS/KAKAO/LOG)
    │   ├── NotificationType.java            (enum: CONFIRMED/CANCELLED/ADMIN_MESSAGE)
    │   └── NotificationRepository.java
    ├── event/
    │   ├── PaymentEventConsumer.java
    │   └── ReservationEventConsumer.java
    ├── user/
    │   ├── domain/UserSync.java
    │   └── event/UserEventConsumer.java     (user.created/updated/deleted 구독)
    ├── system/
    │   └── PingController.java
    └── config/
        ├── SecurityConfig.java
        └── KafkaConfig.java
```

---

## 핵심 로직

이벤트 consume → 로컬 `UserSyncRepository` 로 사용자 정보 조회 → `NotificationSender.send()` 호출 → Notification 이력 저장 (SENT / FAILED). 유저 동기화 정보가 없으면 발송 스킵 후 FAILED 기록.

`NotificationSender` 는 인터페이스로 분리돼 있어, 이메일·SMS·카카오 알림톡 등 채널 추가 시 구현체만 추가하면 된다. 현재는 `LogNotificationSender` (로그 출력) 만 구현돼 있다.

알림 타입은 `CONFIRMED` / `CANCELLED` / `ADMIN_MESSAGE` 세 가지다. `ADMIN_MESSAGE`는 `reservation_id` 없이 저장된다.

---

## HTTP (Internal)

| Endpoint | 호출 서비스 | 처리 |
|----------|-------------|------|
| `POST /api/v1/internal/messages` | api | 관리자 메시지 발송 → `ADMIN_MESSAGE` 이력 저장 |

---

## 접근 제어 (SecurityConfig)

```
/ping                    → permitAll
/api/v1/internal/**      → permitAll (게이트웨이/보안그룹 레벨 차단 전제)
그 외                     → authenticated
```

## Kafka

### consume
| 토픽 | 처리 |
|------|------|
| payment.completed | 예약 확정 알림 발송 |
| reservation.cancelled | 예약 취소 알림 발송 |