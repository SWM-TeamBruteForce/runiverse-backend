# API Convention

REST API 표면 규칙 — 엔드포인트 설계·DTO 작성·스펙 문서화가 모두 이 규칙을 따른다.

## 기본

- Base path: `/api/v1`
- 필드명: JSON 요청/응답은 camelCase — DB 컬럼은 snake_case 유지, 백엔드에서 매핑한다.
- ID 타입: `userId`만 UUID, 서버가 발급하는 그 외 리소스 ID는 Long이다. 클라이언트 식별자(`deviceId`)와 커서(`cursor`)·S3 key는 문자열이다.
- 시각: ISO 8601 `yyyy-MM-ddTHH:mm:ss` — KST 기준, 오프셋 없이 초 단위까지(예: `2026-07-20T13:00:00`). 달력 날짜는 `YYYY-MM-DD`다. 저장은 KST(`TimeZoneConfig`), 직렬화 형식은 `JacksonConfig`가 고정한다.
- DB enum: API에 노출할 때도 동일한 영문 코드를 사용한다. 변환 매핑은 없고 값 목록은 [erd.md](erd.md) §6을 따른다.
- 사용자 리소스 경로: 타인도 접근할 수 있으면 `/users/{userId}/...`, 본인만 접근하면 `/users/me/...`를 쓴다. 후자는 토큰 주체가 곧 대상이라 경로에 식별자를 받지 않는다.
  - **단, 그 리소스를 만드는 경로가 이미 따로 있으면 생성·삭제를 그 경로로 통일한다** — 만들 때와 지울 때 경로가 갈리는 편이 더 헷갈린다. 매칭 신청(`POST`/`DELETE /running-matches`)이 여기 해당하며, 유저당 하나뿐이라 식별자 없이도 대상이 정해진다.
  - **`/users/me/status`는 "지금 무엇을 하는 중인가"만 담는다** — 매칭·러닝 진행 상태와 그 판단에 필요한 최소값이다. 계정 상태·온보딩 여부·설정처럼 활동과 무관한 것은 넣지 않는다. 경로 이름이 넓어 무엇이든 끼워 넣기 쉬우므로 범위를 여기에 고정한다.

## 정본

- **구현된 API는 코드가 정본이다.** 문서와 어긋나면 문서를 고친다 — 단 코드가 버그인 게 명백하면 예외이고, 판단이 갈리면 팀에 확인한다.
- **미구현 API는 이 문서와 api-spec.md가 정본이다.** 구현이 명세와 다르게 나가면 구현을 맞추거나, 의도적 변경이면 명세를 함께 고친다.

## 하위 호환

**첫 스토어 배포 전에는 적용하지 않는다** — 배포된 구버전이 없으므로 계약은 FE와 합의해 자유롭게 바꾼다. 배포 후부터 아래가 적용된다.

- 클라이언트가 스토어 배포 앱이라 구버전이 오래 남는다 — 배포된 계약은 파괴적으로 바꾸지 않는다.
- 필드 추가는 optional로 한다. 기존 필드는 제거·리네임·타입 변경·의미 변경을 하지 않는다.
- 바꿔야 하면 새 필드를 더하고, 구버전이 빠질 때까지 기존 필드도 함께 채운다.

## 응답 형태

- 성공 상태 코드: 리소스를 새로 만드는 POST는 `201 Created`, 조회·갱신은 `200 OK`, 반환할 본문이 없으면 `204 No Content`다. 로그인·토큰 재발급·중복 확인처럼 리소스를 만들지 않는 POST는 `200 OK`를 쓴다.
- 에러 응답: `{ code, message }`는 필수이며 오류별 추가 필드를 둘 수 있다. 평면 구조를 쓰고 `error` 래핑·status 필드는 두지 않으며, 상태는 HTTP 상태 코드로 표현한다.
- 페이지네이션을 사용하는 목록은 커서 기반(`?cursor=&limit=`)이다. `limit` 기본값은 20, 최대 50이며 초과값은 50으로 클램프한다. 응답은 `{ items: [...], nextCursor: string | null }`이다.
- 토글형 액션: POST(등록)/DELETE(취소)로 분리하며 중복 호출에도 성공한다. 갱신된 상태·카운트를 반환하면 `200 OK`, 반환값이 없으면 `204 No Content`로 응답한다.

## 인증

- Bearer 토큰, Access+Refresh 이원화(`Authorization: Bearer {accessToken}`).
- Refresh 시 rotation: access·refresh 모두 재발급하고 이전 refreshToken은 무효화한다.
- 로그아웃 시 해당 access 토큰은 서버 블랙리스트 처리한다(`401 TOKEN_BLOCKED`).

## 물리량 단위

- 단위 접미사는 `...Meters` / `...Seconds` / `...Ms` / `...SecondsPerKm` / `...MetersPerSecond` / `...Degrees` / `...Bytes` / `...Kg` / `...Cm` / `...Kcal` / `...Spm`으로 명시한다. 위도·경도는 표준 필드명 `latitude`/`longitude`를 쓴다.
- 시간 간격은 초(`...Seconds`)가 기본이고, **초 단위로는 정밀도가 모자라는 짧은 간격만 `...Ms`를 쓴다**(예: `startsInMs`). 시각 포맷이 초 단위까지라 밀리초가 필요한 값은 시각이 아니라 간격으로 내려보낸다.
- 거리는 전부 미터로 통일하고(km 표시는 프론트 포맷팅), 페이스는 초/km 정수로 쓴다(`390` → "6:30" 표시).
- 예: `totalDistanceMeters`, `averagePaceSecondsPerKm`, `speedMetersPerSecond`, `weightKg`, `caloriesKcal`
