# /convention-check

구현된 코드가 `CLAUDE.md`의 "Conventions that bite if missed" 항목을 준수하는지 검증한다.
변경된 파일 기준으로 체크하며, 위반 사항만 출력한다.

사용법: `/convention-check`  
특정 서비스만 검사: `/convention-check <서비스명>`  
예시: `/convention-check reservation`

## 실행 순서

### 1. 검사 대상 파일 파악

```bash
git diff --name-only HEAD
git diff --cached --name-only
```

`.java` 파일만 대상. 서비스명 인자가 있으면 해당 서비스 경로만 필터링.

### 2. 체크리스트 검사

변경된 파일을 읽고 아래 항목을 순서대로 확인한다.

#### 패키지 구조
- 패키지 선언이 `com.example.booking.{서비스명}.{레이어}` 형식인가
- 레이어 depth가 4 이상인가 (base `com.example.booking` = depth 3, 서비스 = depth 4)

#### 응답 형식
- Controller 반환값이 `ResponseEntity<Map>` 또는 `{success, data, error}` 구조가 아닌가
- 성공 응답은 DTO를 직접 반환하는가
- 실패는 `BusinessException(errorCode)` 를 throw하는가 (직접 `ResponseEntity` 에러 바디 구성 금지)

#### ErrorCode 위치
- `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `BAD_REQUEST`, `INTERNAL_ERROR` 외의 도메인 코드가 `core`에 정의되어 있지 않은가
- 각 서비스 도메인 에러코드가 해당 서비스의 `error/` 패키지에 정의되어 있는가

#### HTTP 상태 코드
- `PAYMENT_FAILED` 류 비즈니스 결과가 422로 설정되어 있는가 (500 금지)
- `LOCK_FAILED` 류가 409로 설정되어 있는가 (429/503 금지)

#### Kafka 발행 시점
- Kafka produce 코드가 `@Transactional` 메서드 내부에 직접 존재하지 않는가
- Outbox INSERT 또는 `@TransactionalEventListener(phase = AFTER_COMMIT)` 을 사용하는가

#### 동시성 (reservation 서비스만)
- `Reservation` 관련 테이블에 `UNIQUE` 제약이 추가되어 있지 않은가
- `findOverlapping` 쿼리가 `CANCELLED` 상태를 제외하고 있는가

#### JWT
- JWT 발급 코드(`JwtIssuer`)가 api 서비스 외에 존재하지 않는가
- 각 서비스의 필터가 `JwtVerifier`(core)를 사용하는가

#### 내부 API
- `/internal/**` 경로에 `permitAll()` 이 설정되어 있지 않은가

#### amount 컬럼
- `Reservation.amount`가 수정 가능한 구조가 아닌가 (setter/업데이트 로직 금지)

### 3. 결과 출력

위반 항목만 출력한다. 형식:

```
[위반] {파일경로}:{라인번호} — {항목명}: {설명}
```

위반 없으면: "컨벤션 검사 통과 — 위반 사항 없음" 출력.