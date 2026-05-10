# 01. 프로젝트 개요 & 요구사항

## 프로젝트 개요

특정 시간대를 예약하는 범용 예약 플랫폼 엔진.
프론트엔드만 교체하면 숙박, 강의, 시설, 상담 등 다양한 도메인에 적용 가능.

MSA 구조로 설계되며, 4개의 독립 배포 서비스 + 1개의 공통 라이브러리(core)로 구성된다.

---

## 기능 요구사항

### 인증 (api 서비스)
- 사용자는 이메일/비밀번호로 회원가입 및 로그인할 수 있다
- JWT 기반 인증을 사용한다
- Access Token 만료 시 Refresh Token 으로 갱신할 수 있다

### 업체(Owner) (api 서비스)
- 업체를 등록, 수정, 조회할 수 있다
- 업체 타입은 PENSION / CLASS / FACILITY / CONSULTING 이다

### 예약 대상(Resource) (api 서비스)
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

### 관리 (api 서비스)
- 업체 관리자는 전체 예약 현황을 조회할 수 있다 (reservation 서비스로 위임)
- 업체 관리자는 예약을 수동으로 확정 또는 취소할 수 있다
- 캘린더 형태로 예약 현황을 조회할 수 있다

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

> **현재 상태**: 4개 서비스 기본 기능 구현 완료. Kafka 연동, Redis 분산락, 관리 API 등은 개선 이슈로 분리됨 (아래 개발 순서 참고).

---

## 배포 단위 (MSA)

```
[Client]
   ↓
┌──────────────┐
│ api          │ 외부 진입점, 인증, 사용자/업체/리소스 CRUD
└──────────────┘
   ↓ REST / Kafka
┌──────────────┐
│ reservation  │ 예약 도메인, 동시성 처리
└──────────────┘
   ↓ Kafka
┌──────────────┐  ┌──────────────┐
│ payment      │  │ notification │
└──────────────┘  └──────────────┘

[core] ← 라이브러리 (배포 X). 4 서비스에 jar 임베드
```

각 서비스는 자기 DB 만 소유. 다른 서비스 데이터는 REST 또는 이벤트 페이로드로 전달받는다.

---

## 개발 순서

### 완료

| 단계 | 내용 |
|------|------|
| 1단계 | core 라이브러리 — BaseEntity, ErrorCode, GlobalExceptionHandler, JwtVerifier, JwtAuthenticationFilter |
| 2단계 | api 서비스 — 회원가입/로그인/JWT 발급, Owner/Resource/AvailableTime CRUD, Internal API |
| 3단계 | reservation 서비스 — 예약 생성/조회/취소 (시간 중복 검사 포함) |
| 4단계 | payment 서비스 — 결제 내역 조회, 환불 |
| 5단계 | notification 서비스 — 알림 발송(Mock), 이력 조회 |

### 개선 이슈 (미구현)

| 항목 | 설명 |
|------|------|
| Refresh Token | api 서비스 `POST /auth/refresh` 미구현 |
| Redis 분산락 | reservation 서비스 예약 생성 시 동시성 처리 미적용 (현재 시간 중복 검사만) |
| DB 비관적 락 | reservation 서비스 `findOverlapping` 에 `@Lock` 미적용 |
| Kafka 연동 | 서비스 간 이벤트 흐름 전체 미연결 — `reservation.created` → 결제, `payment.completed` / `reservation.cancelled` → 알림 |
| 관리 API | api 서비스 `AdminController` + `ReservationClient` 미구현 |
| 유저 동기화 | 각 서비스가 JWT 서명 검증만으로 유저를 신뢰하는 구조 → `user.created` / `user.deleted` Kafka 이벤트로 각 서비스 users 테이블 동기화 필요 |
