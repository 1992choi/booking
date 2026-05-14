# Booking

범위 기반 예약 플랫폼 (MSA). 4개 서비스(`api`, `reservation`, `payment`, `notification`) + 공통 라이브러리 `core`로 구성.

설계 문서는 [`docs/`](./docs) 참고. 시작점: [`docs/01-overview.md`](./docs/01-overview.md).

---

## 프로젝트 설정

### 1. 사전 요구사항

| 항목 | 버전 |
| --- | --- |
| Java | 25 |
| Docker / Docker Compose | 최신 |

### 2. 인프라 기동 (MySQL + Kafka)

```bash
docker compose up -d
```

| 컨테이너 | 포트 | 비고 |
|----------|------|------|
| `booking-mysql` | 3306 | 계정 `root` / `root` |
| `booking-kafka` | 9092 | KRaft 모드 (단일 브로커) |

초기화 시 `db_api`, `db_reservation`, `db_payment`, `db_notification` 4개 DB 자동 생성. 데이터는 `booking-mysql-data` 볼륨에 영속.

상태 확인:
```bash
docker compose ps
```

DB 초기화가 필요할 때:
```bash
docker compose down -v   # 볼륨까지 삭제 후 재기동
docker compose up -d
```

### 3. 빌드

```bash
./gradlew build
```

### 4. 서비스 기동

각 서비스는 별도 포트로 동작.

| 서비스 | 명령 | 포트 |
|--------|------|------|
| api | `./gradlew :api:bootRun` | 8080 |
| reservation | `./gradlew :reservation:bootRun` | 8081 |
| payment | `./gradlew :payment:bootRun` | 8082 |
| notification | `./gradlew :notification:bootRun` | 8083 |

### 5. 정리

```bash
docker compose down          # 컨테이너만 제거 (데이터 유지)
docker compose down -v       # 볼륨까지 삭제 (DB 초기화)
```