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
| 인증 | `JwtVerifier` (검증만), `AuthPrincipal` | 토큰 발급 (api 서비스 단독) |
| 예외 | `BusinessException`, `GlobalExceptionHandler` | 도메인 예외 클래스 |

---

## 패키지 구조

```
core/
└── src/main/java/com/example/booking/core/
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
    │   ├── AuthPrincipal.java
    │   └── SecurityAutoConfig.java     (@AutoConfiguration)
    └── util/
        └── ...
```

---

## 주요 클래스

### BaseEntity
```java
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

### ErrorCode (인터페이스)
```java
public interface ErrorCode {
    HttpStatus status();
    String code();
    String message();
}
```

### CommonErrorCode (횡단 공통)
```java
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED,    "AUTH_001",   "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN,          "AUTH_002",   "권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND,          "COMMON_001", "리소스를 찾을 수 없습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST,      "COMMON_400", "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### BusinessException
```java
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode;
    }
}
```

### GlobalExceptionHandler (RFC 9457 ProblemDetail)
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handle(BusinessException e) {
        ErrorCode ec = e.getErrorCode();
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ec.status(), ec.message());
        pd.setProperty("code", ec.code());
        return ResponseEntity.status(ec.status()).body(pd);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            e.getBindingResult().getAllErrors().getFirst().getDefaultMessage()
        );
        pd.setProperty("code", CommonErrorCode.BAD_REQUEST.code());
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnknown(Exception e) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.INTERNAL_ERROR.message()
        );
        pd.setProperty("code", CommonErrorCode.INTERNAL_ERROR.code());
        return ResponseEntity.internalServerError().body(pd);
    }
}
```

> 성공 응답은 envelope 없이 DTO 자체를 반환한다. HTTP status 가 진실의 원천.

### JwtVerifier
```java
@Component
public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(@Value("${booking.jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public AuthPrincipal verify(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        return new AuthPrincipal(
            Long.parseLong(claims.getSubject()),
            Role.valueOf(claims.get("role", String.class))
        );
    }
}
```

> 토큰 발급은 api 서비스만 한다. 다른 서비스는 검증만.

### AuthPrincipal
```java
public record AuthPrincipal(Long userId, Role role) {

    public enum Role { USER, OWNER, ADMIN }
}
```

---

## 의존성 (build.gradle)

```groovy
plugins {
    id 'java-library'
}

bootJar { enabled = false }
jar { enabled = true }

dependencies {
    api 'org.springframework.boot:spring-boot-starter-web'
    api 'org.springframework.boot:spring-boot-starter-data-jpa'
    api 'org.springframework.boot:spring-boot-starter-security'
    api 'io.jsonwebtoken:jjwt-api'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
}
```

> 라이브러리이므로 `bootJar` 비활성. 의존성은 `api` 스코프로 선언해 임베드한 서비스에 전이됨.
> jjwt 버전은 도입 시점 최신 안정(0.12.x 이상) 사용.

---

## AutoConfiguration

각 서비스가 core 를 임베드하면 `JwtAuthenticationFilter`, `GlobalExceptionHandler`, JPA Auditing 이 자동 등록되도록 `@AutoConfiguration` 으로 노출한다.

```
core/src/main/resources/META-INF/spring/
  └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
```
