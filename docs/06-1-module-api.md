# 06-1. api 서비스

## 역할

인증 및 회원 관리 서비스. 회원(User) CRUD + JWT 발급/갱신 + 유저 변경 이벤트 Kafka 발행.

| 항목 | 값 |
|------|-----|
| 포트 | 8080 |
| DB | db_api |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | notification (HTTP — 관리자 메시지 발송) |

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
    │   ├── filter/LoginRateLimitFilter.java (로그인 요청 rate limiting — Bucket4j)
    │   ├── JwtIssuer.java                  (토큰 발급 — api 서비스 단독)
    │   ├── RefreshTokenBlacklist.java      (Redis 기반 JTI 블랙리스트)
    │   ├── RefreshTokenClaims.java
    │   └── dto/
    ├── user/
    │   ├── controller/UserController.java
    │   ├── service/UserService.java
    │   ├── domain/User.java
    │   ├── dto/
    │   └── event/
    │       ├── UserCreatedDomainEvent.java
    │       ├── UserUpdatedDomainEvent.java
    │       ├── UserDeletedDomainEvent.java
    │       ├── UserCreatedKafkaEvent.java
    │       ├── UserUpdatedKafkaEvent.java
    │       ├── UserDeletedKafkaEvent.java
    │       └── UserEventPublisher.java
    ├── notification/
    │   ├── NotificationClient.java         (RestClient 래퍼 — 재시도 + 서킷브레이커 적용)
    │   └── dto/SendAdminMessageRequest.java
    ├── internal/
    │   └── controller/InternalController.java (서비스 간 호출 endpoint)
    ├── error/
    │   └── ApiErrorCode.java
    └── config/
        ├── SecurityConfig.java
        ├── RestClientConfig.java           (notification RestClient 빈)
        ├── RateLimitConfig.java
        └── KafkaConfig.java
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

> Refresh Token은 Stateless JWT (`type` claim으로 구분). 로그아웃 시 JTI를 Redis 블랙리스트에 등록 (TTL = refresh token 잔여 만료 시간).
> `/auth/refresh` 에 access token을 넘기거나, 블랙리스트에 등록된 JTI를 사용하면 401 반환.

### 접근 제어 (SecurityConfig)

```
/api/v1/auth/**                             → permitAll
/ping                                       → permitAll
/api/v1/admin/**                            → ADMIN role 필요
그 외 (내부 API 포함)                        → authenticated
```

### User API

| Endpoint | 설명 |
|----------|------|
| `GET /api/v1/users/me` | 내 정보 조회 |
| `PUT /api/v1/users/me` | 이름/전화번호 수정 → `user.updated` 이벤트 발행 |
| `DELETE /api/v1/users/me` | 회원 탈퇴 (hard delete) → `user.deleted` 이벤트 발행 |
| `GET /api/v1/admin/users?role=` | 유저 목록 조회 (ADMIN 전용, role 필터 선택) |
| `POST /api/v1/admin/users/{userId}/message` | 특정 유저에게 관리자 메시지 발송 (notification 서비스에 HTTP 위임) |

### 로그인 Rate Limiting

`LoginRateLimitFilter` (Bucket4j + Redis) 가 `/api/v1/auth/login` 요청에 적용된다. 임계치 초과 시 429 (`API_003`) 반환.

### Kafka 이벤트 발행

회원 상태 변경 시 `ApplicationEventPublisher` → `@TransactionalEventListener(AFTER_COMMIT)` 패턴으로 발행.

| 토픽 | 발행 시점 | 페이로드 |
|------|-----------|----------|
| `user.created` | 회원가입 완료 | userId, name, email, phone |
| `user.updated` | 정보 수정 완료 | userId, name, email, phone |
| `user.deleted` | 탈퇴 완료 | userId |

### Internal API

`/api/v1/internal/**` — 외부 노출 금지.

| Endpoint | 용도 |
|----------|------|
| `GET /api/v1/internal/users/{id}` | 사용자 정보 조회 |

### ApiErrorCode

| code | HTTP | 설명 |
|------|------|------|
| API_001 | 409 | 이메일 중복 |
| API_002 | 401 | 이메일/비밀번호 불일치 |
| API_003 | 429 | 로그인 요청 횟수 초과 |
| API_004 | 503 | 알림 서비스 일시 불가 (서킷브레이커 OPEN) |
