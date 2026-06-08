# /test

`/impl`로 구현된 코드에 대한 테스트 코드를 작성하고 검증한다.

사용법: `/test <기능명>`  
예시: `/test outbox-compensation`

## 실행 순서

### 1. 구현 파악

`.claude/tasks/{feature}.md`와 구현된 파일을 읽어 테스트 대상을 파악한다.

### 2. 테스트 코드 작성

구현 파일별로 테스트를 작성한다.

**테스트 원칙:**
- 단위 테스트: 서비스 레이어 로직 (Mockito로 의존성 격리)
- 통합 테스트: Repository 레이어 (`@DataJpaTest`), Kafka consumer/producer (`@SpringBootTest` + EmbeddedKafka)
- 테스트 클래스 위치: `src/test/java/...` — 구현 클래스와 동일 패키지
- 테스트 메서드명: `{시나리오}_then_{기대결과}` 형식 (한글 가능)

**반드시 포함할 케이스:**
- 정상 경로 (happy path)
- 실패 경로 — `BusinessException`이 올바른 `ErrorCode`로 throw되는지
- 경계값 또는 엣지 케이스 (기능에 따라)

### 3. 작성된 테스트만 실행

작성한 테스트 클래스만 먼저 실행해 테스트 자체가 올바른지 확인한다.

```bash
# 단일 클래스
./gradlew test --tests "com.example.booking.{서비스}.{패키지}.{ClassName}"

# 특정 메서드
./gradlew test --tests "com.example.booking.{서비스}.{패키지}.{ClassName}.{methodName}"
```

실패 시 테스트 코드 또는 구현 수정 후 재실행. 통과할 때까지 반복.

### 4. 전체 테스트 실행 (clean)

개별 테스트 통과 후 캐시 없이 전체 테스트를 돌려 회귀를 확인한다.

```bash
./gradlew clean test
```

실패 시:
- 기존 테스트가 깨진 경우 → 구현 코드 확인 후 수정
- 새 테스트가 깨진 경우 → 테스트 코드 재검토

전체 통과할 때까지 반복.

### 5. 완료 보고

작성한 테스트 파일 목록, 테스트 케이스 수, 전체 테스트 결과(통과/실패)를 출력.