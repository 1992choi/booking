# 99. 학습 시뮬레이션

각 시나리오는 특정 계정이나 데이터를 통해 장애/패턴을 재현한다.  
시뮬레이션 전용 계정 비밀번호는 모두 `12341234`.

---

## 시뮬레이션 계정

| 이메일 | 이름 | 역할 | 시나리오 |
|--------|------|------|----------|
| `error@bookit.com` | 에러테스터 | MERCHANT | Kafka Consumer Lag |
| `circuit@bookit.com` | 서킷테스터 | USER | 서킷브레이커 OPEN |
| `test@bookit.com` | 테스터 | USER | 레이스 컨디션 (동시성 오버셀링) |

---

## Kafka Consumer Lag

**계정:** `error@bookit.com`

**원인:** 컨슈머가 이벤트를 처리하는 도중 예외를 던지면 오프셋이 커밋되지 않는다. `KafkaConfig`에 설정한 `DefaultErrorHandler(FixedBackOff(5000, MAX_VALUE))`는 5초 간격으로 무한 재시도하므로, poison message가 하나라도 있으면 해당 파티션 전체가 멈추고 lag이 계속 쌓인다.

**재현 방법:**
1. `error@bookit.com`으로 로그인 후 음수 가격 상품(`-1000`)으로 예약 생성
2. `reservation.created` 이벤트 발행 → payment consumer 수신
3. `IllegalArgumentException("음수 금액")` 발생 → 오프셋 미커밋 → consumer lag 누적

**Lag 확인:**

```bash
docker exec booking-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payment-group \
  --describe
```

| 컬럼 | 의미 |
|------|------|
| `CURRENT-OFFSET` | 컨슈머가 마지막으로 커밋한 오프셋. `-`이면 아직 한 번도 커밋되지 않음 |
| `LOG-END-OFFSET` | 브로커에 쌓인 마지막 오프셋 |
| `LAG` | 두 값의 차이. `CURRENT-OFFSET`이 `-`이면 계산 불가로 `-` 표시 |

**토픽에 쌓인 이벤트 내용 확인:**

```bash
docker exec booking-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic reservation.created \
  --from-beginning
```

**문제 이벤트 건너뛰기 (오프셋 리셋):**

컨슈머가 활성 상태이면 리셋이 불가하므로 **payment 서비스를 먼저 중지**한다.

```bash
docker exec booking-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group payment-group \
  --topic reservation.created \
  --reset-offsets \
  --to-offset 1 \
  --execute
```

`--to-offset N`에 건너뛰고 싶은 이벤트의 다음 오프셋을 지정한다 (오프셋은 0-based).  
실행 후 payment 서비스를 재시작하면 지정한 오프셋부터 consume을 재개한다.

> 레코드는 브로커에 그대로 남는다. 오프셋만 이동시켜 컨슈머가 읽지 않도록 한다.

---

## 서킷브레이커 OPEN

**계정:** `circuit@bookit.com`

**재현 방법:**
1. ADMIN 계정(`admin@bookit.com`)으로 로그인
2. `circuit@bookit.com`의 userId로 반복 요청
3. notification 서비스가 해당 userId에 대해 항상 500 반환
4. 5번 중 3번(60%) 이상 실패 시 서킷 OPEN

**서킷브레이커 설정 (api 서비스 — `notification` 인스턴스)**

| 항목 | 값 |
|------|----|
| sliding-window-size | 5 |
| failure-rate-threshold | 60% |
| wait-duration-in-open-state | 10s |
| permitted-calls-in-half-open | 2 |

**관찰 포인트:**
- OPEN 전: notification 서비스 로그에 `[CHAOS]` 경고 출력
- OPEN 후: api 서비스가 notification에 요청을 보내지 않고 즉시 503 (`API_004`) 반환
- 10초 후 HALF-OPEN 전환 → 정상 userId로 2번 성공하면 CLOSED 복귀

---

## 레이스 컨디션 (동시성 오버셀링)

**계정:** `test@bookit.com`

**대상 리소스:** `03-seed-data.sql`에서 생성되는 "레이스 컨디션 테스트 룸" (`max_capacity=10`, 슬롯 1개).

**배경:** `docs/99-backlog.md`의 "동시성 보강" 항목(Redis 분산락, DB 비관적락)이 아직 미적용 상태라, 현재는 `validateSlots`의 overlap 쿼리만으로 정원(10명)을 방어하고 있다. 대량 동시 예약 요청을 쏴서 정원을 초과한 예약(오버셀링)이 생성되는지 확인한다.

**k6 설치 (macOS):**

```bash
brew install k6
```

**실행:**

```bash
k6 run -e VUS=500 -e ITERATIONS=50000 load-test/k6/race-condition.js
```

| 옵션 | 기본값 | 의미 |
|------|--------|------|
| `VUS` | 200 | 동시 가상 유저 수 |
| `ITERATIONS` | 50000 | 전체 요청(예약 시도) 건수 |
| `API_BASE_URL` | `http://localhost:8080` | 로그인용 api 서비스 |
| `RESERVATION_BASE_URL` | `http://localhost:8081` | 예약 생성용 reservation 서비스 |

**관찰 포인트:**
- `reservations` 테이블에서 해당 리소스로 생성된 행 개수를 확인. 10개를 초과하면 오버셀링 버그가 재현된 것.

```sql
SELECT COUNT(*) FROM db_reservation.reservations WHERE resource_id = (
    SELECT id FROM db_reservation.resources WHERE name = '레이스 컨디션 테스트 룸'
);
```