# 06-5. review 모듈

## 역할

업체(Merchant) 리뷰 CRUD. 학습 목적으로 Kotlin + Spring Boot 로 작성된 독립 배포 모듈.

| 항목 | 값 |
|------|-----|
| 포트 | 8084 |
| 언어 | Kotlin |
| DB | db_reservation (reservation 서비스와 공유 — 새 DB 미프로비저닝) |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | 없음 (REST 호출도, Kafka 구독도 없음) |

---

## 책임 도메인

review 모듈이 쓰기 권한을 갖는 테이블은 신설 `reviews` 하나뿐이다. `Merchant`/`Resource`/`Reservation`은 reservation 서비스가 소유하며, review 모듈은 이들을 **읽기 전용**으로만 직접 조회한다 (서비스 간 REST 호출 없이 `db_reservation`을 공유 DB로 바로 연결).

---

## 패키지 구조

```
review/
└── src/main/kotlin/com/example/booking/review/
    ├── ReviewApplication.kt
    ├── controller/
    │   └── ReviewController.kt
    ├── service/
    │   └── ReviewService.kt
    ├── domain/
    │   ├── Review.kt
    │   └── ReviewRepository.kt
    ├── reservation/                        (읽기 전용 — db_reservation 직접 조회)
    │   ├── ReservationView.kt              (reservations 테이블, id/userId/resourceId/status만 매핑)
    │   ├── ReservationViewRepository.kt    (findById만 노출 — save/delete 없음)
    │   ├── ResourceView.kt                 (resources 테이블, id/merchantId만 매핑)
    │   └── ResourceViewRepository.kt
    ├── dto/
    │   ├── ReviewCreateRequest.kt
    │   ├── ReviewUpdateRequest.kt
    │   └── ReviewResponse.kt
    ├── error/
    │   └── ReviewErrorCode.kt
    └── config/
        └── SecurityConfig.kt
```

---

## 핵심 로직

### 리뷰 작성 흐름

1. `ReservationViewRepository`로 `reservationId` 조회 → 없으면 404 (`CommonErrorCode.NOT_FOUND`)
2. `reservation.userId != 요청자` → 403 (`CommonErrorCode.FORBIDDEN`)
3. `reservation.status != CONFIRMED` → 422 (`REVIEW_004`)
4. 이미 해당 `reservationId`로 작성된 리뷰가 있으면 → 409 (`REVIEW_003`)
5. `ResourceViewRepository`로 `reservation.resourceId` → `merchantId` 조회
6. `Review` INSERT (`reservationId`, `merchantId` snapshot, `userId`, `content`)

reservation 자체가 없거나 본인 예약이 아닌 경우는 review 전용 에러 코드를 늘리지 않고 `CommonErrorCode`를 재사용한다 (reservation 서비스의 `MerchantService`가 소유권 불일치에 `CommonErrorCode.FORBIDDEN`을 쓰는 것과 동일한 관례).

### 수정/삭제

작성자 본인이 아니면 403 (`REVIEW_002`), 리뷰가 없으면 404 (`REVIEW_001`).

### 예약 취소와의 관계

`Reservation`이 이후 `CANCELLED` 되어도 리뷰는 그대로 유지된다. review 모듈은 Kafka를 구독하지 않으므로 별도 동기화 처리가 없다.

---

## 읽기 전용 엔티티 (db_reservation 공유)

`ReservationView`/`ResourceView`는 Hibernate `@Immutable`로 표시하고, 리포지토리는 Spring Data `Repository<T, ID>` 마커 인터페이스에 `findById`만 선언해 `save`/`delete` 자체를 노출하지 않는다. reservation 서비스가 소유한 테이블에 review 모듈이 실수로도 쓰기 작업을 할 수 없도록 하는 방어 장치다.

---

## 접근 제어 (SecurityConfig)

```
GET /api/v1/reviews    → permitAll
그 외                  → authenticated (JWT, 작성자 본인 여부는 서비스 레이어에서 검증)
```

---

## ReviewErrorCode

| code | HTTP | 설명 |
|------|------|------|
| REVIEW_001 | 404 | 리뷰 없음 |
| REVIEW_002 | 403 | 본인 리뷰 아님 |
| REVIEW_003 | 409 | 이미 리뷰 작성됨 |
| REVIEW_004 | 422 | 예약이 CONFIRMED 상태 아님 |

---

## Kotlin ↔ core(Java) 연동

- `core`는 순수 Java 라이브러리(`bootJar { enabled = false }`, `jar { enabled = true }`)라 Kotlin에서 `implementation project(':core')`로 그대로 소비 가능.
- `kotlin("plugin.spring")` — `@Component`/`@Service`/`@Transactional` 등 core·Spring 어노테이션이 붙은 클래스를 자동 open 처리 (Kotlin 클래스는 기본 `final`이라 CGLIB 프록시가 불가능하므로 필요).
- `kotlin("plugin.jpa")` — `@Entity` 클래스 자동 open + no-arg 생성자 보강.
- `BaseEntity`는 Java `abstract class`라 Kotlin에서 별도 처리 없이 상속 가능.
- Bean Validation 어노테이션은 Kotlin data class 생성자 파라미터에 `@field:` 유즈사이트 타깃을 붙여야 인식된다 (`@field:NotBlank` 등).
- **JVM target**: 루트 `build.gradle`의 `sourceCompatibility = '25'`는 Java 25 라이브러리로 컴파일되는 `core`에도 적용된다. Gradle의 변형(variant) 호환성 검사상 Kotlin 컴파일 타깃이 이보다 낮으면 `project :core` 의존성 해석이 실패하므로, review의 `compilerOptions.jvmTarget`도 `JVM_25`로 맞춘다 (Kotlin 2.1.x는 JVM_23까지만 지원하여 2.4.0으로 상향).
