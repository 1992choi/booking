# 04. API 명세

각 endpoint 가 어느 서비스에 속하는지 표기한다. 클라이언트는 ALB/Gateway 를 거쳐 path 기반으로 라우팅된 서비스에 도달한다.

| Path 패턴 | 라우팅 대상 서비스 |
|-----------|---------------------|
| `/api/v1/auth/**` | api |
| `/api/v1/users/**`, `/api/v1/merchants/**`, `/api/v1/resources/**` | api |
| `/api/v1/merchants/{merchantId}/reservations` | api → (REST) reservation |
| `/api/v1/reservations/**` | reservation |
| `/api/v1/payments/**` | payment |
| `/api/v1/admin/reservations/{id}/confirm`, `/api/v1/admin/reservations/{id}/cancel` | api → (REST) reservation |

---

## 공통

### Base URL
```
http://localhost/api/v1   (로컬: ALB 없이 각 포트 직접)
```

### 응답 정책

- **성공**: 적절한 2xx + DTO 직접 반환 (envelope 없음)
  ```json
  { "userId": 1, "email": "hong@example.com", "name": "홍길동" }
  ```
- **실패**: 적절한 4xx/5xx + RFC 9457 `application/problem+json`
  ```json
  {
    "type": "about:blank",
    "title": "Conflict",
    "status": 409,
    "detail": "이미 예약된 시간대입니다.",
    "code": "RSV_001",
    "instance": "/api/v1/reservations"
  }
  ```

### 에러 코드

| code | HTTP | 설명 | 정의 위치 |
|------|------|------|------------|
| AUTH_001 | 401 | 인증 실패 | core (CommonErrorCode) |
| AUTH_002 | 403 | 권한 없음 | core |
| COMMON_001 | 404 | 리소스 없음 | core |
| COMMON_400 | 400 | 잘못된 요청 | core |
| COMMON_500 | 500 | 서버 오류 | core |
| API_001 | 409 | 이메일 중복 | api |
| API_002 | 401 | 이메일/비밀번호 불일치 | api |
| API_003 | 404 | 업체 없음 | api |
| API_004 | 404 | 설비 없음 | api |
| API_005 | 404 | 가능 시간 없음 | api |
| RSV_001 | 409 | 시간대 중복 | reservation |
| RSV_002 | 409 | 동시 요청 락 실패 | reservation |
| RSV_003 | 422 | 인원 초과 (max_capacity) | reservation |
| RSV_004 | 404 | 예약 없음 | reservation |
| RSV_005 | 403 | 본인 예약 아님 | reservation |
| PAY_001 | 422 | 결제 실패 (비즈니스 결과) | payment |
| PAY_002 | 409 | 환불 불가 상태 | payment |
| PAY_003 | 404 | 결제 내역 없음 | payment |
| NTF_001 | 404 | 알림 이력 없음 | notification |

> 결제 실패는 5xx(서버 오류) 가 아니라 422(처리 가능하나 비즈니스 룰로 거절). 클라이언트가 인지하고 재시도/안내해야 함.

---

## 인증 API (api 서비스)

### 회원가입
```
POST /api/v1/auth/signup

Request:
{
  "name": "홍길동",
  "email": "hong@example.com",
  "password": "password123",
  "phone": "010-1234-5678"
}

Response 201:
{
  "userId": 1,
  "email": "hong@example.com",
  "name": "홍길동"
}
```

### 로그인
```
POST /api/v1/auth/login

Request:
{
  "email": "hong@example.com",
  "password": "password123"
}

Response 200:
{
  "accessToken": "eyJhbGci...",
  "refreshToken": "eyJhbGci...",
  "expiresIn": 3600
}
```

> refreshToken은 Stateless JWT (type=refresh claim). 서버에 저장하지 않음.

### 토큰 갱신
```
POST /api/v1/auth/refresh

Request:
{
  "refreshToken": "eyJhbGci..."
}

Response 200:
{
  "accessToken": "eyJhbGci...",
  "expiresIn": 3600
}

Error 401 (refresh token이 유효하지 않거나 access token을 사용한 경우):
{
  "status": 401,
  "code": "AUTH_001"
}
```

---

## Merchant API (api 서비스)

### 업체 등록
```
POST /api/v1/merchants
Authorization: Bearer {jwt}

Request:
{
  "name": "한옥 펜션",
  "phone": "010-1234-5678",
  "type": "PENSION"
}

Response 201:
{
  "id": 1,
  "userId": 10,
  "name": "한옥 펜션",
  "phone": "010-1234-5678",
  "type": "PENSION",
  "createdAt": "2026-05-01T10:00:00"
}
```

> 한 User 가 여러 업체를 등록할 수 있음 (1:N). 중복 등록 제한 없음.

### 내 업체 목록 조회
```
GET /api/v1/merchants/me
Authorization: Bearer {jwt}

Response 200:
[
  {
    "id": 1,
    "userId": 10,
    "name": "한옥 펜션",
    "phone": "010-1234-5678",
    "type": "PENSION",
    "createdAt": "2026-05-01T10:00:00"
  }
]
```

### 전체 업체 목록 조회
```
GET /api/v1/merchants

Response 200:
[
  {
    "id": 1,
    "name": "한옥 펜션",
    "type": "PENSION"
  }
]
```

### 업체 상세 조회
```
GET /api/v1/merchants/{merchantId}

Response 200:
{
  "id": 1,
  "name": "한옥 펜션",
  "type": "PENSION",
  "resources": []
}
```

### 업체 수정
```
PUT /api/v1/merchants/{merchantId}
Authorization: Bearer {jwt}

Request:
{
  "name": "한옥 펜션 (리뉴얼)",
  "phone": "010-9999-8888",
  "type": "PENSION"
}

Response 200:
{
  "id": 1,
  "name": "한옥 펜션 (리뉴얼)",
  "type": "PENSION"
}

Error 403: 본인 소유가 아닌 업체 수정 시도
Error 404 (API_003): 업체 없음
```

### 업체별 예약 목록 조회
```
GET /api/v1/merchants/{merchantId}/reservations?status=CONFIRMED&page=0&size=10
Authorization: Bearer {jwt}  (해당 업체 소유자만 가능)

Response 200:
{
  "content": [
    {
      "id": 1,
      "status": "CONFIRMED",
      "resourceName": "별채 A",
      "startTime": "2026-05-01T14:00:00",
      "endTime": "2026-05-01T15:00:00",
      "headCount": 2,
      "amount": 150000
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1
}

Error 403 (AUTH_002): 본인 소유 업체가 아닌 경우
Error 404 (API_003): 업체 없음
```

> api 서비스가 해당 업체의 resourceId 목록을 조회한 뒤, reservation 서비스의 내부 엔드포인트 `GET /api/v1/internal/reservations/by-merchant` 에 위임한다. 리소스가 없으면 reservation 서비스 호출 없이 빈 페이지를 반환한다.

---

## Resource API (api 서비스)

### 예약 대상 등록
```
POST /api/v1/merchants/{merchantId}/resources

Request:
{
  "name": "별채 A",
  "description": "2인실 독채",
  "price": 150000,
  "maxCapacity": 2
}

Response 201:
{
  "resourceId": 1,
  "name": "별채 A",
  "price": 150000
}
```

### 가능 시간 등록
```
POST /api/v1/resources/{resourceId}/available-times

Request:
{
  "startTime": "2026-05-01T14:00:00",
  "endTime": "2026-05-01T15:00:00"
}

Response 201:
{
  "availableTimeId": 1,
  "startTime": "2026-05-01T14:00:00",
  "endTime": "2026-05-01T15:00:00",
  "status": "OPEN"
}
```

### 예약 대상 수정
```
PUT /api/v1/resources/{resourceId}
Authorization: Bearer {jwt}  (업체 소유자(MERCHANT 역할)만 가능 — 본인 소유 resource)

Request:
{
  "name": "별채 B",
  "description": "리모델링 완료",
  "price": 200000,
  "maxCapacity": 4
}

Response 200:
{
  "resourceId": 1,
  "name": "별채 B",
  "price": 200000
}

Error 403: 본인 소유가 아닌 resource 수정 시도
```

### 예약 대상 삭제
```
DELETE /api/v1/resources/{resourceId}
Authorization: Bearer {jwt}  (업체 소유자(MERCHANT 역할)만 가능 — 본인 소유 resource)

Response 204

Error 403: 본인 소유가 아닌 resource 삭제 시도
```

### 가능 시간 조회
```
GET /api/v1/resources/{resourceId}/available-times?date=2026-05-01

Response 200:
[
  {
    "availableTimeId": 1,
    "startTime": "2026-05-01T14:00:00",
    "endTime": "2026-05-01T15:00:00",
    "status": "OPEN"
  }
]
```

---

## Reservation API (reservation 서비스)

### 예약 요청 (핵심 — 동시성 처리)
```
POST /api/v1/reservations
Authorization: Bearer {jwt}

Request:
{
  "resourceId": 1,
  "availableTimeIds": [1, 2],
  "headCount": 2
}

Response 201 (슬롯당 1개 생성, 배열 반환):
[
  {
    "id": 1,
    "status": "PENDING",
    "resourceName": "별채 A",
    "startTime": "2026-05-01T14:00:00",
    "endTime": "2026-05-01T15:00:00",
    "headCount": 2,
    "amount": 150000
  }
]

Error 409 (application/problem+json):
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 예약된 시간대입니다.",
  "code": "RSV_001"
}
```

> reservation 서비스는 예약 생성 전 api 서비스에 `GET /api/v1/internal/resources/{id}` 로 resource 검증 (가격, max_capacity), `GET /api/v1/internal/available-times?ids=` 로 슬롯 상태 및 소속 resource 검증.

### 예약 상세 조회
```
GET /api/v1/reservations/{reservationId}

Response 200:
{
  "id": 1,
  "status": "CONFIRMED",
  "resourceName": "별채 A",
  "startTime": "2026-05-01T14:00:00",
  "endTime": "2026-05-01T15:00:00",
  "headCount": 2,
  "amount": 150000
}
```

> 결제 정보가 함께 필요하면 클라이언트가 `/payments/{reservationId}` 를 별도 호출. 서비스 간 합성을 reservation 에서 하지 않음 (BFF 또는 클라이언트 책임).

### 내 예약 목록
```
GET /api/v1/reservations/me?status=CONFIRMED&page=0&size=10

Response 200:
{
  "content": [
    {
      "reservationId": 1,
      "resourceId": 5,
      "resourceName": "별채 A",
      "startTime": "2026-05-01T14:00:00",
      "endTime": "2026-05-01T15:00:00",
      "headCount": 2,
      "amount": 150000,
      "status": "CONFIRMED",
      "createdAt": "2026-04-25T10:30:00"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 5,
  "totalPages": 1
}
```

> 목록 항목에는 결제 정보가 포함되지 않음. 결제 상세가 필요하면 `/payments/{reservationId}` 별도 호출.
> `resourceName` 은 reservation 서비스가 api 서비스에 batch 로 조회하거나 (`POST /api/v1/internal/resources/lookup` with id 배열), 예약 시점에 snapshot 으로 저장해 둘 수 있음 — 구현 결정 필요.

### 예약 취소
```
PUT /api/v1/reservations/{reservationId}/cancel

Response 200:
{
  "reservationId": 1,
  "status": "CANCELLED"
}
```

---

## Payment API (payment 서비스)

### 결제 내역 조회
```
GET /api/v1/payments/{reservationId}

Response 200:
{
  "paymentId": 1,
  "reservationId": 1,
  "amount": 150000,
  "status": "COMPLETED",
  "paidAt": "2026-05-01T13:00:00"
}
```

### 환불 요청
```
POST /api/v1/payments/{reservationId}/refund

Response 200:
{
  "paymentId": 1,
  "status": "REFUNDED"
}

Error 409 (status 가 COMPLETED 가 아닐 때):
{
  "status": 409,
  "code": "PAY_002",
  "detail": "환불 가능 상태가 아닙니다."
}
```

---

## Admin API (api 서비스 → reservation 위임)

api 서비스가 진입점이지만 실제 데이터는 reservation 서비스에서 REST 로 가져온다.
예약 목록 조회는 merchant 기반 조회(`GET /api/v1/merchants/{merchantId}/reservations`)를 사용한다.

### 캘린더 뷰
```
GET /api/v1/admin/reservations/calendar?year=2026&month=5
Authorization: Bearer {jwt}  (MERCHANT 역할)

Response 200:
{
  "2026-05-01": [
    {
      "reservationId": 1,
      "resourceName": "별채 A",
      "startTime": "14:00",
      "endTime": "15:00",
      "status": "CONFIRMED"
    }
  ]
}
```

### 예약 수동 확정
```
PUT /api/v1/admin/reservations/{reservationId}/confirm

Response 200:
{
  "reservationId": 1,
  "status": "CONFIRMED"
}
```

### 예약 취소 처리
```
PUT /api/v1/admin/reservations/{reservationId}/cancel

Response 200:
{
  "reservationId": 1,
  "status": "CANCELLED"
}
```

---

## Notification API (notification 서비스)

### 내 알림 목록
```
GET /api/v1/notifications/me
Authorization: Bearer {jwt}

Response 200:
[
  {
    "id": 1,
    "reservationId": 1,
    "type": "CONFIRMED",
    "channel": "LOG",
    "status": "SENT",
    "sentAt": "2026-05-01T14:00:00"
  }
]
```

> type: `CONFIRMED` / `CANCELLED`
> channel: `EMAIL` / `SMS` / `KAKAO` / `LOG`
> status: `SENT` / `FAILED`

---

## 내부 API (서비스 간 호출 전용)

`/api/v1/internal/**` 는 외부 노출 X (보안 그룹 / Gateway 에서 차단). JWT 토큰을 서비스 간 전달하여 검증.

### api 서비스 내부 API

| Endpoint | 호출자 | 용도 |
|----------|--------|------|
| `GET /api/v1/internal/resources/{id}` | reservation | 가격/정원 검증 |
| `GET /api/v1/internal/available-times?ids=` | reservation | 슬롯 상태 및 resource 귀속 검증 |
| `PUT /api/v1/internal/available-times/block` | reservation | 슬롯 상태 BOOKED 처리 |
| `PUT /api/v1/internal/available-times/release` | reservation | 슬롯 상태 OPEN 복구 |
| `GET /api/v1/internal/users/{id}` | payment, notification | 사용자 정보 (이메일, 이름) |

### reservation 서비스 내부 API

| Endpoint | 호출자 | 용도 |
|----------|--------|------|
| `GET /api/v1/internal/reservations/calendar?year=&month=` | api (admin) | 캘린더 뷰 |
| `GET /api/v1/internal/reservations/{id}` | api (admin) | 예약 상세 |
| `PUT /api/v1/internal/reservations/{id}/confirm` | api (admin) | 수동 확정 |
| `PUT /api/v1/internal/reservations/{id}/cancel` | api (admin) | 수동 취소 |
| `GET /api/v1/internal/reservations/by-merchant?resourceIds=` | api (merchant) | 업체별 예약 목록 |
