---
name: usecase
description: >-
  Runiverse 백엔드에서 api-spec.md에 명세된 API·유스케이스 한 건을
  도메인·애플리케이션·필요한 인프라 어댑터·프레젠테이션·테스트까지 전 레이어로 구현하거나 확장할 때 사용한다.
  "API 만들어줘", "스펙 N번 구현", "엔드포인트 추가"와 같은 요청이 해당한다.
  단일 레이어 유지보수·버그 수정·리팩터링·테스트만 작성하는 요청에는
  명세 한 건의 전 레이어 구현 일부로 함께 요청된 경우가 아니면 사용하지 않는다.
  자바 코드를 바꾸지 않는 요청(문서·명세 작성, 정합성 점검만)과
  로그·주석 추가·리네임에도 사용하지 않는다.
---

# 유스케이스 구현

명세 한 건을 전 레이어의 테스트 가능한 코드로 구현한다. 레이어와 네이밍은 `docs/architecture.md`를 따른다.

## 저장소 고유 규칙

- **Spring Data Repository를 쓰지 않는다.** `EntityManager`와 JPQL 텍스트 블록을 사용한다.

## 사전 확인

`api-spec.md` 색인에서 대상 번호나 경로를 특정한다. 대상이 모호하거나 계약이 빠졌으면 수정 전에 한 번에 확인한다. 문서는 아래 범위만 읽는다.

| 문서 | 확인할 것 |
|---|---|
| `api-spec.md` | §0 공통 규칙 + 대상 절의 경로·필드·상태 코드·에러 케이스·**검증 메시지 문구** |
| `erd.md` | 컬럼·타입, §0 PK/FK 정책, §6 enum 사전, 조회 조건이 있으면 §7 인덱스 |
| `feature-spec.md` | 해당 화면 절(§1)과 공통 도메인 제약(§2) |
| `api-convention.md` | 정본·성공 상태 코드·단위 접미사·커서 페이지네이션·토글 액션 |

**문서끼리 또는 명세와 코드가 어긋나면 한쪽을 그대로 따르지 않는다.** 어느 쪽이 정본인지는 `api-convention.md` "정본"을 따른다(구현된 API는 코드, 미구현 API는 명세). 구현 결과가 갈리는 차이는 멈추고 확인하고, 그렇지 않으면 판단 근거를 밝히고 진행한 뒤 보고한다. 이미 받은 수정 기준은 재확인하지 않는다.

정합성 점검만 요청받으면 `spec-check`를 사용한다. 점검과 구현을 함께 요청받으면 차이와 수정 기준을 먼저 합의한다.

형태는 `application/auth/command/signup`(생성)·`login`(조회+토큰), 조회는 `application/running/query/record`를 참고한다. 더 맞는 구조가 있으면 근거와 함께 제안하되, 요청 범위 밖의 기존 코드는 바꾸지 않는다.

## 구현

컴파일을 유지하며 안쪽에서 바깥쪽으로 구현한다. 변경할 레이어만 `references/layer-patterns.md`에서 읽는다.

**1) 도메인** — 값 규칙이나 상태 전이가 있을 때만 변경한다. 프레임워크를 import하지 않는다.

**2) 애플리케이션** — `architecture.md`의 패키지 구조대로 `command/`·`query/` 기능 패키지, `port/in`·`port/out`, 유스케이스 거부 조건용 `exception/`을 만든다. 로그·메트릭을 남기면 `layer-patterns.md`의 "로그·메트릭"을 따른다.

**3) 에러 처리** — 애플리케이션 예외를 추가하거나 요청 값 규칙을 바꿀 때 `references/error-registration.md`를 따르고 DTO·VO 검증을 함께 반영한다.

**4) 인프라** — JPA 엔티티는 `erd.md`의 제약을 그대로 반영한다. 도메인 ↔ 엔티티 변환은 어댑터가 맡는다. 같은 애그리거트·저장 기술의 포트는 기존 어댑터가 함께 구현할 수 있다.

**5) 프레젠테이션** — 컨트롤러와 그 request·response는 첫 경로 구간의 도메인 `presentation/<도메인>/`에 둔다 — 호출하는 유스케이스의 도메인과는 무관하다(`architecture.md` presentation 규칙). 설정이 붙이므로 `@RequestMapping`에 `/api/v1`을 넣지 않는다. DTO 단위 접미사는 `api-convention.md` "물리량 단위"를 따른다. Bean Validation 메시지는 **`api-spec.md` 문구 그대로** 둔다.

**6) 테스트** — `docs/code-convention.md`와 `UserVoTest`·`UserOnboardingTest`·`UserPersistenceAdapterTest`를 따른다. 도메인 예외의 타입·메시지, 핸들러의 성공·실패 경로와 실패 후 중단을 검증한다. 테스트는 세 층이다.

| 층 | 위치 | 하는 일 |
|---|---|---|
| 단위 | `src/test/.../unit_test/<도메인>/`, 어댑터는 `unit_test/infrastructure/` | 도메인·Handler·요청 검증·컨트롤러(standalone MockMvc)·어댑터를 각각 검증 |
| 통합 | `src/test/.../integration_test/` | 스프링 없이 페이크(`integration_test/fake/`)로 Handler를 조립해 유스케이스 흐름 검증 |
| E2E | `e2e_test/` | 배포 이미지를 Docker로 띄워 HTTP·WebSocket으로 검증. `e2e_test/run-e2e.sh` |

새 유스케이스는 조회를 포함해 통합 테스트를 붙인다. JPQL을 새로 쓰거나 고쳤으면 E2E에도 그 쿼리를 타는 단계를 붙인다(기존 시나리오에 잇거나 새로 만든다) — 실제 DB에서 쿼리를 확인하는 층은 E2E뿐이다. 외부 제공자가 필요한 경로(소셜 로그인)는 E2E에서 제외하고 그 사실을 보고한다.

테스트 제외 요청이 있으면 새 테스트를 만들지 않는다. 컴파일과 기존 테스트로 검증하고 미완료 커버리지를 보고한다.

## 검증

```bash
cd running-service && ./gradlew test
```

응답 필드명·상태 코드·에러 코드, 포트의 응집도, 새 에러 코드의 상태·노출 여부가 계약과 맞는지 확인한다. E2E는 Docker가 필요하므로 돌리지 못했으면 그 사실을 밝힌다.

구현과 필요한 테스트를 작성하고 위 검증을 마쳐야 완료다. 구현 요약, 검증 결과, 남은 결정이나 실행하지 못한 항목만 보고한다.

## WebSocket·SSE

러닝 WebSocket(`api-spec.md` 5-C·5-D)은 `presentation/running/websocket/`, 매칭 SSE(5-A~5-C)는 `presentation/match/sse/`에 선례가 있다. 기존 채널에 메시지를 더하면 `RunningWebSocketHandler`·`RunningMessageType`과 `message/`의 요청·페이로드 형태를 따른다. WebSocket 에러는 핸들러가 `ErrorPayload`로 직접 보내며 HTTP 노출 정책을 타지 않는다.

다음은 아직 정하지 않았으므로 REST 절차를 적용하지 말고 설계를 먼저 합의한다: 새 채널·세션 정책, WebSocket·SSE 진입점의 로그 MDC(`logging-convention.md` "전환 중")와 메트릭(`metrics-convention.md` "예정"), `RUNNING_PAUSE`/`RUNNING_RESUME`.
