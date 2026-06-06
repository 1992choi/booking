# /update-docs

최근 변경된 코드를 분석해서 `docs/` 문서를 현행화한다.

## 실행 순서

### 1. 변경 파일 파악
```bash
git diff --name-only HEAD~1 HEAD
git diff --name-only  # unstaged
git diff --cached --name-only  # staged
```
`.java`, `.gradle`, `.yml` 변경 파일만 대상. `docs/` 변경은 무시.

### 2. 변경 내용 확인
변경된 파일을 읽어서 무엇이 바뀌었는지 파악한다.
- 새 엔드포인트 추가/삭제
- 엔티티 필드 변경
- 서비스 간 통신 변경 (Kafka 토픽, REST 호출)
- 에러코드 추가/변경
- 의존성 추가 (build.gradle)

### 3. 영향받는 docs 선별

| 변경 유형 | 업데이트할 문서 |
|-----------|----------------|
| Controller 추가/변경 | `04-api-spec.md` |
| Entity 필드 변경 | `03-erd.md` |
| Kafka 토픽/이벤트 변경 | `02-architecture.md`, 해당 `06-*.md` |
| 새 기능 구현 (backlog 항목) | `99-backlog.md` (완료 표시) |
| 서비스 전반 구조 변경 | `02-architecture.md` |
| core 모듈 변경 | `05-module-core.md` |
| api 서비스 변경 | `06-1-module-api.md` |
| reservation 서비스 변경 | `06-2-module-reservation.md` |
| payment 서비스 변경 | `06-3-module-payment.md` |
| notification 서비스 변경 | `06-4-module-notification.md` |

### 4. 문서 업데이트 규칙
- 실제 코드에 없는 내용은 삭제 또는 수정
- 코드에 새로 생긴 내용은 추가
- 문서의 기존 스타일(표 형식, 헤딩 구조)을 유지
- 설계 의사결정 맥락(Why)은 건드리지 않음 — 코드에서 읽을 수 없는 정보임
- 변경이 없는 문서는 손대지 않음

### 5. 완료 보고
어떤 파일을 왜 업데이트했는지 한 줄씩 요약 출력.
변경이 없으면 "업데이트할 내용 없음" 출력.
