# 99. Backlog

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

## CQRS

예약 조회(Read)와 생성/취소(Write) 모델 분리. 현재는 동일 엔티티로 읽기/쓰기를 모두 처리.

- Write 모델: 기존 JPA 엔티티 유지
- Read 모델: 조회 전용 DTO/Repository 분리 (QueryDSL 또는 별도 Read DB)
- 적용 대상: 예약 목록 조회, 업체별 캘린더 뷰 — 조회 빈도가 높고 Write와 요구사항이 달라 분리 효과가 큼

---

## Testcontainers 도입

### 배경

지금 모든 테스트는 로컬 docker-compose로 띄운 실제 MySQL/Redis/Kafka/MongoDB에 직접 연결해서 돈다. 감사 로그 기능 작업 중 이 방식의 부작용을 실제로 겪었다 — Mongo 데이터가 테스트 트랜잭션 롤백 대상이 아니라 실행할 때마다 누적되고, 처음엔 테스트 이메일을 고정 문자열로 써서 반복 실행 시 실패했다(`AuthAuditTest`, `UUID` 기반 이메일로 우회). 인프라가 안 떠 있으면 테스트 자체가 실패하고, 여러 세션이 같은 로컬 인프라를 공유하면 서로 데이터가 섞일 위험도 있다.

### 해결 방향

- 각 서비스 테스트에 Testcontainers(MySQL/Redis/Kafka/MongoDB 모듈)를 도입해 테스트마다 격리된 컨테이너를 뜨고 내리도록 전환
- `@ServiceConnection` 또는 `@DynamicPropertySource`로 커넥션 정보를 자동 주입
- 로컬 docker-compose 인프라와 병행 가능(개발 중 빠른 반복은 기존 방식, CI/정식 테스트 실행은 Testcontainers)

### 적용 대상

| 서비스 | 필요 컨테이너 |
|--------|--------------|
| api | MySQL, Redis, Kafka, MongoDB |
| reservation | MySQL, Redis, Kafka, MongoDB |
| payment | MySQL, Kafka |
| notification | MySQL, Kafka |
| review | MySQL(reservation과 공유) |

### 진행 상태 (notification 파일럿 — MySQL만 적용, Kafka는 보류, 커밋 안 됨)

6개 항목 중 프로덕션 코드를 안 건드리는 유일한 항목이라 notification부터 파일럿으로 시작. 최종적으로 **MySQL만 Testcontainers로 전환**하고 **Kafka는 기존 docker-compose의 localhost Kafka를 그대로 씀** — 36개 테스트 전부 통과 확인(`./gradlew :notification:test`). 변경사항은 전부 working tree에 uncommitted로 남아있음(`notification/build.gradle`, 신규 `TestcontainersConfiguration.java`, 5개 `@SpringBootTest` 클래스에 `@Import` 추가, `application.yml`에서 `spring.datasource.*`만 제거하고 `spring.kafka.bootstrap-servers: localhost:9092`는 유지).

- **MySQL은 됨**: `org.testcontainers.mysql.MySQLContainer`(2.x부터 패키지 이동 — `org.testcontainers.containers.MySQLContainer`는 전체 `@Deprecated`) + `@ServiceConnection`으로 정상 동작
- **Kafka는 보류**: 시도 과정에서 두 가지 문제를 만남
  1. notification은 Boot의 Kafka 자동구성을 안 쓰고 `KafkaConfig`가 `spring.kafka.bootstrap-servers`를 `@Value`로 직접 읽기 때문에 `@ServiceConnection`이 아니라 `DynamicPropertyRegistrar` 빈으로 우회해야 함 — 이 부분 자체는 코드로 해결 가능했음
  2. **진짜 블로커**: `apache/kafka:3.9.0` 이미지가 Apple Silicon(arm64) 호스트에서 Testcontainers로 띄우면 JVM이 `SIGILL`로 죽음(`docker-compose.yml`의 kafka 서비스가 `platform: linux/amd64`를 박아둔 이유로 추정). `docker rmi` 후 amd64 manifest digest로 명시적으로 pull/tag해서 로컬 이미지를 amd64로 고정하니 이 SIGILL 자체는 해결됨(`docker run --platform linux/amd64 apache/kafka:3.9.0 java -version`은 정상 동작 확인) — 하지만 Testcontainers의 네이티브 `org.testcontainers.kafka.KafkaContainer`로 띄우면 **다른 지점에서 여전히 "Container exited with code 1"**로 실패함(아키텍처 문제는 아닌 것으로 보이나, 정확한 원인은 더 파보지 않고 중단함). 이 정도 깊이의 Testcontainers-Kafka 내부 디버깅은 "간단한 작업" 범위를 넘어선다고 판단해 Kafka는 보류하고 MySQL만 적용하는 쪽으로 정리함
- **참고**: 디버깅 과정에서 로컬 `apache/kafka:3.9.0` 이미지 태그가 arm64 ↔ amd64로 여러 번 바뀜 — 최종적으로 amd64로 고정된 상태. 이 과정 중 기존 `docker compose up -d`로 띄워둔 `booking-kafka`가 일시적으로 `unhealthy`로 바뀌었는데(healthcheck가 `kafka-broker-api-versions.sh`를 못 찾음), 확인해보니 이 healthcheck는 이미지의 static PATH 설정에 원래부터 해당 스크립트가 없어 실행될 수 없는 구조였음 — 브로커 자체(로그 확인)는 정상 동작 중이라 이미지 재태깅과는 무관한, 이 docker-compose 설정 자체의 기존 결함으로 보임(재현되면 `docker compose restart kafka`로는 안 고쳐짐 — healthcheck 정의를 `/opt/kafka/bin/kafka-broker-api-versions.sh` 전체 경로로 고치거나 PATH에 추가해야 함)
- **Kafka 재도전 시 참고**: Testcontainers 2.x부터 Gradle artifact id가 `mysql`/`kafka`/`junit-jupiter`에서 `testcontainers-mysql`/`testcontainers-kafka`/`testcontainers-junit-jupiter`로 바뀜(옛 id는 버전이 빈 채로 에러)
- **남은 선택지**: (1) Testcontainers의 `KafkaContainer` "exited with code 1" 원인을 더 깊이 디버깅해서 Kafka까지 완성, (2) 지금 상태(MySQL만) 그대로 커밋하고 Kafka는 백로그에 남겨둠, (3) api/reservation/payment/review 등 나머지 서비스로 MySQL-Testcontainers 패턴 확대

---

## Elasticsearch 검색

### 배경

업체(Merchant)/리소스(Resource) 조회는 지금 MySQL 쿼리(+ Redis 캐싱)로만 처리된다. 이름/설명 기반 검색이나 다중 조건 필터링처럼 관계형 쿼리로는 번거로운 기능은 아직 없다. RDB + 검색엔진 이원화는 실무에서 흔한 조합이라 학습 가치가 크다.

### 해결 방향

- Merchant/Resource 데이터를 Elasticsearch에 색인(최초엔 Kafka 이벤트 없이 동기 이중 쓰기로 시작 가능, 이후 필요하면 CDC/이벤트 기반으로 발전)
- 업체명/리소스명 검색, 카테고리·가격대 필터링 등을 Elasticsearch 쿼리로 제공하는 신규 검색 엔드포인트 추가
- 기존 `GET /api/v1/merchants` 등 목록 조회는 그대로 두고, 검색은 별도 엔드포인트로 분리(Elasticsearch 장애가 기존 기능에 영향 주지 않도록)

### 적용 대상

reservation (Merchant/Resource 소유 서비스)

---

## Kafka 메시지 Avro + Schema Registry 전환

### 배경

지금 Kafka 이벤트(`user.created`/`user.updated`/`user.deleted`, `reservation.created`, `payment.completed`/`payment.failed`, `reservation.cancelled`)는 전부 `JsonSerializer`로 발행된다. 스키마가 코드(DTO)에만 존재해서, 프로듀서가 필드를 추가/변경/삭제해도 컨슈머가 역직렬화에 실패하는지는 배포 후 런타임에 가서야 드러난다.

### 해결 방향

- Confluent Schema Registry(또는 Apicurio Registry) 컨테이너를 docker-compose에 추가
- 이벤트별 `.avsc` 스키마 정의, 호환성 모드는 `BACKWARD`로 설정
- `KafkaAvroSerializer`/`KafkaAvroDeserializer`로 교체 — 프로듀서는 발행 전 레지스트리에 스키마를 등록/검증하고, 컨슈머는 메시지에 실린 스키마 ID로 레지스트리에서 스키마를 조회해 디코딩
- 기존 이벤트 중 하나(예: `reservation.created`)부터 먼저 전환해 파이프라인 검증 후 나머지로 확장

### 적용 대상

api, reservation, payment, notification (Kafka 발행/구독 전체)

---

## 대용량 엑셀 비동기 Export (Job + 진행률 알림)

### 배경

과거 통합(서버 렌더링, 세션 기반) 환경에서 대용량 엑셀 다운로드를 운영하며 겪은 문제: 중복 요청 시 리소스를 많이 잡아먹을까 봐 다운로드 버튼을 누르면 화면을 블러 처리해 재클릭을 막았는데, 정작 유저가 스피너만 계속 돌다가 응답 없이 멈추는 화면을 자주 마주쳤다. 원인은 한 가지가 아니라 "요청 하나를 끝까지 열어둔 채 기다리는(synchronous) 구조" 자체에 있었을 가능성이 크다:

- 전체 워크북을 메모리에 올리는 방식(`XSSFWorkbook`)이면 대용량에서 생성 자체가 오래 걸려 요청 스레드가 오래 묶임
- 리버스 프록시/게이트웨이의 기본 타임아웃(보통 30~60초)에 걸려 중간에 커넥션이 끊김 — 클라이언트는 깔끔한 에러 없이 "응답 없음"처럼 보임
- 세션 기반 인증이라면 생성이 오래 걸리는 동안 세션이 끊길 수도 있음

**중요한 전제 하나**: 이 문제는 프론트/백엔드 분리 여부와 무관하다 — 스트리밍 생성(`SXSSFWorkbook` 등) 자체는 과거 서버 렌더링 환경에서도 동일하게 가능했다. 분리 환경에서 실질적으로 달라지는 건 "프론트가 인증 헤더를 실어 보내야 하니 다운로드 트리거를 `fetch` + `Blob`으로 처리해야 한다"는 것 정도다. 또한 단순 스트리밍(요청 하나를 끝까지 열어두되 메모리만 적게 쓰는 방식)만으로는 위에서 겪은 "오래 걸리는 요청이 중간 어딘가에서 끊기는" 구조적 문제 자체는 해결되지 않는다 — 근본적으로는 무거운 작업을 요청/응답 생명주기에서 분리해야 한다.

### 해결 방향

요청-응답 동기 모델 대신, **Job 기반 비동기 처리**로 전환한다:

1. 다운로드 요청 시 즉시 Job을 생성하고 Job ID만 응답(202 Accepted 등) — 실제 무거운 작업은 백그라운드에서 수행
2. 실제 엑셀 생성은 **Spring Batch**(이미 있는 `batch` 모듈 재사용) — chunk 단위 처리라 진행 건수(읽음/처리/씀)가 `JobRepository`/`StepExecution`에 자연히 남는다. 엑셀 파일 자체는 Apache POI `SXSSFWorkbook`(또는 EasyExcel)으로 메모리에 전체를 올리지 않고 생성
3. 완료/진행 알림은 **SSE**로 실시간 push — `notification` 모듈에 이미 있는 `SseNotificationRegistry`(예약 확정/취소 알림과 동일한 메커니즘)를 재사용. 유저가 다운로드 요청 화면을 벗어나 다른 화면에 가 있어도 알림을 받을 수 있다(단순 스트리밍 방식으로는 애초에 불가능한 부분)
4. SSE를 놓치거나 브라우저를 껐다 켜도 찾을 수 있도록, 완료된 Job 목록을 조회하는 "다운로드 내역" API도 별도로 둔다 — 실시간 알림이 없어도 언제든 다시 와서 받을 수 있게
5. 생성된 파일 보관 방식은 별도 검토 필요 — 오브젝트 스토리지가 이 프로젝트엔 아직 없어서, 우선은 로컬 디스크에 임시 저장하고 내부 다운로드 엔드포인트로 서빙하는 것부터 시작 가능

### 미정 사항 (실제 구현 시 결정 필요)

- 어떤 데이터를 내보낼지(예: 업체 예약 목록, 업체 일별 통계) — 아직 정하지 않음
- Job 상태를 어느 서비스가 소유할지 (batch가 유력 — 이미 JobRepository가 있음)
- 완료된 파일을 어디에 보관할지 (로컬 디스크 임시 보관 vs 오브젝트 스토리지)

### 적용 대상

batch (Job 실행), notification (SSE 진행률/완료 알림), 데이터 소유 서비스(미정)

---

## AI 사용법 안내 챗봇 (LLM Gateway + 로컬/클라우드 모델)

### 배경

booking-web에 대화형 챗봇 UI를 추가해서, 사용자/관리자가 자연어로 시스템 사용법을 묻거나 자신의 예약·결제 상태를 조회할 수 있게 하고 싶다. 이 프로젝트는 AWS/Azure 같은 클라우드 환경이 없고 전부 로컬 docker-compose로 구성돼 있어서, 관리형 LLM 게이트웨이(Bedrock 등)는 선택지가 아니다. 대신 **실무에서 흔히 쓰는 self-hosted LLM 게이트웨이 패턴을 로컬에 그대로 구축**해서, 단순히 Claude API를 직접 호출하는 것보다 더 실제 운영에 가까운 구성을 학습 목표로 삼는다. 프론트 쪽 설계는 `booking-web/docs/99-backlog.md`에도 대응 항목을 적어둠.

### 해결 방향 — 전체 구조

```
booking-web (ChatWidget)
      ↓
api 서비스 (POST /api/v1/assistant/chat)
      ↓ (OpenAI 호환 /chat/completions 호출, provider는 "model" 값으로 선택)
LiteLLM Proxy (신규 docker-compose 서비스)
      ├── model="claude-..."  → Anthropic Claude API (클라우드)
      └── model="local-..."   → Ollama (신규 docker-compose 서비스, 로컬 오픈 웨이트 모델)
```

- **LiteLLM Proxy** 신규 docker-compose 서비스로 추가(self-hosted, OSS) — `config.yaml`에 모델별 provider 매핑을 정의. Anthropic API 키는 LiteLLM 설정에만 보관하고 `api`는 키를 아예 모른다(키 중앙화 — 실무 게이트웨이의 핵심 가치 중 하나)
- **Ollama** 신규 docker-compose 서비스로 추가, 오픈 웨이트 모델(예: Qwen2.5 또는 Llama 3.1 중 로컬 리소스에 맞는 크기) 1개를 서빙해서 LiteLLM 뒤에 두 번째 provider로 등록 — Apple Silicon 네이티브 arm64를 지원해서 `apache/kafka` 이미지에서 겪은 것 같은 아키텍처 문제는 없을 것으로 예상
- `api` 서비스는 Anthropic SDK를 직접 의존하지 않고, LiteLLM의 OpenAI 호환 엔드포인트만 호출 — 모델 이름만 바꾸면 Claude ↔ 로컬 모델 전환/fallback이 `api` 코드 변경 없이 가능(LiteLLM의 `fallbacks` 설정으로 처리)
- LiteLLM이 Prometheus/OTel로 메트릭·로그를 뽑을 수 있어서, 이미 구축된 **Prometheus/Grafana/Loki/Tempo** 스택으로 그대로 흘려보낸다 — 프롬프트/응답 로깅, 레이턴시, 토큰 사용량을 기존 관측 스택에서 관찰하는 것 자체가 이 백로그 항목의 핵심 학습 목표

### 해결 방향 — 1단계: 문서 기반 Q&A (스코프 작게 시작)

- **api 서비스**에 신규 엔드포인트 추가: `POST /api/v1/assistant/chat` (요청 `{ message, history? }`, 응답 `{ reply }`)
  - api를 선택한 이유: 이미 인증/회원 외에 관리자 메시지 발송(`/internal/messages` → notification RestClient 호출)처럼 범용 엔드포인트를 갖고 있어 자연스러운 위치. 전용 서비스로 분리하는 것도 가능하지만 1단계 스코프에서는 과함
- `api`가 LiteLLM을 거쳐 모델(Claude 또는 로컬 모델)을 호출. 시스템 프롬프트에 `docs/*.md` 요약(또는 발췌)을 컨텍스트로 정적 삽입 — RAG/임베딩 검색은 1단계에서 생략, 문서가 많아지면 2단계 이후 검토
- 대화 히스토리는 1단계에서 서버에 영속화하지 않음 — 프론트가 최근 턴들을 매 요청에 함께 보내는 stateless 방식
- 응답은 1단계에서 blocking(스트리밍 아님) — 프론트는 로딩 스피너로 대응

### 해결 방향 — 2단계: 실데이터 조회 (tool-use)

- tool-use(함수 호출)로 "내 예약 상태 조회", "결제 내역 조회" 등 tool 정의 — 로컬 모델은 Claude보다 tool-use 안정성이 떨어질 수 있어, 2단계는 우선 Claude 경로로 검증 후 로컬 모델에도 적용 검토
- api가 기존 RestClient 패턴(api → notification 호출과 동일한 방식)으로 reservation/payment의 기존 엔드포인트를 그대로 호출해 tool 결과를 받아옴 — 호출자의 JWT를 그대로 forward해서 각 서비스가 독립적으로 재검증(기존 서비스 간 인증 컨벤션 그대로 재사용, 신규 내부 엔드포인트 불필요)
- "왜 결제가 실패했어?" 류의 원인 분석형 질문은 이미 구축된 Loki + 트레이스 ID 연계를 활용해 관련 로그를 조회하는 tool을 추가 — 2단계 후순위

### 미정 사항 (실제 구현 시 결정 필요)

- 로컬 모델 선택(크기/리소스 요구사항) — 로컬 머신 사양에 맞춰 결정
- 대화 기록을 서버에 영속화할지, 한다면 어디에(현재는 생략하고 프론트 로컬 상태로만 유지)
- Claude API 키를 어떻게 LiteLLM 컨테이너에 주입할지(환경변수 vs `.env`) 및 레이트리밋/비용 제어 설정
- 전용 서비스로 분리할지 여부 — tool-use 대상이 여러 서비스에 걸치면서 api의 책임 범위가 커지면 재검토

### 적용 대상

api (신규 엔드포인트, LiteLLM 호출로 전환), 신규 docker-compose 서비스(LiteLLM, Ollama), reservation/payment(2단계 tool-use 대상 — 기존 엔드포인트 재사용, 변경 없음)

