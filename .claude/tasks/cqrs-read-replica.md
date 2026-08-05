# CQRS — MySQL Read Replica 기반 읽기/쓰기 분리

`docs/99-backlog.md` "CQRS" 항목의 구체화. **아직 구현 시작 전 — 설계 논의만 완료된 상태.**

## 결정 배경

CQRS의 read/write 모델 분리를 물리적으로 어떻게 나눌지 두 가지 옵션을 논의함:

1. **MySQL 복제(master-replica)** ← 채택
2. Kafka 이벤트 기반 프로젝션 (read 전용 비정규화 스키마를 이벤트로 채움)

처음엔 2번(이벤트 프로젝션)이 read 모델을 write와 다른 형태로 자유롭게 설계할 수 있어 "교과서적 CQRS"에 가깝다고 제안했으나, 사용자가 **"실무에서는 인프라(DB) 자체를 나누는 1번을 CQRS 목적으로 많이 쓴다"**는 이유로 1번을 선택. 실무 정합성을 우선한 결정이므로 그대로 따른다.

## 구현 범위 (아직 미착수)

### 1. 인프라 (docker-compose)

- **`booking-mysql` (source) 설정 변경**: binlog + GTID 활성화
  - `--log-bin=mysql-bin --gtid-mode=ON --enforce-gtid-consistency=ON --server-id=1`
  - 복제 전용 계정 추가 (`REPLICATION SLAVE` 권한)
  - ⚠️ **기존에 26시간+ 떠 있는 컨테이너를 재시작해야 적용됨** (볼륨 데이터는 유지, 컨테이너만 내려갔다 올라옴) — 재시작 여부는 사용자 확인 후 진행하기로 함, **아직 미승인**.
- **`mysql-replica` 컨테이너 신규 추가**
  - `--server-id=2 --gtid-mode=ON --enforce-gtid-consistency=ON --read-only=ON --super-read-only=ON`
  - `replicate-do-db=db_reservation` — db_reservation만 복제 대상 (전체 인스턴스 아님, CQRS 적용 대상이 reservation 도메인이므로)

### 2. 초기 데이터 동기화 (1회, 수동)

복제는 "설정 시점 이후" 변경분만 자동으로 흐른다. 이미 `booking-mysql`에 쌓여있는 기존 `db_reservation` 데이터는 복제 대상이 아니므로, `mysqldump`로 스냅샷 떠서 replica에 최초 1회 restore 필요. 이후 그 시점의 GTID 위치를 기준으로 `CHANGE REPLICATION SOURCE TO ...; START REPLICA;` 실행 — 이 시점부터는 자동 스트리밍.

### 3. reservation 서비스

- `AbstractRoutingDataSource` + `LazyConnectionDataSourceProxy`로 DataSource 2개 구성 (쓰기용 primary / 읽기용 replica)
- 라우팅 기준: 현재 트랜잭션이 `@Transactional(readOnly = true)`인지 여부 (Spring 표준 read/write splitting 패턴)
- 별도로 특정 쿼리만 골라 라우팅하지 않음 — 이미 `readOnly = true`로 선언된 기존 조회 메서드들(예약 목록, 캘린더 뷰 등)이 자동으로 replica로 감

## 예상 파일 목록

- `docker-compose.yml` — `booking-mysql` command 수정, `mysql-replica` 서비스 추가
- `docker/mysql/init/` — 복제 계정 생성 스크립트 추가 (source), replica 초기 동기화 스크립트/절차
- `reservation/src/main/java/.../config/DataSourceConfig.java` — 신규, primary/replica DataSource + RoutingDataSource 빈 등록
- `reservation/src/main/resources/application.yml` — replica datasource 접속 정보 추가

## 주의사항 / 학습 포인트

- **복제 지연(replication lag)**: 예약 생성 직후 바로 목록을 조회하면 방금 쓴 데이터가 replica에 아직 반영 안 돼 안 보일 수 있음. 버그가 아니라 이 패턴의 본질적인 트레이드오프 — read-after-write consistency가 필요한 화면(예: 예약 생성 직후 응답)은 원래 API 응답 자체(POST 응답)로 커버되므로 문제 없음.
- **재시작 필요 항목**: `booking-mysql`은 설정 변경 후 재시작 필요. 다른 서비스(api/payment/notification)도 같은 컨테이너를 쓰므로 재시작 중 짧은 단절 발생 가능 — 타이밍 고려.
- **범위**: `db_reservation`만 복제 대상. `db_api`/`db_payment`/`db_notification`은 이번 작업과 무관.

## 다음 액션

1. `booking-mysql` 재시작(설정 변경) 승인 받기
2. 승인되면 docker-compose 수정 → 컨테이너 재생성 → 복제 계정/GTID 확인
3. 초기 데이터 동기화 (dump & restore)
4. reservation 서비스 DataSource 라우팅 구현
5. `./gradlew build` + 실제 replica lag 시나리오로 동작 검증