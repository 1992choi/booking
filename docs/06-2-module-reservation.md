# 06-2. reservation 서비스

## 역할

예약 도메인의 핵심 비즈니스 로직 + 동시성 처리. 예약 생성/조회/취소를 담당하며 Redis 분산 락 + DB 비관적 락으로 중복 예약을 방지한다.

| 항목 | 값 |
|------|-----|
| 포트 | 8081 |
| DB | reservation_db |
| 외부 노출 | O (path: `/reservations/**`) |
| 의존 | core (라이브러리), Redis, Kafka |
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
    │   ├── ReservationService.java
    │   ├── ReservationValidator.java
    │   └── ReservationCancelService.java
    ├── domain/
    │   ├── Reservation.java
    │   └── ReservationRepository.java
    ├── client/
    │   └── ResourceClient.java          (api 서비스 REST 호출)
    ├── event/
    │   ├── ReservationEventPublisher.java
    │   └── PaymentEventConsumer.java    (payment.failed 처리)
    ├── error/
    │   └── ReservationErrorCode.java
    ├── dto/
    │   ├── CreateReservationRequest.java
    │   └── ReservationResponse.java
    ├── internal/
    │   └── controller/InternalController.java
    └── config/
        ├── RedissonConfig.java
        └── KafkaConfig.java
```

---

## 핵심 로직

### ReservationService — 예약 생성

```java
@Service
@RequiredArgsConstructor
public class ReservationService {

    private final RedissonClient redisson;
    private final ReservationRepository repository;
    private final ReservationValidator validator;
    private final ResourceClient resourceClient;
    private final ApplicationEventPublisher events;

    @Transactional
    public ReservationResponse create(Long userId, CreateReservationRequest req) {

        // 1) api 서비스에 resource 검증 (가격/정원/기간)
        ResourceSnapshot resource = resourceClient.fetch(req.resourceId());
        validator.validateCapacity(resource, req.headCount());

        // 2) Redis 분산 락
        String lockKey = "reservation:lock:" + req.resourceId();
        RLock lock = redisson.getLock(lockKey);
        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                throw new BusinessException(ReservationErrorCode.LOCK_FAILED);
            }

            // 3) 시간 겹침 체크 (DB 비관적 락)
            validator.validateNoOverlap(req.resourceId(), req.startTime(), req.endTime());

            // 4) 예약 생성 (price 는 snapshot)
            Reservation reservation = Reservation.create(userId, req, resource.price());
            repository.save(reservation);

            // 5) 트랜잭션 커밋 후 Kafka publish (Outbox or AFTER_COMMIT 이벤트)
            events.publishEvent(new ReservationCreatedDomainEvent(reservation));

            return ReservationResponse.from(reservation, resource.name());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ReservationErrorCode.LOCK_FAILED);
        } finally {
            if (lock.isHeldByCurrentThread()) lock.unlock();
        }
    }
}
```

### ReservationValidator

```java
@Component
@RequiredArgsConstructor
public class ReservationValidator {

    private final ReservationRepository repository;

    public void validateCapacity(ResourceSnapshot resource, int headCount) {
        if (headCount > resource.maxCapacity()) {
            throw new BusinessException(ReservationErrorCode.CAPACITY_EXCEEDED);
        }
    }

    public void validateNoOverlap(Long resourceId, LocalDateTime start, LocalDateTime end) {
        List<Reservation> overlapping = repository.findOverlappingWithLock(resourceId, start, end);
        if (!overlapping.isEmpty()) {
            throw new BusinessException(ReservationErrorCode.CONFLICT);
        }
    }
}
```

### ReservationRepository — 시간 겹침 쿼리

```java
public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM Reservation r
        WHERE r.resourceId = :resourceId
          AND r.status <> com.example.booking.reservation.domain.ReservationStatus.CANCELLED
          AND r.startTime < :end
          AND r.endTime > :start
    """)
    List<Reservation> findOverlappingWithLock(
        @Param("resourceId") Long resourceId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);

    Page<Reservation> findByUserId(Long userId, Pageable pageable);
}
```

### ReservationEventPublisher — Kafka (AFTER_COMMIT)

```java
@Component
@RequiredArgsConstructor
public class ReservationEventPublisher {

    private final KafkaTemplate<String, Object> kafka;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCreated(ReservationCreatedDomainEvent e) {
        kafka.send("reservation.created", ReservationCreatedKafkaEvent.from(e.reservation()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(ReservationCancelledDomainEvent e) {
        kafka.send("reservation.cancelled", ReservationCancelledKafkaEvent.from(e.reservation()));
    }
}
```

> 트랜잭션 커밋 후 발행하므로 DB 롤백 시 유령 이벤트 차단. 더 강한 보장이 필요하면 Outbox 패턴 도입.

### PaymentEventConsumer — payment.failed 시 예약 취소

```java
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final ReservationCancelService cancelService;

    @KafkaListener(topics = "payment.failed", groupId = "reservation-group")
    public void onPaymentFailed(PaymentFailedEvent event) {
        cancelService.cancelByPaymentFailure(event.reservationId(), event.reason());
    }
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

### InternalController — 서비스 간 호출 endpoint

api 서비스의 admin 기능이 위임 호출하는 진입점. 외부 노출은 Gateway 단에서 차단된다.

```java
@RestController
@RequestMapping("/api/v1/internal/reservations")
@RequiredArgsConstructor
public class InternalController {

    private final ReservationQueryService queryService;
    private final ReservationAdminService adminService;

    // admin → reservation 위임: 전체 예약 조회 (필터/페이징)
    @GetMapping
    public PageResponse<ReservationSummary> search(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) Long resourceId,
            @PageableDefault(size = 20) Pageable pageable) {
        return queryService.search(date, status, resourceId, pageable);
    }

    // admin → reservation 위임: 캘린더 뷰
    @GetMapping("/calendar")
    public Map<LocalDate, List<ReservationCalendarItem>> calendar(
            @RequestParam int year,
            @RequestParam int month) {
        return queryService.calendar(year, month);
    }

    // admin → reservation 위임: 단건 상세
    @GetMapping("/{id}")
    public ReservationDetail getOne(@PathVariable Long id) {
        return queryService.getDetail(id);
    }

    // admin → reservation 위임: 수동 확정
    @PutMapping("/{id}/confirm")
    public ReservationResponse confirm(@PathVariable Long id) {
        return adminService.forceConfirm(id);
    }

    // admin → reservation 위임: 수동 취소
    @PutMapping("/{id}/cancel")
    public ReservationResponse cancel(@PathVariable Long id, @RequestBody CancelRequest req) {
        return adminService.forceCancel(id, req.reason());
    }
}
```

> SecurityConfig 에서 `/api/v1/internal/**` 은 내부 호출자(보안 그룹 또는 mTLS) 만 통과하도록 제한.

---

## Kafka

### produce
| 토픽 | 시점 | phase |
|------|------|-------|
| reservation.created | 예약 생성 완료 | AFTER_COMMIT |
| reservation.cancelled | 예약 취소 | AFTER_COMMIT |

### consume
| 토픽 | 처리 |
|------|------|
| payment.failed | 예약 상태 → CANCELLED |

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
    implementation 'org.redisson:redisson-spring-boot-starter'   // 도입 시점 최신
    implementation 'org.springframework.kafka:spring-kafka'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3'

    runtimeOnly 'com.mysql:mysql-connector-j'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.testcontainers:mysql'
    testImplementation 'org.testcontainers:kafka'
}
```
