# 06-1. api 서비스

## 역할

인증 및 회원 관리 서비스. 회원(User) CRUD + JWT 발급/갱신 + 유저 변경 이벤트 Kafka 발행.

| 항목 | 값 |
|------|-----|
| 포트 | 8080 |
| DB | db_api (MySQL) + db_api_audit (MongoDB, 감사 로그 전용) |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | notification (HTTP — 관리자 메시지 발송), LiteLLM (HTTP — AI 어시스턴트 챗봇) |

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
    ├── assistant/
    │   ├── LiteLlmClient.java              (RestClient 래퍼 — 재시도 + 서킷브레이커 적용)
    │   ├── controller/AssistantController.java
    │   └── dto/ (ChatRequest, ChatTurn, ChatResponse)
    ├── internal/
    │   └── controller/InternalController.java (서비스 간 호출 endpoint)
    ├── error/
    │   └── ApiErrorCode.java
    └── config/
        ├── SecurityConfig.java
        ├── RestClientConfig.java           (notification, litellm RestClient 빈)
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
/actuator/**                                → permitAll
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

### 감사 로그 (MongoDB)

`core`의 `AuditService`(`05-module-core.md` 참고)로 로그인 성공만 기록한다. 로그인 실패는 기록하지 않는다.

| 액션 | 기록 시점 | detail |
|------|----------|--------|
| `LOGIN` | `AuthService.login()` 성공 시 | `email` |

MongoDB 연결은 `db_api`(MySQL)와 별개로 `db_api_audit`(MongoDB)를 사용하며, 기록 실패는 로그인 자체를 막지 않는다.

### Kafka 이벤트 발행

회원 상태 변경 시 `ApplicationEventPublisher` → `@TransactionalEventListener(AFTER_COMMIT)` 패턴으로 발행.

| 토픽 | 발행 시점 | 페이로드 |
|------|-----------|----------|
| `user.created` | 회원가입 완료 | userId, name, email, phone |
| `user.updated` | 정보 수정 완료 | userId, name, email, phone |
| `user.deleted` | 탈퇴 완료 | userId |

### AI 어시스턴트 (`POST /api/v1/assistant/chat`)

```
Client → POST /assistant/chat {message, history?}
       → AssistantController → LiteLlmClient
                                  ↓ RestClient (재시도 + 서킷브레이커, "litellm" 인스턴스)
                                  ↓ POST {litellm}/chat/completions (OpenAI 호환)
                                LiteLLM Gateway
                                  ├── model=claude → Anthropic Claude API
                                  └── model=local  → Ollama (로컬 모델)
       ← {reply}
```

시스템 프롬프트는 `src/main/resources/assistant/system-prompt.txt`(사용법 안내용 정적 텍스트)를 매 요청마다 메시지 맨 앞에 붙인다. 대화 기록은 서버에 영속화하지 않고, 프론트가 보낸 `history`를 그대로 이어붙여 한 번의 요청으로 전달한다(1단계 스코프). Anthropic API 키는 api가 아니라 LiteLLM 컨테이너에만 존재한다. 자세한 설계는 백엔드 `docs/99-backlog.md`의 "AI 사용법 안내 챗봇" 참고.

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
| API_005 | 503 | AI 어시스턴트(LiteLLM) 일시 불가 (서킷브레이커 OPEN) |
