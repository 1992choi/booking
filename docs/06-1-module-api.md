# 06-1. api 서비스

## 역할

외부 HTTP 요청의 진입점 + 사용자/업체/리소스 도메인 소유 + 인증(JWT 발급) + 관리 API 의 진입점.

| 항목 | 값 |
|------|-----|
| 포트 | 8080 |
| DB | api_db |
| 외부 노출 | O |
| 의존 | core (라이브러리) |
| 호출하는 서비스 | reservation (REST, 관리 API 위임) |

---

## 책임 도메인

api 서비스가 자체 DB 에 소유하는 엔티티:
- User (회원)
- Owner (업체)
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
    │   ├── service/JwtIssuer.java          (토큰 발급 — api 서비스 단독)
    │   ├── domain/                          (RefreshToken 등)
    │   └── dto/
    ├── user/
    │   ├── controller/UserController.java
    │   ├── service/UserService.java
    │   ├── domain/User.java
    │   ├── domain/UserRepository.java
    │   └── dto/
    ├── owner/
    │   ├── controller/OwnerController.java
    │   ├── service/OwnerService.java
    │   ├── domain/
    │   └── dto/
    ├── resource/
    │   ├── controller/ResourceController.java
    │   ├── service/ResourceService.java
    │   ├── domain/Resource.java
    │   ├── domain/AvailableTime.java
    │   └── dto/
    ├── admin/
    │   ├── controller/AdminController.java   (reservation 서비스 위임)
    │   └── client/ReservationClient.java     (RestClient)
    ├── internal/
    │   └── controller/InternalController.java (서비스 간 호출 endpoint)
    ├── error/
    │   └── ApiErrorCode.java                  (api 도메인 ErrorCode)
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
Client → /users/me  (Bearer token)
       → JwtAuthenticationFilter (core 제공)
           ↓ JwtVerifier.verify(token)
           ↓ SecurityContext 에 AuthPrincipal 저장
       → UserController
```

### JwtIssuer (api 서비스 단독)

```java
@Component
public class JwtIssuer {

    private final SecretKey key;
    private final long accessTokenTtl;

    public String issue(Long userId, AuthPrincipal.Role role) {
        return Jwts.builder()
            .subject(userId.toString())
            .claim("role", role.name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + accessTokenTtl))
            .signWith(key)
            .compact();
    }
}
```

> 검증은 core 의 `JwtVerifier` 를 모든 서비스가 공유. 발급은 api 만.

### SecurityConfig

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        return http
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/resources/*/available-times").permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/internal/**").access(internalCallerOnly())
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

### Controller 에서 사용자 추출

```java
@GetMapping("/users/me")
public UserResponse getMe(@AuthenticationPrincipal AuthPrincipal principal) {
    return userService.getById(principal.userId());
}
```

### ApiErrorCode

```java
@Getter
@RequiredArgsConstructor
public enum ApiErrorCode implements ErrorCode {
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "API_001", "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "API_002", "이메일 또는 비밀번호가 일치하지 않습니다."),
    OWNER_ALREADY_EXISTS(HttpStatus.CONFLICT, "API_003", "이미 등록된 업체가 있습니다."),
    OWNER_NOT_FOUND(HttpStatus.NOT_FOUND, "API_004", "업체를 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "API_005", "예약 대상을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### Admin → reservation 위임

```java
@RestController
@RequestMapping("/api/v1/admin/reservations")
@RequiredArgsConstructor
public class AdminController {

    private final ReservationClient reservationClient;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<ReservationSummary> list(@RequestParam Map<String, String> params) {
        return reservationClient.search(params);
    }
}

@Component
@RequiredArgsConstructor
public class ReservationClient {

    private final RestClient restClient;  // resilience4j 적용

    public PageResponse<ReservationSummary> search(Map<String, String> params) {
        return restClient.get()
            .uri(uri -> uri.path("/api/v1/internal/reservations").queryParams(toMap(params)).build())
            .retrieve()
            .body(new ParameterizedTypeReference<>() {});
    }
}
```

### Internal API (서비스 간 호출용)

```java
@RestController
@RequestMapping("/api/v1/internal")
public class InternalController {

    @GetMapping("/resources/{id}")
    public ResourceSnapshot getResource(@PathVariable Long id) {
        // reservation 서비스가 예약 시 검증용으로 호출
    }

    @GetMapping("/users/{id}")
    public UserSnapshot getUser(@PathVariable Long id) {
        // payment / notification 이 사용자 정보 조회용
    }
}
```

`/api/v1/internal/**` 은 외부 노출 금지 (보안 그룹 / Gateway 차단).

---

## 의존성 (build.gradle)

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot'
    id 'io.spring.dependency-management'
}

dependencies {
    implementation project(':core')

    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'io.github.resilience4j:resilience4j-spring-boot3'

    runtimeOnly 'com.mysql:mysql-connector-j'

    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
```
