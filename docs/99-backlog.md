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

## 관찰가능성 스택 고도화 (Grafana)

현재 Zipkin(분산추적) + Prometheus(메트릭)까지 구성된 상태. Loki + Tempo + Grafana를 추가해 메트릭/로그/트레이스를 단일 화면에서 연결하여 볼 수 있도록 고도화.

- ~~Prometheus: 각 서비스 메트릭 수집~~ — **완료.** `docker-compose.yml`의 `prometheus` 컨테이너가 `docker/prometheus/prometheus.yml` 스크랩 설정으로 api/reservation/payment/notification의 `/actuator/prometheus`를 수집 (`micrometer-registry-prometheus` 추가 + `management.endpoints.web.exposure.include: health,prometheus` + SecurityConfig에 `/actuator/**` permitAll 필요했음). batch/pg/review는 actuator 자체가 없어 대상 아님.
- ~~Grafana: 메트릭/로그/트레이스 통합 대시보드~~ — **완료.** `docker-compose.yml`의 `grafana` 컨테이너가 `docker/grafana/provisioning/datasources/prometheus.yml`로 Prometheus 데이터소스를 자동 프로비저닝. `localhost:3000` (admin/admin). Loki/Tempo 연동 전까지는 Prometheus 메트릭만 조회 가능.
- Loki: 로그 수집 (Promtail 또는 Loki Logback Appender). Grafana Explore로 조회.
- Tempo: Zipkin 대체 분산추적 백엔드 (OTel exporter를 OTLP로 교체)
- docker-compose에 위 3개 컨테이너 추가

---

## CQRS

예약 조회(Read)와 생성/취소(Write) 모델 분리. 현재는 동일 엔티티로 읽기/쓰기를 모두 처리.

- Write 모델: 기존 JPA 엔티티 유지
- Read 모델: 조회 전용 DTO/Repository 분리 (QueryDSL 또는 별도 Read DB)
- 적용 대상: 예약 목록 조회, 업체별 캘린더 뷰 — 조회 빈도가 높고 Write와 요구사항이 달라 분리 효과가 큼
