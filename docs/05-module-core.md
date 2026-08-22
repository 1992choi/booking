# 05. core 모듈 (라이브러리)

## 역할

4개 서비스가 공통으로 의존하는 **라이브러리**. 배포 산출물이 아니다 (`bootJar { enabled = false }`, `jar { enabled = true }`).

도메인 엔티티와 Repository 는 core 에 두지 **않는다** — 각 서비스가 자기 도메인을 자기 DB 와 함께 소유. core 는 횡단 관심사만 담당.

---

## 책임 범위

| 영역 | core 가 제공 | core 가 제공하지 않음 |
|------|--------------|----------------------|
| 엔티티 | `BaseEntity` (audit 필드만) | 도메인 엔티티 (User, Reservation 등) |
| Repository | X | 도메인별 Repository (각 서비스가 정의) |
| 응답 형식 | `ProblemDetail` 핸들러, 에러 응답 표준화 | 성공 응답 envelope (없음 — DTO 직접 반환) |
| 에러 코드 | `ErrorCode` 인터페이스, `CommonErrorCode` (횡단적인 것만) | 도메인 에러 코드 (각 서비스가 enum 정의) |
| 인증 | `JwtVerifier` (검증만), `AuthPrincipal`, `Role` | 토큰 발급 (api 서비스 단독) |
| 예외 | `BusinessException`, `GlobalExceptionHandler` | 도메인 예외 클래스 |
| 관측성 | 요청 로깅 필터(`RequestLoggingFilter`), Micrometer Tracing + OTel OTLP exporter, `/ping` 헬스체크(`PingService`) | 서비스별 커스텀 메트릭 |

---

## 패키지 구조

```
core/
└── src/main/java/com/example/booking/core/
    ├── CoreAutoConfiguration.java       (@AutoConfiguration — PingService, GlobalExceptionHandler, RequestLoggingFilter 등록)
    ├── JpaAuditingAutoConfiguration.java (@AutoConfiguration — @EnableJpaAuditing, EntityManager 있을 때만)
    ├── PingService.java
    ├── entity/
    │   └── BaseEntity.java
    ├── error/
    │   ├── ErrorCode.java              (interface)
    │   ├── CommonErrorCode.java        (enum implements ErrorCode)
    │   ├── BusinessException.java
    │   └── GlobalExceptionHandler.java
    ├── auth/
    │   ├── JwtVerifier.java
    │   ├── JwtAuthenticationFilter.java
    │   ├── JwtAuthenticationEntryPoint.java
    │   ├── AuthPrincipal.java
    │   ├── Role.java
    │   └── SecurityAutoConfig.java     (@AutoConfiguration — booking.jwt.secret 설정 시에만 활성화)
    ├── logging/
    │   └── RequestLoggingFilter.java
    └── tracing/
        └── TracingAutoConfiguration.java (@AutoConfiguration — Micrometer Tracing + OTel OTLP exporter)
```

---

## 에러 처리

각 서비스의 도메인 에러는 `ErrorCode` 인터페이스를 구현한 enum 으로 정의한다 (`ApiErrorCode`, `ReservationErrorCode` 등). `throw new BusinessException(errorCode)` 하면 `GlobalExceptionHandler` 가 RFC 9457 `application/problem+json` 으로 변환해 응답한다.

`CommonErrorCode` 는 서비스 경계를 넘는 횡단 코드만 담는다 (`AUTH_001`, `AUTH_002`, `COMMON_001`, `COMMON_400`, `COMMON_500`). 도메인 에러 코드는 각 서비스 enum 에만 정의한다.

에러 코드 전체 목록은 `04-api-spec.md` 참고.

---

## JWT

토큰 **검증**은 `JwtVerifier` (core) 를 모든 서비스가 공유한다. 토큰 **발급**은 api 서비스 `JwtIssuer` 만 담당한다.

`JwtAuthenticationFilter` 가 매 요청마다 `Authorization: Bearer {token}` 을 파싱해 `AuthPrincipal` 을 `SecurityContext` 에 저장한다. 인증 실패(토큰 없음/무효) 시 `JwtAuthenticationEntryPoint` 가 `application/problem+json` 형식의 401 응답을 직접 작성한다.

```java
public record AuthPrincipal(Long userId, Role role) {}

public enum Role { USER, MERCHANT, ADMIN }
```

---

## AutoConfiguration

각 서비스가 core 를 임베드하면 아래 4개 `@AutoConfiguration` 이 자동 등록된다 (`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).

| AutoConfiguration | 활성 조건 | 등록하는 것 |
|---|---|---|
| `CoreAutoConfiguration` | 항상 | `PingService`, `GlobalExceptionHandler`, `RequestLoggingFilter`(최우선 순위 — 인증 실패도 로깅) |
| `JpaAuditingAutoConfiguration` | `EntityManager` 클래스패스에 있을 때 | `@EnableJpaAuditing` (BaseEntity 의 createdAt/updatedAt 자동 채움). `pg` 모듈처럼 JPA 를 쓰지 않으면 비활성 |
| `SecurityAutoConfig` | `booking.jwt.secret` 프로퍼티가 설정됐을 때 | `JwtVerifier`, `JwtAuthenticationFilter`, `JwtAuthenticationEntryPoint` |
| `TracingAutoConfiguration` | OpenTelemetry/Micrometer Tracing 클래스패스에 있을 때 | OTLP export 용 `OpenTelemetry`/`Tracer` 빈 |