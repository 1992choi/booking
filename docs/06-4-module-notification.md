# 06-4. notification 서비스

## 역할

알림 발송 (Mock). `payment.completed` / `reservation.cancelled` 이벤트를 consume 해 사용자에게 알림을 발송하고 발송 이력을 저장한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8083 |
| DB | notification_db |
| 외부 노출 | △ (이력 조회 정도. 발송은 외부 트리거 없음) |
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
    │       ├── NotificationChannel.java     (interface)
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

### PaymentEventConsumer

```java
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "payment.completed", groupId = "notification-group")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        notificationService.sendReservationConfirmed(event);
    }
}
```

### ReservationEventConsumer

```java
@Component
@RequiredArgsConstructor
public class ReservationEventConsumer {

    private final NotificationService notificationService;

    @KafkaListener(topics = "reservation.cancelled", groupId = "notification-group")
    public void onReservationCancelled(ReservationCancelledEvent event) {
        notificationService.sendReservationCancelled(event);
    }
}
```

### NotificationService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationChannel channel;
    private final UserClient userClient;

    @Transactional
    public void sendReservationConfirmed(PaymentCompletedEvent event) {
        UserSnapshot user = userClient.fetch(event.userId());
        send(event.userId(), event.reservationId(), NotificationType.CONFIRMED, user);
    }

    @Transactional
    public void sendReservationCancelled(ReservationCancelledEvent event) {
        UserSnapshot user = userClient.fetch(event.userId());
        send(event.userId(), event.reservationId(), NotificationType.CANCELLED, user);
    }

    private void send(Long userId, Long reservationId, NotificationType type, UserSnapshot user) {
        Notification record = Notification.create(userId, reservationId, type, channel.name());
        try {
            channel.send(user, type);
            record.markSent();
        } catch (Exception e) {
            log.warn("Notification send failed", e);
            record.markFailed();
        }
        repository.save(record);
    }
}
```

### NotificationChannel (확장 포인트)

```java
public interface NotificationChannel {
    ChannelName name();
    void send(UserSnapshot user, NotificationType type);
}

@Component
@Slf4j
public class LogChannel implements NotificationChannel {

    @Override public ChannelName name() { return ChannelName.LOG; }

    @Override
    public void send(UserSnapshot user, NotificationType type) {
        log.info("[Mock 알림] userId={}, email={}, type={}", user.id(), user.email(), type);
    }
}
```

> 이메일/SMS/카카오 알림톡은 `NotificationChannel` 구현체를 추가하면 됨.

### NotificationErrorCode

```java
@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "NTF_001", "알림 이력을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

---

## Kafka

### consume
| 토픽 | 처리 |
|------|------|
| payment.completed | 예약 확정 알림 발송 |
| reservation.cancelled | 예약 취소 알림 발송 |

---

## 향후 확장

```
현재: LogChannel (로그 출력)
  ↓
1차: EmailChannel (Spring Mail)
  ↓
2차: KakaoChannel (알림톡 API)
  ↓
3차: PushChannel (FCM/APNs)
```

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
