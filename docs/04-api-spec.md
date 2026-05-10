# 04. API 명세

각 endpoint 가 어느 서비스에 속하는지 표기한다. 클라이언트는 ALB/Gateway 를 거쳐 path 기반으로 라우팅된 서비스에 도달한다.

| Path 패턴 | 라우팅 대상 서비스 |
|-----------|---------------------|
| `/api/v1/auth/**` | api |
| `/api/v1/users/**`, `/api/v1/owners/**`, `/api/v1/resources/**` | api |
| `/api/v1/reservations/**` | reservation |
| `/api/v1/payments/**` | payment |
| `/api/v1/admin/reservations/**` | api → (REST) reservation |

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
| RSV_001 | 409 | 시간대 중복 | reservation |
| RSV_002 | 409 | 동시 요청 락 실패 | reservation |
| RSV_003 | 422 | 인원 초과 (max_capacity) | reservation |
| PAY_001 | 422 | 결제 실패 (비즈니스 결과) | payment |
| PAY_002 | 409 | 환불 불가 상태 | payment |

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
```

---

## Owner API (api 서비스)

### 업체 등록
```
POST /api/v1/owners

Request:
{
  "name": "한옥 펜션",
  "phone": "010-1234-5678",
  "type": "PENSION"
}

Response 201:
{
  "ownerId": 1,
  "name": "한옥 펜션",
  "type": "PENSION"
}
```

### 업체 목록 조회
```
GET /api/v1/owners

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
GET /api/v1/owners/{ownerId}

Response 200:
{
  "id": 1,
  "name": "한옥 펜션",
  "type": "PENSION",
  "resources": []
}
```

---

## Resource API (api 서비스)

### 예약 대상 등록
```
POST /api/v1/owners/{ownerId}/resources

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
  "startTime": "2026-05-01T14:00:00",
  "endTime": "2026-05-01T15:00:00",
  "headCount": 2
}

Response 201:
{
  "reservationId": 1,
  "status": "PENDING",
  "resourceName": "별채 A",
  "startTime": "2026-05-01T14:00:00",
  "endTime": "2026-05-01T15:00:00",
  "amount": 150000
}

Error 409 (application/problem+json):
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 예약된 시간대입니다.",
  "code": "RSV_001"
}
```

> reservation 서비스는 예약 생성 전 api 서비스에 `GET /api/v1/internal/resources/{id}` 로 resource 검증 (가격, max_capacity).

### 예약 상세 조회
```
GET /api/v1/reservations/{reservationId}

Response 200:
{
  "reservationId": 1,
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

### 전체 예약 현황
```
GET /api/v1/admin/reservations?date=2026-05-01&status=CONFIRMED

Response 200:
{
  "content": [],
  "totalElements": 10
}
```

### 캘린더 뷰
```
GET /api/v1/admin/reservations/calendar?year=2026&month=5

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

## 내부 API (서비스 간 호출 전용)

`/api/v1/internal/**` 는 외부 노출 X (보안 그룹 / Gateway 에서 차단). 호출 시 서비스 간 mTLS 또는 내부 토큰 사용.

| Endpoint | 호출자 | 용도 |
|----------|--------|------|
| `GET /api/v1/internal/resources/{id}` | reservation | 가격/정원 검증 |
| `GET /api/v1/internal/users/{id}` | payment, notification | 사용자 정보 (이메일, 이름) |
| `GET /api/v1/internal/reservations/{id}` | api (admin), payment | 예약 상세 |
