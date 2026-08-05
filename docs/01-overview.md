# 01. 프로젝트 개요 & 요구사항

## 프로젝트 개요

특정 시간대를 예약하는 범용 예약 플랫폼 엔진.
프론트엔드만 교체하면 숙박, 강의, 시설, 상담 등 다양한 도메인에 적용 가능.

MSA 구조로 설계되며, 4개의 핵심 도메인 서비스(api/reservation/payment/notification) + 1개의 공통 라이브러리(core)를 중심으로 구성된다. 여기에 배치 전용 `batch`, Mock PG `pg`, 학습용 리뷰 모듈 `review` 가 추가로 독립 배포된다 (`docs/02-architecture.md` 참고).

---

## 기능 요구사항

### 인증 (api 서비스)
- 사용자는 이메일/비밀번호로 회원가입 및 로그인할 수 있다
- JWT 기반 인증을 사용한다
- Access Token 만료 시 Refresh Token 으로 갱신할 수 있다

### 업체(Merchant) (reservation 서비스)
- 업체를 등록, 수정, 조회할 수 있다
- 한 사용자가 여러 업체를 등록할 수 있다 (1:N)
- 업체 타입은 PENSION / CLASS / FACILITY 이다

### 예약 대상(Resource) (reservation 서비스)
- 업체는 예약 대상(객실, 수업 등)을 등록, 수정, 삭제할 수 있다
- 예약 대상에 예약 가능한 시간대를 등록할 수 있다

### 예약 (reservation 서비스)
- 사용자는 특정 예약 대상의 시간대를 예약할 수 있다
- 동일 시간대 중복 예약은 허용하지 않는다
- 예약 상태는 PENDING → CONFIRMED → (CANCELLED) 순서로 전이된다
- 사용자는 자신의 예약 목록을 조회할 수 있다
- 사용자는 예약을 취소할 수 있다

### 결제 (payment 서비스)
- 예약 완료 시 결제가 자동으로 요청된다 (Mock)
- 결제 상태는 PENDING → COMPLETED / FAILED 로 전이된다
- 결제 실패 시 예약은 CANCELLED 처리된다 (Kafka 이벤트로 전파)
- 환불 요청이 가능하다

### 알림 (notification 서비스)
- 예약 확정 시 사용자에게 알림을 발송한다 (Mock)
- 예약 취소 시 사용자에게 알림을 발송한다 (Mock)
- 알림 발송 이력을 저장한다

### 관리 (reservation 서비스)
- 업체 관리자는 전체 예약 현황을 조회할 수 있다
- 업체 관리자는 예약을 수동으로 확정 또는 취소할 수 있다
- 캘린더 형태로 예약 현황을 조회할 수 있다
- 업체별 일별 예약 통계(확정/취소 건수, 매출)를 조회할 수 있다 (batch 모듈이 매일 새벽 집계)

### 리뷰 (review 모듈, 학습용)
- 사용자는 본인이 `CONFIRMED` 상태로 완료한 예약에 대해 텍스트 리뷰(별점 없음)를 작성할 수 있다
- 예약 1건당 리뷰는 1개만 작성할 수 있다
- 작성자 본인만 리뷰를 수정/삭제할 수 있다
- 누구나 업체별 리뷰 목록을 인증 없이 조회할 수 있다

### 운영 자동화 (batch 모듈)
- 설정된 시간(기본 10분) 이상 `PENDING` 상태로 남은 예약은 자동으로 `CANCELLED` 처리된다
- 업체별 일별 예약 통계를 매일 새벽 1시에 집계한다

---

## 비기능 요구사항

### 동시성
- 동일 시간대에 다수의 예약 요청이 들어와도 중복 예약이 발생하지 않아야 한다
- Redis 분산 락 + DB 비관적 락 + 시간 겹침 검증의 다층 방어 전략을 사용한다

### 성능
- 예약 API 응답 시간 목표: 1초 이내
- 동시 100명 요청 시 정확히 1건만 성공해야 한다

### 신뢰성
- 예약 완료 이벤트는 유실되지 않아야 한다 (Kafka)
- 결제 실패 시 예약 상태가 정합성 있게 처리되어야 한다 (이벤트 기반 보상)

---

## 기술 스택

| 분류 | 기술 |
|------|------|
| Backend | Spring Boot 4.0.x |
| Language | Java 25 (LTS) |
| Database | MySQL 8.x — 서비스별 DB 분리 (JPA / QueryDSL) |
| Cache / 분산 락 | Redis (Redisson) — reservation 서비스만 |
| Message Broker | Kafka — 서비스 간 비동기 통신 |
| Service-to-Service | Spring 6 RestClient |
| Resilience | Resilience4j (Circuit Breaker / Timeout / Retry) |
| Build Tool | Gradle 9.x (멀티 모듈) |
| Infrastructure | AWS (RDS, ElastiCache, MSK, ECS) |
| Frontend | Next.js (React) |

---

## 배포 단위 (MSA)

```
[Client]
   ↓
┌──────────────┐
│ api          │ 외부 진입점, 인증(User/JWT)
└──────────────┘
   ↓ Kafka
┌──────────────┐
│ reservation  │ 예약 도메인 전체 (Merchant/Resource/AvailableTime/Reservation), 동시성 처리
└──────────────┘
   ↓ Kafka
┌──────────────┐  ┌──────────────┐
│ payment      │  │ notification │
└──────────────┘  └──────────────┘

[core] ← 라이브러리 (배포 X). 4 서비스에 jar 임베드
```

> 위 다이어그램은 핵심 예약 흐름(4개 서비스)만 표시한다. `batch`(예약 만료·통계 배치)와 `review`(리뷰, Kotlin)는 `db_reservation` 을 reservation 서비스와 공유하며 독립 배포되고, `pg`(Mock PG 서버)는 payment 서비스가 RestClient 로 직접 호출하는 완전 독립 프로세스다. 자세한 모듈 구조는 `docs/02-architecture.md` 참고.

각 서비스는 자기 DB 만 소유 (batch/review 는 reservation 과 db_reservation 공유). 다른 서비스 데이터는 REST 또는 이벤트 페이로드로 전달받는다.
