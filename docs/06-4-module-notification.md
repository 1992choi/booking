# 06-4. notification 서비스

## 역할

알림 발송 (Mock). `payment.completed` / `reservation.cancelled` 이벤트를 consume 하거나 api 서비스로부터 HTTP 요청을 받아 사용자에게 알림을 발송하고 발송 이력을 저장한다. 발송과 동시에 SSE로 연결된 클라이언트에도 실시간 push 한다.

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
    │   ├── ReservationEventConsumer.java
    │   └── NotificationCreatedDomainEvent.java  (알림 저장 후 발행 — SSE push 트리거)
    ├── sse/
    │   └── SseNotificationRegistry.java     (userId ↔ SseEmitter 매핑, AFTER_COMMIT에 push)
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

`NotificationService.send()`/`sendAdminMessage()`는 `Notification` 저장 직후 `NotificationCreatedDomainEvent`를 발행한다. `SseNotificationRegistry`가 `@TransactionalEventListener(AFTER_COMMIT)`로 이를 구독해, 저장 트랜잭션이 실제로 커밋된 뒤에만 SSE로 push한다(Kafka 발행과 동일하게 "커밋 후 발행" 규칙을 따름 — 롤백된 알림이 클라이언트에 먼저 보이는 걸 방지).

`PATCH /api/v1/notifications/{id}/read` 로 알림을 읽음 처리하면 `read_at`이 채워진다. 본인 소유가 아닌 알림이면 403, 존재하지 않으면 404.

---

## HTTP (Internal)

| Endpoint | 호출 서비스 | 처리 |
|----------|-------------|------|
| `POST /api/v1/internal/messages` | api | 관리자 메시지 발송 → `ADMIN_MESSAGE` 이력 저장 |

---

## 실시간 알림 (SSE)

`GET /api/v1/notifications/stream?token={accessToken}` — 연결을 유지하며(`SseEmitter`, 타임아웃 30분) 해당 유저에게 온 알림을 실시간 push. 상세 요청/응답 형식은 `04-api-spec.md` 참고.

- `SseNotificationRegistry`가 `Map<userId, List<SseEmitter>>`로 연결을 관리 — 같은 유저의 여러 탭/기기가 동시에 연결하면 전부에게 push
- 브라우저 `EventSource`가 커스텀 헤더를 못 보내서 이 엔드포인트만 JWT를 쿼리 파라미터로 받는다 — `SecurityConfig`에서 `permitAll()` 처리하고, 인증은 컨트롤러 안에서 `JwtVerifier.verify(token)`으로 직접 수행
- 연결이 끊기거나(`onError`/`onTimeout`) 정상 종료(`onCompletion`)되면 등록된 emitter를 목록에서 제거
- 서버가 알림을 유실 없이 재전송해주는 기능(이벤트 리플레이)은 없음 — 연결돼 있는 동안만 수신, 과거 이력은 `GET /api/v1/notifications/me`로 별도 조회

---

## 접근 제어 (SecurityConfig)

```
/ping                        → permitAll
/actuator/**                 → permitAll
/api/v1/internal/**          → permitAll (게이트웨이/보안그룹 레벨 차단 전제)
/api/v1/notifications/stream → permitAll (컨트롤러 내부에서 JwtVerifier로 직접 인증)
그 외                         → authenticated
```

## Kafka

### consume
| 토픽 | 처리 |
|------|------|
| payment.completed | 예약 확정 알림 발송 |
| reservation.cancelled | 예약 취소 알림 발송 |