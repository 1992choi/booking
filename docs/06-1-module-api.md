# 06-1. api 서비스

## 역할

외부 HTTP 요청의 진입점 + 사용자/업체(Merchant)/리소스 도메인 소유 + 인증(JWT 발급) + 관리 API 의 진입점.

| 항목 | 값 |
|------|-----|
| 포트 | 8080 |
| DB | db_api |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | reservation (REST, 관리 API 위임) |

---

## 책임 도메인

api 서비스가 자체 DB 에 소유하는 엔티티:
- User (회원)
- Merchant (업체, 1:N 관계 — 한 User 가 여러 Merchant 소유 가능)
- Resource (예약 대상)
- AvailableTime (가능 시간대)

---

## 패키지 구조

```
api/
└── src/main/java/com/example/booking/api/
    ├── ApiApplication.java
    ├── auth/
    │   ├── controller/AuthController.java
    │   ├── service/AuthService.java
    │   ├── JwtIssuer.java                  (토큰 발급 — api 서비스 단독)
    │   └── dto/
    ├── user/
    ├── merchant/
    ├── resource/
    ├── admin/
    │   ├── controller/AdminController.java   (reservation 서비스 위임)
    │   └── client/ReservationClient.java     (RestClient)
    ├── internal/
    │   └── controller/InternalController.java (서비스 간 호출 endpoint)
    ├── error/
    │   └── ApiErrorCode.java
    └── config/
        ├── SecurityConfig.java
        └── RestClientConfig.java
```

---

## 주요 설계

### 인증 흐름

```
[Login]
Client → POST /auth/login → AuthService
                              ↓ User 검증 + bcrypt
                              ↓ JwtIssuer.issue(userId, role)
                            ← {accessToken, refreshToken}

[API call]
Client → Bearer token
       → JwtAuthenticationFilter (core 제공)
           ↓ JwtVerifier.verify(token)
           ↓ SecurityContext 에 AuthPrincipal 저장
       → Controller
```

> Refresh Token은 Stateless JWT — 서버에 저장하지 않음. `type` claim(`access` / `refresh`)으로 구분.
> `/auth/refresh` 에 access token 을 넘기면 401 반환.

### 접근 제어 (SecurityConfig)

```
/api/v1/auth/**                             → permitAll
GET /api/v1/resources/*/available-times     → permitAll
GET /api/v1/merchants                       → permitAll
GET /api/v1/merchants/{id}                  → permitAll
/api/v1/admin/**                            → hasRole("MERCHANT")
그 외                                        → authenticated
```

> `JwtAuthenticationFilter` 가 권한을 `ROLE_{role}` 형태로 등록하므로 `hasRole("MERCHANT")` 사용.

### Admin → reservation 위임

`/api/v1/admin/reservations/**` 는 api 서비스가 진입점이지만, `ReservationClient` 를 통해 reservation 서비스의 `/api/v1/internal/reservations/**` 에 위임한다. `Authorization` 헤더를 그대로 전달하고 각 서비스가 JWT 를 독립 검증한다.

### Internal API

`/api/v1/internal/**` 는 서비스 간 호출 전용 — 외부 노출 금지 (보안 그룹 / Gateway 차단).

| Endpoint | 호출자 | 용도 |
|----------|--------|------|
| `GET /api/v1/internal/resources/{id}` | reservation | 가격/정원 검증 |
| `GET /api/v1/internal/users/{id}` | payment, notification | 사용자 정보 |