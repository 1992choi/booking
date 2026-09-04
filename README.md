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

Grafana가 Slack 알림 웹훅을 필요로 하므로, 먼저 `.env`를 만든다.

```bash
echo "SLACK_WEBHOOK_URL=<발급받은 웹훅 URL>" > .env
```

`SLACK_WEBHOOK_URL` 발급 방법:
1. https://api.slack.com/apps → **Create New App** → **From scratch** → 워크스페이스 선택
2. 왼쪽 메뉴 **Incoming Webhooks** → 상단 토글 ON
3. **Add New Webhook to Workspace** → 알림 받을 채널 선택 → **Allow**
4. 생성된 `https://hooks.slack.com/services/...` URL을 `.env`에 붙여넣기

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
| `booking-prometheus` | 9090 | 메트릭 수집 UI. `docker/prometheus/prometheus.yml`에서 api/reservation/payment/notification의 `/actuator/prometheus`를 `host.docker.internal`로 스크랩 (batch/pg/review는 actuator 미적용이라 대상 아님) |
| `booking-grafana` | 3000 | 대시보드 UI. 계정 `admin` / `admin`. `docker/grafana/provisioning/datasources/`로 Prometheus/Loki/Tempo 데이터소스 자동 연결. `docker/grafana/provisioning/alerting/`로 Slack 알림(5xx 발생 시) 자동 구성 |

초기화 시 `db_api`, `db_reservation`, `db_payment`, `db_notification` 4개 DB 자동 생성. 데이터는 `booking-mysql-data` 볼륨에 영속.

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

상태 확인:
```bash
docker compose ps
```

전체 초기화 (볼륨까지 삭제 후 재기동):
```bash
docker compose down -v && docker compose up -d
```

### 3. 빌드

```bash
./gradlew build
```

### 4. 서비스 기동

각 서비스는 별도 포트로 동작. `batch`는 HTTP 를 노출하지 않고 스케줄러로만 동작한다.

| 서비스 | 명령 | 포트 |
|--------|------|------|
| api | `./gradlew :api:bootRun` | 8080 |
| reservation | `./gradlew :reservation:bootRun` | 8081 |
| payment | `./gradlew :payment:bootRun` | 8082 |
| notification | `./gradlew :notification:bootRun` | 8083 |
| review | `./gradlew :review:bootRun` | 8084 |
| pg (Mock PG) | `./gradlew :pg:bootRun` | 8090 (REST), 50051 (gRPC) |
| batch | `./gradlew :batch:bootRun` | (HTTP 없음, `@Scheduled` 배치 실행) |

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
| Prometheus + Micrometer | 메트릭 수집(`/actuator/prometheus`) | api, reservation, payment, notification |
| Grafana | 메트릭 대시보드 (Prometheus 데이터소스 연동) + Alerting(5xx 발생 시 Slack 알림) | api, reservation, payment, notification |
| MongoDB (Spring Data MongoDB) | 사용자 활동 감사 로그(`audit_logs`) | api, reservation |
| gRPC + Protocol Buffers | payment → pg 거래 승인/취소 (REST 병행, `booking.pg.protocol`로 전환) | payment, pg |