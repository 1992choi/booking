# 03. ERD (서비스별 DB 분리)

MSA 원칙에 따라 각 서비스가 자기 DB 만 소유한다. 다른 서비스 데이터는 REST 호출 또는 Kafka 이벤트 페이로드로 받는다.

```
db_api          ← User, Owner, Resource, AvailableTime
db_reservation  ← Reservation
db_payment      ← Payment
db_notification ← Notification
```

> 다른 서비스 도메인의 식별자(예: `user_id`, `resource_id`)는 단순 BIGINT 컬럼으로 보유한다. **FK 제약은 걸지 않는다** — 물리적으로 다른 DB이기 때문.

---

## BaseEntity (공통 — core 라이브러리)

모든 테이블은 BaseEntity 를 상속받아 created_at, updated_at 을 자동으로 포함한다.

```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

---

## db_api (api 서비스 소유)

### User (예약자 / 일반 사용자)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| name | VARCHAR | 이름 |
| email | VARCHAR | 이메일 (UNIQUE) |
| phone | VARCHAR | 전화번호 |
| password | VARCHAR | bcrypt 해시 |
| role | ENUM | USER / OWNER / ADMIN |
| created_at | DATETIME | |
| updated_at | DATETIME | |

> `role` 은 JWT 클레임에 포함되어 각 서비스로 전파됨.

### Owner (업체/호스트)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| user_id | BIGINT | FK → User (업체 운영자 계정) |
| name | VARCHAR | 업체명 |
| phone | VARCHAR | 전화번호 |
| type | ENUM | PENSION / CLASS / FACILITY / CONSULTING |
| created_at | DATETIME | |
| updated_at | DATETIME | |

> Owner 는 별도 로그인 주체가 아니라 User(role=OWNER) 와 1:1 매핑.

### Resource (예약 대상)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| owner_id | BIGINT | FK → Owner |
| name | VARCHAR | 대상명 |
| description | TEXT | 설명 |
| price | BIGINT | 가격 |
| max_capacity | INT | 최대 수용 인원 |
| created_at | DATETIME | |
| updated_at | DATETIME | |

### AvailableTime (예약 가능 시간대)
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| resource_id | BIGINT | FK → Resource |
| start_time | DATETIME | 시작 시간 |
| end_time | DATETIME | 종료 시간 |
| status | ENUM | OPEN / BLOCKED |
| created_at | DATETIME | |
| updated_at | DATETIME | |

> reservation 서비스가 예약 시 api 서비스의 `/api/internal/resources/{id}/check` REST 로 가용성 검증.
> AvailableTime.status 는 운영자가 임의로 시간대를 막을 때(`BLOCKED`) 사용. 실제 예약 점유는 db_reservation 의 Reservation 으로 판단.

---

## db_reservation (reservation 서비스 소유)

### Reservation
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| user_id | BIGINT | db_api.User.id (FK X) |
| resource_id | BIGINT | db_api.Resource.id (FK X) |
| resource_name | VARCHAR | 예약 시점의 설비명 snapshot |
| start_time | DATETIME | 예약 시작 |
| end_time | DATETIME | 예약 종료 |
| status | ENUM | PENDING / CONFIRMED / CANCELLED |
| head_count | INT | 인원 수 |
| amount | BIGINT | **예약 시점의 가격 snapshot** (불변, 청구 기준값) |
| created_at | DATETIME | |
| updated_at | DATETIME | |

> INDEX: `(resource_id, start_time, end_time)` — 시간 겹침 쿼리 성능
> INDEX: `(user_id)` — 내 예약 조회
>
> **`Reservation.amount` vs `Payment.amount` 역할 구분**
> - `Reservation.amount`: 예약 시점에 api 의 Resource.price 를 복사 (snapshot). 청구 기준이 되는 **약속된 가격**. Resource.price 가 나중에 바뀌어도 영향 없음.
> - `Payment.amount`: 실제 결제 시도된 금액. 보통은 `Reservation.amount` 와 동일하지만, 부분 결제/할인 적용 등으로 달라질 수 있는 **결제 사실값**.

---

## db_payment (payment 서비스 소유)

### Payment
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| reservation_id | BIGINT | db_reservation.Reservation.id (FK X), UNIQUE |
| user_id | BIGINT | db_api.User.id (FK X) |
| amount | BIGINT | **실제 결제 시도/완료된 금액** (Reservation.amount 와 같지 않을 수 있음 — 할인/부분 결제 등) |
| status | ENUM | PENDING / COMPLETED / FAILED / REFUNDED |
| paid_at | DATETIME | 결제 시각 |
| failed_reason | VARCHAR | 실패 사유 (nullable) |
| created_at | DATETIME | |
| updated_at | DATETIME | |

---

## db_notification (notification 서비스 소유)

### Notification
| 컬럼 | 타입 | 설명 |
|------|------|------|
| id | BIGINT | PK |
| user_id | BIGINT | db_api.User.id (FK X) |
| reservation_id | BIGINT | db_reservation.Reservation.id (FK X) |
| type | ENUM | CONFIRMED / CANCELLED |
| channel | ENUM | EMAIL / SMS / KAKAO / LOG |
| status | ENUM | SENT / FAILED |
| sent_at | DATETIME | |
| created_at | DATETIME | |
| updated_at | DATETIME | |

---

## 도메인 간 관계 (논리적)

물리적 FK 가 아닌 논리적 참조 관계.

```
User (api)
  └─ 1:N → Reservation (reservation)
  └─ 1:N → Payment (payment)
  └─ 1:N → Notification (notification)
  └─ 1:1 → Owner (api)

Owner (api)
  └─ 1:N → Resource (api)

Resource (api)
  └─ 1:N → AvailableTime (api)
  └─ 1:N → Reservation (reservation)

Reservation (reservation)
  └─ 1:1 → Payment (payment)
  └─ 1:N → Notification (notification)
```

---

## 상태 전이

### Reservation Status
```
PENDING → CONFIRMED   (payment.completed 이벤트 수신 시)
PENDING → CANCELLED   (payment.failed 이벤트 수신 시)
CONFIRMED → CANCELLED (사용자/관리자 취소 요청 시)
```

### Payment Status
```
PENDING → COMPLETED  (Mock 결제 성공)
PENDING → FAILED     (Mock 결제 실패)
COMPLETED → REFUNDED (환불)
```
