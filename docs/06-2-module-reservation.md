# 06-2. reservation 서비스

## 역할

예약 도메인의 핵심 비즈니스 로직 + 동시성 처리. 예약 생성/조회/취소를 담당하며 Redis 분산 락 + DB 비관적 락으로 중복 예약을 방지한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8081 |
| DB | reservation_db |
| 외부 노출 | O (path: `/reservations/**`) |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | api (REST, resource 검증) |

---

## 책임 도메인

reservation 서비스가 자체 DB 에 소유:
- Reservation

---

## 패키지 구조

```
reservation/
└── src/main/java/com/example/booking/reservation/
    ├── ReservationApplication.java
    ├── controller/
    │   └── ReservationController.java
    ├── service/
    │   └── ReservationService.java
    ├── domain/
    │   ├── Reservation.java
    │   ├── ReservationRepository.java
    │   └── ReservationStatus.java
    ├── client/
    │   ├── ResourceClient.java          (api 서비스 REST 호출)
    │   └── ResourceSnapshot.java
    ├── error/
    │   └── ReservationErrorCode.java
    ├── dto/
    │   ├── CreateReservationRequest.java
    │   ├── ReservationResponse.java
    │   └── PageResponse.java
    └── config/
        ├── RestClientConfig.java
        └── SecurityConfig.java
```

> Redis 분산락, Kafka 연동은 개선 이슈로 미구현 (backlog).

---

## 핵심 로직

### ReservationService — 예약 생성

```java
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ResourceClient resourceClient;

    @Transactional
    public ReservationResponse create(Long userId, CreateReservationRequest request) {
        // 1) api 서비스에 resource 검증 (가격/정원)
        ResourceSnapshot resource = resourceClient.fetch(request.resourceId());

        if (request.headCount() > resource.maxCapacity()) {
            throw new BusinessException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }

        // 2) 시간 겹침 체크
        boolean hasOverlap = !reservationRepository.findOverlapping(
                request.resourceId(), request.startTime(), request.endTime()).isEmpty();
        if (hasOverlap) {
            throw new BusinessException(ReservationErrorCode.CONFLICT);
        }

        // 3) 예약 생성 (price, resourceName 은 snapshot)
        Reservation reservation = Reservation.builder()
                .userId(userId)
                .resourceId(request.resourceId())
                .resourceName(resource.name())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .status(ReservationStatus.PENDING)
                .headCount(request.headCount())
                .amount(resource.price())
                .build();

        reservationRepository.save(reservation);
        return ReservationResponse.from(reservation);
    }
}
```

> Redis 분산락 + DB 비관적 락은 개선 이슈 (backlog).

### ReservationRepository — 시간 겹침 쿼리

```java
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Query("""
        SELECT r FROM Reservation r
        WHERE r.resourceId = :resourceId
          AND r.status <> com.example.booking.reservation.domain.ReservationStatus.CANCELLED
          AND r.startTime < :end
          AND r.endTime > :start
    """)
    List<Reservation> findOverlapping(
        @Param("resourceId") Long resourceId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    Page<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status, Pageable pageable);
}
```

### ReservationErrorCode

```java
@Getter
@RequiredArgsConstructor
public enum ReservationErrorCode implements ErrorCode {
    CONFLICT(HttpStatus.CONFLICT,                 "RSV_001", "이미 예약된 시간대입니다."),
    LOCK_FAILED(HttpStatus.CONFLICT,              "RSV_002", "잠시 후 다시 시도해주세요."),
    CAPACITY_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "RSV_003", "최대 수용 인원을 초과했습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND,               "RSV_004", "예약을 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN,               "RSV_005", "본인 예약만 취소할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

---

## Kafka (미구현 — 개선 이슈)

| 방향 | 토픽 | 시점 |
|------|------|------|
| produce | reservation.created | 예약 생성 완료 후 AFTER_COMMIT |
| produce | reservation.cancelled | 예약 취소 후 AFTER_COMMIT |
| consume | payment.failed | 예약 상태 → CANCELLED |

> Kafka 연동 완료 시 `@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴 사용. 현재는 미연결.

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
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3'

    runtimeOnly 'com.mysql:mysql-connector-j'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}

// 개선 이슈 도입 시 추가 예정:
// implementation 'org.redisson:redisson-spring-boot-starter'
// implementation 'org.springframework.kafka:spring-kafka'
```
