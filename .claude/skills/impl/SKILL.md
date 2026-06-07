# /impl

`.claude/tasks/{feature}.md`를 읽고 설계 기반으로 구현한다.
`/design`이 먼저 실행된 것을 전제로 한다.

사용법: `/impl <기능명>`  
예시: `/impl outbox-compensation`, `/impl jwt-auth`

## 실행 순서

### 1. 태스크 파일 읽기

`.claude/tasks/{feature}.md`를 읽는다.
파일이 없으면 "먼저 `/design {feature}`를 실행하세요" 출력 후 종료.

### 2. 현재 코드 파악

태스크 파일의 "예상 파일 목록"을 기준으로 기존 코드 확인:
- 이미 존재하는 파일은 내용을 읽어 충돌·중복 여부 파악
- 신규 파일은 패키지 구조와 네이밍 컨벤션 확인

### 3. 구현

태스크 파일의 "구현 범위"와 "주의사항"을 엄격히 따른다.

**반드시 지킬 것:**
- 패키지: `com.example.booking.{서비스명}.{레이어}`
- 응답 envelope 없음 — DTO 직접 반환
- 실패는 `BusinessException(errorCode)` — `GlobalExceptionHandler`가 처리
- 각 서비스 전용 ErrorCode enum 사용 (`ReservationErrorCode`, `PaymentErrorCode` 등)
- Kafka 발행은 Outbox INSERT 또는 `@TransactionalEventListener(AFTER_COMMIT)` — 트랜잭션 외부에서만
- 서비스 간 직접 REST 호출 금지 — Kafka 또는 내부 API(`/internal/**`)만 허용

**하지 말 것:**
- 요청하지 않은 기능 추가
- 인접 코드 리팩터링
- 불가능한 시나리오에 대한 방어 코드

### 4. 빌드 확인

```bash
./gradlew build
```

빌드 실패 시 원인 파악 후 수정. 통과할 때까지 반복.

### 5. 완료 보고

생성/수정한 파일 목록과 핵심 변경 사항을 한 줄씩 요약 출력.
태스크 파일의 "주의사항" 중 구현에 반영한 것도 명시.