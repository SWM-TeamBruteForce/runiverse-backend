# CLAUDE.md

Runiverse 백엔드 — 원격 동반 러닝 플랫폼의 API 서버. 클라이언트는 Flutter 모바일 앱(안드로이드 우선, iOS는 이후 출시).

## 빌드 및 테스트

```bash
cd running-service
./gradlew test                                  # 전체 테스트
./gradlew test --tests '*JwtTokenAdapterTest'   # 단일 클래스 (패턴 매칭)
./gradlew bootRun                               # 앱 실행 (컨텍스트 경로: /api/v1)
```

- 테스트·실행에는 `.env` 필수(spring-dotenv) — 없으면 통합 컨텍스트 로드 자체가 실패한다.
- 테스트는 Mockito javaagent로 실행된다(build.gradle의 `mockitoAgent`) — 테스트 JVM 옵션 수정 시 주의.

## 문서 인덱스 — 구현 전 반드시 해당 문서를 읽을 것

- `docs/architecture.md` — 클린 아키텍처 + DDD 레이어 규칙·구현 스타일 (코드 작성 전 필독)
- `docs/code-convention.md` — 네이밍·주석·테스트 규칙 / `docs/git-convention.md` — 커밋·브랜치·PR 규칙 / `docs/api-convention.md` — API 표면 규칙 (에러 포맷·페이지네이션·인증·단위 접미사)
- `docs/api-spec.md` — API 명세 / `docs/erd.md` — DB 스키마 / `docs/feature-spec.md` — 기능 명세·도메인 제약
  - 자주 바뀐다 — 구현 전 다시 읽고, 코드와 어긋나면 사용자에게 확인한다.
  - 길다 — 통째로 읽지 말고 해당 기능의 섹션만 찾아 읽는다.

## 스킬 (`.claude/skills/`)

맥락에 맞으면 자동으로 적용되고, `/이름`으로 직접 부를 수도 있다.

- `usecase` — 명세 한 건을 전 레이어로 구현·확장 (구현 순서·레이어별 함정)
- `spec-check` — 문서끼리·문서↔구현 정합성과 레이어 규칙 점검 (`scripts/check_conventions.py`)

## 주의사항

- `BusinessException`/`ErrorCode`가 domain(VO 검증)·application(유스케이스)에 같은 이름으로 존재 — import 시 레이어 확인.
- application 에러 코드는 `ErrorCode`와 `GlobalExceptionHandler.toStatus()`에 반드시 반영한다. HTTP 400은 정책상 자동 노출하며, 그 외 상태는 현재 API 계약에서 공개할 코드만 `ErrorExposurePolicy.EXPOSED_CODES`에 추가한다. 의도적 비노출은 근거와 테스트를 남긴다. 공개 대상 코드가 `EXPOSED_CODES`에서 빠지면 컴파일·테스트를 통과해도 런타임에 500으로 마스킹된다.
- 도메인 예외는 500으로 마스킹된다 — 400으로 보여줄 검증은 Request DTO의 Bean Validation이 만든다.
- `.env` 등 시크릿 파일은 절대 커밋하지 않고, 키 값은 출력 시 마스킹한다.
- 요청 없이 공개 API 계약(요청·응답 형식)이나 의존성(라이브러리)을 변경하지 않는다.
- 문제를 우회하는 해결 금지 — 테스트 약화·빈 catch·기능 삭제로 오류를 없애지 않는다.
