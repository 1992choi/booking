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

```bash
docker compose up -d
```

| 컨테이너 | 포트 | 비고 |
|----------|------|------|
| `booking-mysql` | 3306 | 계정 `root` / `root` |
| `booking-kafka` | 9092 | KRaft 모드 (단일 브로커) |
| `booking-redis` | 6379 | |
| `booking-tempo` | 3200, 4317, 4318 | 분산추적 수집기(OTLP gRPC/HTTP). 조회는 Grafana Explore에서 |
| `booking-prometheus` | 9090 | 메트릭 수집 UI. `docker/prometheus/prometheus.yml`에서 api/reservation/payment/notification의 `/actuator/prometheus`를 `host.docker.internal`로 스크랩 (batch/pg/review는 actuator 미적용이라 대상 아님) |
| `booking-grafana` | 3000 | 대시보드 UI. 계정 `admin` / `admin`. `docker/grafana/provisioning/datasources/`로 Prometheus/Loki/Tempo 데이터소스 자동 연결 |

초기화 시 `db_api`, `db_reservation`, `db_payment`, `db_notification` 4개 DB 자동 생성. 데이터는 `booking-mysql-data` 볼륨에 영속.

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
| pg (Mock PG) | `./gradlew :pg:bootRun` | 8090 |
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
| Grafana | 메트릭 대시보드 (Prometheus 데이터소스 연동) | api, reservation, payment, notification |