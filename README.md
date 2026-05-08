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

### 2. 인프라 기동 (MySQL)

```bash
docker compose up -d
```

- `booking-mysql` 컨테이너가 3306 포트로 떠 있음
- 기본 DB: `api_db` / 계정: `root` / `root`
- 데이터는 `booking-mysql-data` 볼륨에 영속

상태 확인:
```bash
docker compose ps
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