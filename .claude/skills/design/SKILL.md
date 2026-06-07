# /design

개발할 기능을 설계하고 `.claude/tasks/{feature}.md`에 기록한다.
이후 `/impl`이 이 파일을 읽고 구현을 시작한다.

사용법: `/design <기능명>`  
예시: `/design reservation-create`, `/design jwt-auth`

## 실행 순서

### 1. 관련 docs 읽기

기능명을 보고 어느 서비스에 해당하는지 판단한 뒤 관련 문서를 읽는다.

| 항상 읽는 문서 | 조건부 |
|--------------|--------|
| `docs/04-api-spec.md` | 해당 서비스 `docs/06-*.md` |
| `docs/03-erd.md` 해당 서비스 섹션 | `docs/02-architecture.md` (서비스 간 통신 포함 시) |

### 2. 불명확한 부분 질문

docs만으로 확인되지 않는 것을 질문한다. 명확하면 이 단계를 건너뛴다.

질문 대상 예시:
- 범위가 여러 서비스에 걸치는 경우
- 비즈니스 규칙이 docs에 명시되지 않은 경우
- 기존 코드와 충돌 가능성이 있는 경우

### 3. 설계 파일 작성

`.claude/tasks/{feature}.md`를 아래 형식으로 작성한다.

```markdown
# {기능명}

## 구현 범위
- 어느 서비스에서 무엇을 만드는지 한 줄씩

## API
| Method | Path | 설명 |
|--------|------|------|
| ...    | ...  | ...  |

## ERD 변경
- 새 테이블 또는 컬럼 변경 내역 (없으면 "없음")

## 예상 파일 목록
- `src/main/java/.../controller/XxxController.java`
- `src/main/java/.../service/XxxService.java`
- ...

## 주의사항
- 구현 시 놓치기 쉬운 컨벤션, 엣지 케이스
```

### 4. 완료 보고

작성한 파일 경로와 핵심 내용을 한 줄로 요약 출력.