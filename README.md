# Booking

범위 기반 예약 플랫폼 (MSA). 4개 서비스(`api`, `reservation`, `payment`, `notification`) + 공통 라이브러리 `core`, 그리고 학습/운영 보조 목적의 `review`(Kotlin) · `batch` · `pg`(Mock PG) 모듈로 구성.

설계 문서는 [`docs/`](./docs) 참고. 시작점: [`docs/01-overview.md`](./docs/01-overview.md).

---

## 개발 플로우

Claude Code를 활용한 기능 개발은 아래 순서로 진행한다.

```
/design <기능명>        # 관련 docs 분석 → .claude/tasks/{기능}.md 에 설계 기록
/impl <기능명>          # 설계 파일 기반 구현 + 빌드 확인
/convention-check       # CLAUDE.md 컨벤션 위반 검사
/test <기능명>          # 테스트 코드 작성 → 개별 실행 → clean 전체 실행
/update-docs            # 변경된 코드 기준으로 docs/ 현행화
```

skill 파일은 `.claude/skills/`, 설계 산출물은 `.claude/tasks/` 에 위치한다.

---

## 프로젝트 설정

### 1. 사전 요구사항

| 항목 | 버전 |
| --- | --- |
| Java | 25 |
| Docker / Docker Compose | 최신 |

### 2. 인프라 기동

Grafana가 Slack 알림 웹훅을, LiteLLM이 Claude 호출용 Anthropic API 키를 필요로 하므로, 먼저 `.env`를 만든다.

```bash
cat <<'EOF' > .env
SLACK_WEBHOOK_URL=<발급받은 웹훅 URL>
ANTHROPIC_API_KEY=<발급받은 Anthropic API 키>
EOF
```

`SLACK_WEBHOOK_URL` 발급 방법:
1. https://api.slack.com/apps → **Create New App** → **From scratch** → 워크스페이스 선택
2. 왼쪽 메뉴 **Incoming Webhooks** → 상단 토글 ON
3. **Add New Webhook to Workspace** → 알림 받을 채널 선택 → **Allow**
4. 생성된 `https://hooks.slack.com/services/...` URL을 `.env`에 붙여넣기

`ANTHROPIC_API_KEY`는 https://console.anthropic.com/settings/keys 에서 발급.

```bash
docker compose up -d
```

| 컨테이너 | 포트 | 비고 |
|----------|------|------|
| `booking-mysql` | 3306 | 계정 `root` / `root` |
| `booking-kafka` | 9092 | KRaft 모드 (단일 브로커) |
| `booking-redis` | 6379 | |
| `booking-mongodb` | 27017 | 인증 없음(개발용). api/reservation의 감사 로그(`db_api_audit`/`db_reservation_audit`) 전용 |
| `booking-tempo` | 3200, 4317, 4318 | 분산추적 수집기(OTLP gRPC/HTTP). 조회는 Grafana Explore에서 |
| `booking-loki` | 3100 | 로그 수집기. api/reservation/payment/notification이 `logback-spring.xml`의 Loki4j appender로 JSON 구조화 로그(`{timestamp_ms, logger_name, level, thread_name, message, ...}`)를 직접 push. 조회는 Grafana Explore 또는 `\| json` 파이프라인으로 필드 필터링 |
| `booking-prometheus` | 9090 | 메트릭 수집 UI. `docker/prometheus/prometheus.yml`에서 api/reservation/payment/notification/batch의 `/actuator/prometheus`를 `host.docker.internal`로 스크랩 (pg/review는 actuator 미적용이라 대상 아님) |
| `booking-grafana` | 3000 | 대시보드 UI. 계정 `admin` / `admin`. `docker/grafana/provisioning/datasources/`로 Prometheus/Loki/Tempo 데이터소스 자동 연결. `docker/grafana/provisioning/alerting/`로 Slack 알림(5xx 발생 시) 자동 구성 |
| `booking-ollama` | 11434 | 로컬 LLM 서빙. 최초 기동 후 모델을 수동으로 받아야 함(아래 참고) |
| `booking-litellm` | 4000 | LLM Gateway. `docker/litellm/config.yaml`에 `claude`(Anthropic) / `local`(Ollama) 두 모델을 등록, `claude` 실패 시 `local`로 fallback. `api` 서비스가 이 게이트웨이만 호출(Anthropic API 키를 직접 들고 있지 않음) |

Ollama는 이미지에 모델이 내장돼 있지 않아 최초 한 번 받아야 한다:
```bash
docker exec booking-ollama ollama pull qwen2.5:7b
```

초기화 시 `db_api`, `db_reservation`, `db_payment`, `db_notification` 4개 DB(빈 스키마)가 자동 생성된다. 데이터는 `booking-mysql-data` 볼륨에 영속.

테이블은 더 이상 이 초기화 스크립트가 만들지 않는다 — 각 서비스(`api`/`reservation`/`payment`/`notification`)가 Flyway로 직접 관리하며, 서비스를 처음 기동(`bootRun` 또는 테스트 실행)하는 시점에 `src/main/resources/db/migration/V1__init.sql`을 실행해 자기 DB의 테이블을 만든다. 그래서 완전히 새로 받은 환경이라면 **4개 서비스를 한 번씩 기동(또는 `./gradlew build`로 테스트를 한 번 돌려도 됨)해야 테이블이 생긴 뒤** 아래 시드 데이터를 넣을 수 있다.

시드 데이터(데모/시뮬레이션용 계정 — `docs/99-simulations.md` 참고) 적용:
```bash
mysql -h 127.0.0.1 -P 3306 -uroot -proot < docker/mysql/03-seed-data.sql
```
`docker-entrypoint-initdb.d`(= `docker/mysql/init/`) 밖에 있어 컨테이너 최초 기동 시 자동 실행되지 않는다 — 테이블이 이미 있어야 하는 스크립트라 수동으로, 그것도 한 번만 실행하면 된다(재실행하면 `UNIQUE` 제약 위반으로 실패).

MongoDB 접속(mongosh):
```bash
docker exec -it booking-mongodb mongosh
# 또는 호스트에 mongosh가 설치돼 있다면
mongosh "mongodb://localhost:27017/db_reservation_audit"
```

감사 로그 조회 (mongosh 접속 후, 서비스별로 DB가 분리돼 있음 — `db_reservation_audit` 또는 `db_api_audit`):
```text
use db_reservation_audit

// 최근 기록 10건
db.audit_logs.find().sort({ createdAt: -1 }).limit(10)

// 특정 액션만 (reservation: RESERVATION_CREATED/RESERVATION_CANCELLED, api: LOGIN)
db.audit_logs.find({ action: "RESERVATION_CREATED" })

// 특정 유저의 활동만
db.audit_logs.find({ userId: 1 })

// 전체 건수
db.audit_logs.countDocuments()
```

**Grafana → Slack 알림 동작 방식**: Spring이 Grafana로 에러를 보내는 단계는 없다 — 체인의 유일한 push는 마지막 Grafana→Slack 한 구간뿐이고, 나머지는 전부 주기적으로 "가서 물어보는"(pull) 방식이다.
1. Spring/Micrometer가 `http_server_requests_seconds_count{status=...}` 카운터를 `/actuator/prometheus`에 노출(수동적, 아무 데도 안 보냄)
2. Prometheus가 15초마다 이 값을 스크랩해서 자기 DB에 저장
3. Grafana 알럿 룰이 1분마다 Prometheus에 직접 쿼리를 날려 임계치 초과 여부를 스스로 판단(`firing`으로 전이) — "이상하다"는 판단은 Grafana가 내림
4. Notification Policy가 Contact Point(Slack)로 라우팅, Contact Point가 Slack Incoming Webhook URL로 HTTP POST(체인의 유일한 push)

Contact Point/Notification Policy/Alert Rule은 `docker/grafana/provisioning/alerting/*.yaml`로 선언돼 있다. **파일명은 무관** — Grafana는 해당 디렉토리의 모든 `.yaml`을 열어 최상위 키(`contactPoints:`/`policies:`/`groups:`)로 종류를 판단한다.

Slack 알림 테스트 (api 서비스를 띄운 상태에서):
```bash
# 인증 없이 강제 500을 발생시키는 테스트 전용 엔드포인트
curl http://localhost:8080/api/v1/test/error500
```
Prometheus가 5xx 증가를 스크랩(최대 15초)한 뒤 Grafana 알럿 룰(`HTTP 5xx 발생`, 1분 주기 평가)이 `firing`으로 전환되면 Slack으로 알림이 온다. `http://localhost:3000/alerting/list`에서 룰 상태를 바로 확인할 수 있다.

**AI 어시스턴트 챗봇 동작 방식**: `POST /api/v1/assistant/chat`에 질문을 보내면 내부적으로 DB를 "검색"하는 게 아니라, Claude에게 그때그때 필요한 텍스트를 쥐여주고 "이어서 자연스럽게 답을 써봐"라고 시키는 것에 가깝다. 두 단계로 나뉜다.

**1) 기본 답변 — 그냥 쥐여준 텍스트를 읽고 답함**
1. `api`가 `src/main/resources/assistant/system-prompt.txt`(사용법 안내를 사람이 미리 적어둔 고정 텍스트)를 모든 요청에 매번 통째로 붙인다
2. 이 텍스트 + 사용자의 질문을 LiteLLM(`:4000`, OpenAI 호환 API)에 보냄
3. LiteLLM은 그 자체로는 아무 지능이 없는 **중계 서버**다 — `docker/litellm/config.yaml`에 적힌 라우팅 규칙대로 Anthropic Claude API(클라우드)나 로컬 Ollama 모델로 요청을 그대로 전달할 뿐
4. Claude가 system-prompt 내용을 바탕으로 질문에 대한 답을 생성해서 돌려줌
5. Claude는 대화를 "기억"하지 않는다 — 다음 질문에도 매번 처음부터 같은 system-prompt + 이전 대화 내용을 전부 다시 보내야 한다(그래서 프론트가 `history`를 매번 함께 보냄)

이 단계만으로는 "예약 취소는 어떻게 해?" 같은 **일반적인 사용법**만 답할 수 있다 — system-prompt에 그 사용자 개인의 실시간 데이터(내 예약 상태 등)는 당연히 없기 때문.

**2) 실데이터 조회 — tool-use (Claude가 "직접 찾아보게" 시킴)**
"내 예약 상태 알려줘"처럼 개인 데이터가 필요한 질문엔, Claude에게 호출 가능한 함수 목록(`tools`)도 같이 알려준다. 흐름:
1. Claude가 질문을 보고 "이건 직접 답할 수 없고 조회가 필요하다" 판단하면, 텍스트 답 대신 "이 함수를 이 값으로 호출해줘"라는 요청(`tool_calls`)을 돌려줌
2. `api`(`AssistantService`)가 그 요청을 받아서 실제로 함수를 실행 — 예를 들어 `get_my_reservations`는 reservation 서비스의 `GET /reservations/me`를 호출자의 JWT로 그대로 호출
3. 실행 결과를 다시 Claude에게 돌려주면, Claude가 그 결과를 보고 최종적으로 사람이 읽을 답을 생성
4. 필요하면 이 왕복을 몇 번 더 반복할 수 있음(최대 3회)

"예약이 안 됐어" 같은 원인 진단 질문은 `find_recent_reservation_error` tool을 쓰는데, 이건 더 안전하게 설계돼 있다 — Claude에게 서버 로그 전체를 읽게 하는 게 아니라, `api`가 Loki에서 **이미 정해진 유한한 에러 코드**(`code=RSV_001` 등, `core`의 `GlobalExceptionHandler`가 항상 이 형식으로 로깅)만 뽑아서 "이 코드의 뜻 하나만" Claude에게 건넨다. Claude는 그 정해진 뜻을 자연스러운 문장으로 바꾸는 역할만 하므로, 로그를 잘못 해석해서 지어낼 위험이 거의 없다. 이 조회는 호출자 본인의 `userId`로만 범위가 좁혀진다(다른 사용자의 실패 기록은 안 보임).

**3) ADMIN 전용 — CS 상담용 "진짜 직접 조회" tool**

위 두 tool은 전부 파라미터가 없고 범위가 호출자 본인으로 고정돼 있어 안전하지만, 그만큼 "정해진 것만" 답할 수 있다. 사내 CS 봇처럼 "관리자가 다른 사용자의 문제를 조사"하는 용도로는 이 정도로는 부족해서, ADMIN 역할에게만 두 tool을 추가로 열어줬다:
- `run_sql`: Claude가 **직접 SELECT SQL을 작성**해서 DB를 조회(쓰기 쿼리는 실행 전에 거부). 서비스별로 DB가 분리돼 있어 `db_reservation.reservations`처럼 스키마를 붙여서 조회
- `run_logql`: Claude가 **직접 LogQL을 작성**해서 Loki를 조회, 범위 제한 없음(어떤 서비스·어떤 사용자·몇 분 전까지든 자유롭게)

핵심은 "USER용 tool 목록"과 "ADMIN용 tool 목록"이 요청마다 다르다는 것이다 — `AssistantService`가 호출자의 role을 보고, ADMIN이 아니면 `run_sql`/`run_logql` 자체를 LiteLLM에 전달하는 tool 목록에서 빼버린다. 그래서 일반 사용자가 질문해도 Claude는 그런 tool이 **존재한다는 사실조차 모른다** — "권한이 없어서 거부"가 아니라 "그런 기능 자체를 모르는" 상태다.

**정리**: `Claude`는 지능(글을 자연스럽게 잇는 능력)을, `LiteLLM`은 그 지능을 어느 provider(클라우드/로컬)에서 가져올지 결정하는 중계를, `api`는 "이 질문엔 어떤 자료가 필요한지" + "이 호출자에게 어떤 tool까지 보여줄지" 판단해서 실제로 그 자료(고정 텍스트든, tool 호출 결과든)를 만들어 Claude에게 넘겨주는 역할을 각각 맡는다.

상태 확인:
```bash
docker compose ps
```

전체 초기화 (볼륨까지 삭제 후 재기동):
```bash
docker compose down -v && docker compose up -d
```
볼륨을 지웠다면 테이블도 전부 사라진 상태 — 위에서 설명한 대로 4개 서비스를 한 번씩 기동(Flyway가 재생성)한 뒤 시드 데이터를 다시 넣어야 한다.

### 3. 빌드

```bash
./gradlew build
```

### 4. 서비스 기동

각 서비스는 별도 포트로 동작. `batch`는 REST API는 없고 `@Scheduled`로 Job을 실행하지만, `/actuator/prometheus` 노출을 위해 최소한의 HTTP(actuator 전용)는 띄운다.

| 서비스 | 명령 | 포트 |
|--------|------|------|
| api | `./gradlew :api:bootRun` | 8080 |
| reservation | `./gradlew :reservation:bootRun` | 8081 |
| payment | `./gradlew :payment:bootRun` | 8082 |
| notification | `./gradlew :notification:bootRun` | 8083 |
| review | `./gradlew :review:bootRun` | 8084 |
| batch | `./gradlew :batch:bootRun` | 8085 (actuator 전용, REST API 없음) |
| pg (Mock PG) | `./gradlew :pg:bootRun` | 8090 (REST), 50051 (gRPC) |

### 5. 로컬 접속 URL

| 용도 | URL |
|------|-----|
| api 서비스 | http://localhost:8080 |
| reservation 서비스 | http://localhost:8081 |
| payment 서비스 | http://localhost:8082 |
| notification 서비스 | http://localhost:8083 |
| review 서비스 | http://localhost:8084 |
| Mock PG 서버 | http://localhost:8090 |
| Prometheus (메트릭) | http://localhost:9090 |
| Grafana (대시보드) | http://localhost:3000 |
| Swagger UI (api) | http://localhost:8080/swagger-ui.html |
| Swagger UI (reservation) | http://localhost:8081/swagger-ui.html |
| Swagger UI (payment) | http://localhost:8082/swagger-ui.html |
| Swagger UI (notification) | http://localhost:8083/swagger-ui.html |

**Swagger UI 사용법**
1. 위 URL 접속 (인증 불필요, 각 서비스가 떠 있어야 함)
2. 인증이 필요한 API는 `api`의 `/api/v1/auth/login`으로 먼저 토큰 발급
3. Swagger UI 우측 상단 **Authorize** 버튼 클릭 → `accessToken` 값만 입력(`Bearer` 접두사는 자동으로 붙음) → **Authorize**
4. 이후 각 엔드포인트의 **Try it out** → **Execute**로 직접 호출 가능. `Authorization` 헤더가 자동으로 실려서 나감

---

## 기술 스택

공통(core 라이브러리, 모든 서비스에 임베드): Spring Boot 4 · Java 25 · JWT(jjwt) 발급/검증 · Micrometer Tracing + OpenTelemetry OTLP exporter(분산추적).

| 기술 | 용도 | 적용 모듈 |
|------|------|-----------|
| Spring Data JPA | ORM | api, reservation, payment, notification, batch, review |
| QueryDSL | 동적 쿼리, 겹침 조회 시 비관적 락(PESSIMISTIC_WRITE) | reservation |
| Kafka (spring-kafka) | 서비스 간 비동기 이벤트 | api, reservation, payment, notification |
| Redis + Bucket4j | 로그인 요청 rate limit | api |
| Redis + Redisson | 예약 생성 시 분산 락(오버셀링 방지) | reservation |
| Spring Cache + Redis | 업체 조회 캐싱 | reservation |
| Resilience4j | notification 호출 재시도 + 서킷브레이커 | api |
| Spring Batch | 예약 만료 처리 · 업체 일별 통계 집계 배치 | batch |
| Kotlin + Spring Boot | 업체 리뷰 기능 (학습용) | review |
| MySQL | 서비스별 DB (database-per-service). batch/review 는 db_reservation 공유 | api, reservation, payment, notification, batch, review |
| Tempo | 분산 트레이싱 수집(OTLP), Grafana Explore에서 조회 | 전 서비스 |
| Prometheus + Micrometer | 메트릭 수집(`/actuator/prometheus`). batch는 업체 일별 통계 집계 결과를 `merchant_daily_reservation_count`/`merchant_daily_revenue` 커스텀 Gauge로도 노출 | api, reservation, payment, notification, batch |
| Grafana | 메트릭 대시보드 (Prometheus 데이터소스 연동) + Alerting(5xx 발생 시 Slack 알림) | api, reservation, payment, notification |
| MongoDB (Spring Data MongoDB) | 사용자 활동 감사 로그(`audit_logs`) | api, reservation |
| gRPC + Protocol Buffers | payment → pg 거래 승인/취소 (REST 병행, `booking.pg.protocol`로 전환) | payment, pg |
| springdoc-openapi (Swagger UI) | 실행 중인 코드 기준 API 문서/테스트 UI (`/swagger-ui.html`, `/v3/api-docs`) | api, reservation, payment, notification |
| Virtual Threads (`spring.threads.virtual.enabled`) | Tomcat 요청 처리 스레드를 가상 스레드로 전환(블로킹 I/O가 많은 구조라 스레드 수 제약 완화 효과) | api, reservation, payment, notification |
| SSE (`SseEmitter`) | 결제 완료/취소/관리자 메시지를 클라이언트에 실시간 push(`GET /api/v1/notifications/stream`) | notification |