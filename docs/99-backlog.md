# 99. Backlog

## 코드 컨벤션 정비

코드 스타일, 아키텍처 일관성, 추상화 레벨이 서비스/레이어별로 중구난방인 상태. 전반적으로 맞춰야 함.

### 코드 스타일
- Checkstyle 또는 Google Java Style Guide 기준 적용 (들여쓰기, 네이밍, 임포트 정렬 등)
- Lombok 사용 범위 통일 (`@RequiredArgsConstructor` 중심으로)
- 불필요한 `public` 제거, 접근 제어자 일관화

### 아키텍처 일관성
- 레이어 간 의존 방향 재확인: Controller → Service → Repository (역방향 참조 제거)
- DTO가 도메인 레이어로 내려가지 않는지 점검
- 패키지 구조가 `com.example.booking.{서비스}.{레이어}` 기준으로 통일되어 있는지 확인

### 추상화 레벨
- 메서드 내 추상화 레벨 혼재 제거 (고수준 흐름 메서드에 저수준 구현 섞이지 않도록)
- 서비스 메서드 단위 재검토 — 한 메서드가 한 가지 책임만 갖는지
- 의미 없는 위임 메서드(pass-through) 제거

---

## 동시성 보강

### Redis 분산락
예약 생성(`POST /api/v1/reservations`) 진입 시 Redisson `tryLock`으로 동일 resourceId 동시 요청을 직렬화.
실패 시 409 RSV_002 반환.

```java
RLock lock = redissonClient.getLock("reservation:lock:" + resourceId);
if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
    throw new BusinessException(ReservationErrorCode.LOCK_FAILED);
}
```

### DB 비관적락
`findOverlapping` 쿼리에 `@Lock(LockModeType.PESSIMISTIC_WRITE)` 추가.
Redis 락이 뚫렸을 때의 마지막 방어선.

---

## Outbox 패턴 + 보상 트랜잭션

### 배경
현재 `@TransactionalEventListener(AFTER_COMMIT)`으로 Kafka를 발행하는데, 발행 직전 프로세스가 죽으면 이벤트가 유실된다.
또한 `payment.completed`를 reservation과 notification이 각각 독립 consume하므로, reservation 확정이 실패해도 notification이 발송되는 불일치가 발생한다.

### 해결 방향

**1. Outbox 패턴**

비즈니스 로직과 outbox INSERT를 같은 트랜잭션으로 묶어 유실을 방지한다.
`@Scheduled` 폴러가 PENDING 레코드를 읽어 Kafka에 발행 후 PUBLISHED 처리.

```
트랜잭션 커밋
  ├── 비즈니스 테이블 변경 (e.g. Reservation INSERT)
  └── outbox INSERT (status=PENDING)   ← 원자적

[@Scheduled]
  → outbox WHERE status=PENDING 조회
  → Kafka publish
  → status = PUBLISHED
```

**2. 이벤트 체인 재설계**

notification이 `payment.completed` 대신 `reservation.confirmed` 를 구독하도록 변경.
reservation 확정이 실제로 성공한 이후에만 알림이 나간다.

```
현재
payment.completed ──┬── reservation: CONFIRMED
                    └── notification: 알림 발송  (reservation 결과 무관)

개선
payment.completed ──── reservation: CONFIRMED + outbox(reservation.confirmed)
                                └── notification: 알림 발송
```

**3. 보상 트랜잭션**

결제 성공 후 예약 확정이 실패한 경우, 결제를 환불하고 슬롯을 복원한다.

```
payment.completed → reservation confirm 실패
                        ↓ 보상
                    payment: 환불 처리 (REFUNDED)
                    reservation: CANCELLED
                    available_time: OPEN 복원
```

### 적용 대상

| 서비스 | 이벤트 | 비고 |
|--------|--------|------|
| reservation | `reservation.created` | outbox 교체 |
| payment | `payment.completed` / `payment.failed` | outbox 교체 |
| reservation | `reservation.confirmed` | 신규 이벤트 + outbox |
| reservation | `reservation.cancelled` | outbox 교체 |

---

## Claude 활용 개발 플로우

### 개요

```
/plan → /impl → /convention-check → /test → /update-docs
```

각 단계를 skill / hook / agent 중 적절한 수단으로 구성한다.

---

### Step 1+2: 정리 & 파일 기록 — `/plan` (custom skill)

`.claude/commands/plan.md`로 정의.

- 관련 docs 자동 읽기: `06-*` 모듈 스펙, `04-api-spec.md`, `03-erd.md`
- 불명확한 부분 질문
- 구현 계획을 `.claude/tasks/{feature}.md`에 저장
  - 구현 범위, API 엔드포인트, ERD 변경, 예상 파일 목록

파일로 남기는 이유: 이후 `/impl` 호출 시 context를 다시 설명하지 않아도 됨.

---

### Step 3: 구현 — `/impl` (custom skill)

`.claude/tasks/{feature}.md`를 읽고 시작.
CLAUDE.md의 "기능 구현 전 docs 3개 확인" 규칙을 자동 수행.

---

### Step 4: 컨벤션 확인 — 두 레이어

**자동 레이어**: `PostToolUse` hook — Edit/Write 후 경량 grep 체크

```json
{
  "hooks": {
    "PostToolUse": [{
      "matcher": "Edit|Write",
      "hooks": [{ "type": "command", "command": ".claude/scripts/convention-lint.sh" }]
    }]
  }
}
```

`convention-lint.sh`: envelope 응답 여부, 패키지 구조, ErrorCode 위치 등 빠른 패턴 검사.

**명시적 레이어**: `/convention-check` (custom skill) — 구현 완료 후 AI가 맥락을 이해하며 종합 검증.

---

### Step 5: 테스트 — `/test` (custom skill)

`/impl` 결과물을 읽고 테스트 작성. 또는 `/impl`에 `--tdd` 옵션으로 통합 가능.

---

### Step 6: 문서 현행화 — Stop hook + `/update-docs`

```json
{
  "hooks": {
    "Stop": [{
      "hooks": [{ "type": "command", "command": ".claude/scripts/docs-reminder.sh" }]
    }]
  }
}
```

`docs-reminder.sh`: Java 파일 변경 여부 확인 후 docs 현행화 필요 메시지 출력.
실제 업데이트는 `/update-docs` (built-in skill) 호출.

---

### 수단별 정리

| 단계 | 수단 | 이유 |
|------|------|------|
| 정리 & 기록 | skill (`/plan`) | 사용자가 트리거, 대화형 |
| 구현 | skill (`/impl`) | 파일 읽고 시작, 반복 패턴 |
| 컨벤션 (자동) | PostToolUse hook | 매 파일 저장마다 자동 |
| 컨벤션 (종합) | skill (`/convention-check`) | AI 판단이 필요한 부분 |
| 테스트 | skill (`/test`) | 구현과 분리된 명시적 단계 |
| 문서 | Stop hook + `/update-docs` | 잊지 않도록 알림, 실행은 수동 |

---

## 분산 추적 (Micrometer Tracing + Zipkin)

MSA 환경에서 요청이 여러 서비스를 거칠 때 흐름 추적.
`reservation → payment → notification` 체인의 지연/오류 원인 파악이 현재 로그만으로는 어려움.

- Micrometer Tracing + Brave 의존성 추가 (각 서비스)
- Zipkin 컨테이너 추가 (docker-compose)
- TraceId/SpanId 자동 전파 확인

---

## Rate Limiting (Bucket4j)

외부 노출 API의 어뷰징 방지. 예약 생성(`POST /api/v1/reservations`) 등 비용이 큰 엔드포인트 우선 적용.

- Bucket4j + Redis 기반 분산 rate limit (서비스 인스턴스가 여러 개여도 공유 카운터)
- 초과 시 429 응답

---

## QueryDSL

동적 쿼리가 필요한 목록 조회에 적용. 현재 `@Query` JPQL로 작성된 정적 쿼리를 보완.

- 적용 대상: 예약 목록 (`status`, `date` 필터 조합), 업체별 예약 캘린더 조회
- `JPAQueryFactory` 빈 등록 + Q클래스 생성 설정 (각 서비스 `build.gradle`)

---

## batch 모듈 (Spring Batch)

독립 모듈(`batch`)로 추가. 구체적인 기능은 미정이나 아래 방향 중 하나 이상 적용 예정.

- **통계 처리**: 일별/월별 예약 건수, 매출 집계
- **이벤트 트리거**: 미완료 예약 자동 만료 (PENDING 상태 N시간 초과 시 CANCELLED), 슬롯 복원
- Chunk 기반 처리, JobParameter로 실행 기준 제어
- `docker-compose`에 배치 실행 환경 추가

---

## CQRS

예약 조회(Read)와 생성/취소(Write) 모델 분리. 현재는 동일 엔티티로 읽기/쓰기를 모두 처리.

- Write 모델: 기존 JPA 엔티티 유지
- Read 모델: 조회 전용 DTO/Repository 분리 (QueryDSL 또는 별도 Read DB)
- 적용 대상: 예약 목록 조회, 업체별 캘린더 뷰 — 조회 빈도가 높고 Write와 요구사항이 달라 분리 효과가 큼