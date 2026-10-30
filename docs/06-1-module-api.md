# 06-1. api 서비스

## 역할

인증 및 회원 관리 서비스. 회원(User) CRUD + JWT 발급/갱신 + 유저 변경 이벤트 Kafka 발행.

| 항목 | 값 |
|------|-----|
| 포트 | 8080 |
| DB | db_api (MySQL) + db_api_audit (MongoDB, 감사 로그 전용) |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | notification (HTTP — 관리자 메시지 발송), LiteLLM/reservation/Loki (HTTP — AI 어시스턴트 챗봇) |

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
    │   ├── AssistantService.java            (tool-use 에이전트 루프, role별 tool 필터링)
    │   ├── AssistantTool.java               (tool 인터페이스 — adminOnly() 기본 false)
    │   ├── AssistantContext.java            (userId, jwt, role)
    │   ├── LiteLlmClient.java               (RestClient 래퍼 — 재시도 + 서킷브레이커 적용, 단일 호출만)
    │   ├── ReservationClient.java           (RestClient 래퍼 — JWT forward)
    │   ├── LokiLogClient.java               (RestClient 래퍼 — LogQL 쿼리)
    │   ├── AdminSqlClient.java              (JdbcTemplate 래퍼 — SELECT만 허용)
    │   ├── controller/AssistantController.java
    │   ├── tool/
    │   │   ├── ReservationStatusTool.java    (get_my_reservations — 전체 사용자)
    │   │   ├── ReservationErrorTool.java     (find_recent_reservation_error — 전체 사용자)
    │   │   ├── AdminSqlTool.java             (run_sql — ADMIN 전용)
    │   │   └── AdminLogTool.java             (run_logql — ADMIN 전용)
    │   └── dto/ (ChatRequest, ChatTurn, ChatResponse, LlmMessage, ToolCall, ToolDefinition)
    ├── internal/
    │   └── controller/InternalController.java (서비스 간 호출 endpoint)
    ├── error/
    │   └── ApiErrorCode.java
    └── config/
        ├── SecurityConfig.java
        ├── RestClientConfig.java           (notification, litellm, reservation, loki RestClient 빈)
        ├── AdminSqlConfig.java             (run_sql tool용 JdbcTemplate — 스키마 미지정, 멀티 DB 조회)
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
       → AssistantController → AssistantService (tool-use 루프, 최대 3회 반복)
                                  ↓ LiteLlmClient.complete(messages, tools)
                                  ↓ POST {litellm}/chat/completions (OpenAI 호환)
                                LiteLLM Gateway
                                  ├── model=claude → Anthropic Claude API
                                  └── model=local  → Ollama (로컬 모델)
                                  ↓ finish_reason=tool_calls 면
                                AssistantService.availableTools(context)로 role에 맞는 tool만 추려서 실행
                                  → 결과를 role=tool 메시지로 다시 LiteLLM에 전달 (반복)
       ← {reply}
```

시스템 프롬프트는 `src/main/resources/assistant/system-prompt.txt`(사용법 안내용 정적 텍스트)를 매 요청마다 메시지 맨 앞에 붙인다. 대화 기록은 서버에 영속화하지 않고, 프론트가 보낸 `history`를 그대로 이어붙여 한 번의 요청으로 전달한다. Anthropic API 키는 api가 아니라 LiteLLM 컨테이너에만 존재한다.

**전체 사용자용 tool** — 파라미터가 없다(모델이 "누구의" 데이터인지 지정하지 못하게, 항상 호출자 본인 범위로만 실행):
- `get_my_reservations`: `ReservationClient`가 호출자 JWT를 forward해서 `GET /api/v1/reservations/me` 호출, 응답 JSON을 그대로 tool 결과로 반환
- `find_recent_reservation_error`: `LokiLogClient`가 `{app="reservation", userId="..."}` 라벨로 최근 15분 내 "Business exception" 로그를 조회해 `code=`/`message=`만 추출해서 반환(원본 로그 전체는 넘기지 않음)

**ADMIN 전용 tool** — `AssistantTool.adminOnly() == true`인 tool은 `AssistantService.availableTools()`가 호출자 role이 ADMIN일 때만 `tools` 목록에 포함시킨다(USER/MERCHANT에겐 LiteLLM에 애초에 tool 정의 자체가 전달되지 않음 — 모델이 그런 tool이 있다는 것조차 모름). CS 상담 시 범위 제한 없이 조회 가능:
- `run_sql`: Claude가 직접 작성한 **SELECT 전용** SQL을 `AdminSqlClient`(JdbcTemplate)로 실행. 스키마 미지정 DataSource(`AdminSqlConfig`)라 `db_reservation.reservations`처럼 항상 스키마를 붙여서 조회(database-per-service 경계를 의도적으로 넘는 예외 — CS 조회 목적으로만). `SELECT`로 시작하지 않는 쿼리는 실행 전에 거부
- `run_logql`: Claude가 직접 작성한 LogQL을 `LokiLogClient.query()`로 실행, 범위 제한 없이 원하는 `app`/`userId`/기간으로 조회

각 tool은 호출 실패 시 예외를 던지지 않고 에러 문자열을 그대로 tool 결과로 반환한다 — 어시스턴트 전체가 503으로 죽는 대신 Claude가 "조회에 실패했습니다"류의 답변을 만들도록 유도. 자세한 설계는 백엔드 `docs/99-backlog.md`의 "AI 사용법 안내 챗봇" 참고.

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
