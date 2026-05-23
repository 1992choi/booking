# 06-1. api 서비스

## 역할

인증 전용 서비스. 회원(User) CRUD + JWT 발급/갱신.

| 항목 | 값 |
|------|-----|
| 포트 | 8080 |
| DB | db_api |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | 없음 |

---

## 책임 도메인

api 서비스가 자체 DB 에 소유하는 엔티티:
- User (회원)

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
    ├── internal/
    │   └── controller/InternalController.java (서비스 간 호출 endpoint)
    ├── error/
    │   └── ApiErrorCode.java
    └── config/
        └── SecurityConfig.java
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
그 외                                        → authenticated
```

### Internal API

`/api/v1/internal/**` — 외부 노출 금지. payment/notification 서비스가 JWT 를 전달해 호출한다.

| Endpoint | 호출자 | 용도 |
|----------|--------|------|
| `GET /api/v1/internal/users/{id}` | payment, notification | 사용자 정보 (이메일, 이름) |

### ApiErrorCode

| code | HTTP | 설명 |
|------|------|------|
| API_001 | 409 | 이메일 중복 |
| API_002 | 401 | 이메일/비밀번호 불일치 |
