---
name: spec-check
description: >-
  Runiverse 문서끼리 또는 문서와 구현의 불일치, 클린 아키텍처 규칙 위반을 진단한다. API·ERD
  정합성, 레이어 의존 방향, 에러 노출, 포트 구조, DTO 단위를 점검한다. "명세대로 구현됐나",
  "스펙 누락", "문서와 코드가 맞나", "레이어·아키텍처 규칙 점검", 리뷰·PR 전 점검 같은
  요청에 사용한다. 수정이 함께 요청되면 수정 전에 진단 단계로 적용하며, 이 스킬 자체는
  파일을 변경하지 않는다. 일반 코드 품질 리뷰, 순수 구조 설명, 불일치 진단이 없는
  문서 작성·편집에는 사용하지 않는다.
---

# 명세·구조 정합성 점검

구조 위반은 스크립트와 수동 확인으로 검사하고, 명세 불일치는 문서와 구현을 직접 대조한다.

발견한 것은 **고치지 않는다.** 차이와 영향을 보고하고 수정 기준을 확인한다. 이미 받은 기준은 재확인하지 않으며, 수정 요청은 기준 확정 뒤 별도 단계에서 수행한다. API의 정본은 `api-convention.md` "정본"으로 판단한다 — 구현된 API는 **문서가 틀렸을 수 있다**. 그 규칙으로 판단할 수 없고 선택에 따라 공개 API·데이터 계약·업무 동작·권한이 갈리는 충돌만 사용자에게 확인한다.

## 0. 요청 유형과 범위

요청에 걸리는 절만 수행한다. 구조만 물으면 §1, 구현 정합성은 §1·§2, 문서 간 정합성은 §3, 종합 점검·PR 점검은 §1~§3이다. 누락·완성도 점검이면 명세에만 있는 REST 엔드포인트와 WebSocket 메시지도 포함한다.

범위를 지정받으면 진입점부터 `port/in`·Handler·도메인·`port/out`·어댑터까지 직접 호출·의존하는 경로와, 인증·예외·직렬화처럼 결과에 직접 영향을 주는 공통 구현을 포함한다. 범위 밖 발견 사항은 보고하지 않는다. 범위가 없으면 구현 정합성은 구현된 진입점 전체, 구조 검사는 main Java 소스 전체를 본다.

다음 명령은 진입점 **후보** 검색용이다. 파일을 열어 클래스·메서드 매핑을 조합해 실제 경로와 WebSocket destination을 확정한다.

```bash
rg -n '@(RestController|Controller|RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|MessageMapping|SubscribeMapping)\b|WebSocketHandler' running-service/src/main/java -g '*.java'
```

판정 기준은 항목별 문서다. 레이어·트랜잭션·포트는 `docs/architecture.md`, API 표면은 `docs/api-convention.md`·`docs/api-spec.md`, 저장 구조·enum은 `docs/erd.md`, 업무 동작은 `docs/feature-spec.md`, 로그·메트릭은 `docs/logging-convention.md`·`docs/metrics-convention.md`를 본다. 같은 영역의 문서끼리 충돌해 정본 규칙으로도 풀리지 않으면 임의로 우선순위를 만들지 말고 조사 필요로 둔다.

## 1. 구조 검사

저장소 루트에서 실행한다 — `running-service` 안에서는 경로가 깨진다. 두 번째 인자는 소스 루트 기준의 파일 또는 디렉터리 하나이며, 생략하면 전체를 검사한다. 여러 범위는 각각 실행한 뒤 중복을 제거한다.

```bash
python3 .claude/skills/spec-check/scripts/check_conventions.py .
python3 .claude/skills/spec-check/scripts/check_conventions.py . application/user
```

종료 코드는 위반이 있어도 0이고, 인자·루트·범위가 잘못됐을 때만 2다. 2가 나오면 경로를 바로잡아 다시 실행하고, 그래도 실행하지 못하면 이 절의 항목을 수동 검사한 뒤 그 사실을 보고에 밝힌다. §2는 구조 검사의 대체가 아니다.

스크립트는 레이어 의존 방향(메트릭 의존 포함), command·query 패키지 구성·트랜잭션, 아웃바운드 구현체 네이밍, 에러 등록 후보, 미사용 예외, 포트 규칙, DTO 단위 접미사, 엔티티 `@Check` ↔ erd CHECK 블록(전체 검사에서만)을 검사한다. 출력 분류는 **후보**다 — 코드와 기준 문서를 직접 확인해 재분류하고, 스크립트 휴리스틱이 문서와 다르면 문서를 기준으로 판정한다. 검사 항목이 "0개"·"없음"으로만 나오는데 코드에 대상이 있으면 스크립트가 구조 변경을 못 따라간 것이다 — 통과로 보고하지 말고 수동 검사한다.

범위를 좁혀 실행한 에러 코드 결과는 단독으로 판정하지 않는다. throw 지점 → application 예외 → 도메인별 `*ErrorCode` enum → 해당 `toStatus()` overload → `EXPOSED_CODES` 또는 마스킹까지 추적한다(domain의 동명 enum은 제외). 전용 `@ExceptionHandler`(`MatchCooldownException`)와 WebSocket 경로는 직접 응답하므로 그 응답을 본다 — WebSocket 전용 코드는 HTTP 마스킹 후보로 나와도 조치 불필요다.

스크립트가 보지 못하는 다음을 직접 확인한다.

- 어댑터가 애그리거트를 반쪽만 복원해서 도메인 메서드가 죽은 코드가 됐는지
- 같은 일을 하는 포트가 이름만 다르게 중복됐는지
- Handler가 필요한 포트만 주입받는지 — 같은 애그리거트·저장 기술의 포트를 어댑터 하나가 함께 구현하는 것도, 나누는 것도 그 자체로는 위반이 아니다
- 포트 구현체의 `*Adapter`·`*Client`·`*Router`·`*Registry` 접미사가 실제 역할과 맞는지
- 컨트롤러와 그 request·response가 API가 다루는 리소스의 도메인 패키지에 있는지(`architecture.md` presentation 규칙) — 경로 첫 구간과 다를 수 있다(`/users/me/running-records` → `running`)
- 로그가 레이어별 규칙(레벨·찍는 위치·남기지 않는 것)을, 메트릭이 기록 위치·태그 규칙을 따르는지 — `logging-convention.md` "전환 중" 절에 해당하는 기존 코드는 위반으로 올리지 않는다

## 2. 명세와 구현 대조

각 엔드포인트 상세뿐 아니라 `docs/api-spec.md` §0의 적용 가능한 공통 규칙도 먼저 대조한다.

| 볼 것 | 구현 |
|---|---|
| 경로·HTTP 메서드 | 클래스 `@RequestMapping` + 메서드 매핑 조합 (`/api/v1`은 설정이 붙임) |
| 인증·인가 | 인증 필터·Security 설정 + 현재 사용자 주입 + 소유자·역할·참가자 권한 검사 |
| 헤더·경로·쿼리 파라미터 | `@RequestHeader`/`@PathVariable`/`@RequestParam`/`@ModelAttribute` DTO + 타입·필수 여부·기본값·최댓값·클램프 동작 |
| 요청 필드명·필수 여부 | Request record + `@Valid` + `@NotNull`/`@NotBlank` 등 제약 값 |
| 검증 메시지 문구 | Bean Validation `message` — **글자 단위로** 같아야 한다 (400 응답 본문) |
| 응답 필드명·타입 | Response record + 매퍼 |
| 성공 상태 코드 | `ResponseEntity.status(...)`·`@ResponseStatus`·기본 200 |
| 에러 코드·상태·노출 | `*ErrorCode` + `toStatus()` + `EXPOSED_CODES` 또는 전용 핸들러 (§1의 추적 경로) |
| 멱등성·업무 동작 | Handler·도메인·포트 호출 + 중복 호출 결과·상태 변경·트랜잭션 |
| 시각·ID·단위·enum | DTO·매핑·도메인 타입과 `docs/api-spec.md` §0·`docs/api-convention.md`·`docs/erd.md` §6 |

JPA 엔티티는 `docs/erd.md` §0과 해당 표의 컬럼명·타입·nullable·PK·UNIQUE·FK·삭제 정책·감사 컬럼·enum 값(§6)과 대조한다. CHECK는 스크립트가 대조한다.

빌드·테스트는 진단의 완료 조건이 아니다. 사용자가 런타임 검증을 요청했거나 별도 수정 단계에서 파일을 바꾼 경우에만 `CLAUDE.md`의 가장 작은 관련 테스트를 실행한다. 실패는 우회하지 말고 명령·핵심 오류와 함께 그대로 보고한다.

## 3. 문서 간 대조

§0에서 정한 범위에 걸리는 절만 대조한다.

- `docs/api-spec.md`의 공통·상세 규칙 ↔ `docs/api-convention.md`
- `docs/api-spec.md`의 동작·권한·1차 범위 ↔ `docs/feature-spec.md`의 해당 화면 절
- `docs/erd.md`의 저장 구조·enum·FK 정책 ↔ `docs/feature-spec.md`의 도메인 제약

## 4. 보고 전 — 의도적 변경인지 확인한다

```bash
git log --oneline -S "<식별자>" -- running-service/src docs
```

`-S`는 식별자가 추가·삭제된 커밋을 찾는다. 메시지가 애매하면 `git show <해시>`로 diff를 확인한다. 작업 중인 변경이 있으면 `git status --short`로 확인하되 되돌리지 않는다.

`docs/architecture.md`의 "구현 스타일 기준"에 적힌 예외와 커밋으로 의도가 확인된 항목은 조치 불필요 또는 활성 임시 예외로 분류한다. 새 코드가 그 예외를 따라 한 경우에만 위반으로 올린다. 이력만으로 판단이 서지 않으면 단정하지 말고 조사 필요로 둔다.

`.env`·키·토큰은 열지 않고, diff나 이력에서 민감정보가 보이면 `[MASKED]`로 적는다.

## 5. 보고 형식

- 결과를 **확정 위반 / 조사 필요 / 휴리스틱 의심**으로 나누고, 각 항목을 `항목 | 기준 | 대상 | 영향·판단` 형식으로 작성한다. 파일·줄 등 확인 가능한 근거를 붙인다.
  - **확정 위반**: 불일치나 명시적 규칙 위반이 확인됐다. 정본 규칙으로 어느 쪽을 고칠지 정해지지 않으면 `불일치 확정·수정 방향 미정`으로 적는다.
  - **조사 필요**: 기준 문서가 충돌하거나 근거가 부족해 판정할 수 없다.
  - **휴리스틱 의심**: 명시적 금지는 없지만 구조적 냄새가 있어 판단이 필요하다.
- 문서에 적힌 기존 리팩터링 예외는 **활성 임시 예외**, 확인 결과 오탐이거나 의도된 것은 **조치 불필요** 절에 둔다. 임시 예외를 새 코드로 넓힌 것은 확정 위반이다.
- 각 분류 안에서는 공개 계약·런타임 동작에 미치는 영향이 큰 것부터 적는다.
- 문제가 없더라도 점검한 범위와 통과한 항목을 적는다. 실행하지 못한 검사는 이유와 미확인 범위를 밝힌다.

## 6. 마무리

§0에서 정한 범위의 항목을 통과·발견·미확인 중 하나로 정리해 §5 형식으로 보고해야 완료다. 미확인이 남으면 전체 통과가 아니라 부분 점검임을 밝힌다. 수정 요청에는 수정 기준과 논리적 작업 단위를 제안한다.

문서가 낡았거나 현재 구현·관례가 더 합리적이라고 판단되면 근거와 함께 문서 수정도 선택지로 제시한다. 다만 제안은 최소 변경 단위로 하고, 요청 범위를 넘는 리팩터링이나 새 구조 도입은 실행하지 않는다.
