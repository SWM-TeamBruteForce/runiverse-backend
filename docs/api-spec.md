# Runiverse API 명세서

> 구성: 엔드포인트 목록(색인) → 상세 명세 0~12. API 표면 규칙은 `api-convention.md`, 데이터 기준은 `erd.md`를 따른다.

---

## 엔드포인트 목록 (색인)

같은 API가 여러 화면에서 쓰이면 처음 등장하는 화면에 한 번만 적고 "사용 화면"으로 표시.

### 1. 인증·온보딩 (초기 페이지 / 회원가입 페이지 / 온보딩 화면)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 1 | POST | `/api/v1/auth/email/verifications` | 이메일 인증번호(6자리) 발급 — 회원가입 1단계 |
| 2 | POST | `/api/v1/auth/email/verifications/confirm` | 이메일 인증번호 확인 → `verificationTicket` 발급 — 회원가입 2단계 |
| 3 | POST | `/api/v1/auth/signup` | 로컬 회원가입 (인증 티켓/비밀번호) — 가입 즉시 자동 로그인 |
| 4 | POST | `/api/v1/auth/login` | 로컬 로그인 |
| 5 | POST | `/api/v1/auth/oauth/google` | 구글 로그인 — 인가 코드+PKCE 서버 교환 → 토큰 발급 |
| 6 | POST | `/api/v1/auth/oauth/kakao` | 카카오 로그인 — 인가 코드+PKCE 서버 교환 → 토큰 발급 |
| 7 | POST | `/api/v1/auth/refresh` | 토큰 재발급 (rotation — accessToken·refreshToken 모두 교체) |
| 8 | POST | `/api/v1/auth/logout` | 로그아웃 — access 토큰 서버 차단(블랙리스트) + 리프레시 토큰 삭제 — 사용 화면: 설정 페이지 |
| 9 | POST | `/api/v1/users/onboarding` | 온보딩 입력 (닉네임 포함, 1회성) |

### 2. 공통 — 디바이스/푸시

| # | Method | Path | 설명 |
|---|--------|------|------|
| 10 | POST | `/api/v1/devices` | 디바이스(푸시 토큰) 등록/갱신, `isActive=true` 전환 — 사용 화면: 로그인 직후 전역 |

### 3. 홈 화면

- 날씨: 서버 API 없음 — 클라이언트가 직접 호출 (상세 3번)
- 매칭 시작·취소: REST (아래 5번). 대기 현황은 SSE 스트림으로 수신

### 4. 매칭완료 대기방

- 대기방 정보·참가자 목록: 매칭 SSE 스트림 (아래 5번)
- 나가기: `DELETE /api/v1/users/me/running-match`
- 친구 초대 **[MVP 제외]**: 엔드포인트 미정 (상세 4번)

### 5. 매칭·러닝 실시간 통신

매칭은 **REST + SSE**, 러닝 구간은 **WebSocket**이다. 전환 절차는 상세 5번 머리말 참고.

**매칭 HTTP**

| # | Method | Path | 설명 |
|---|--------|------|------|
| 11 | POST | `/api/v1/running-matches` | 매칭 신청 (시각+거리) — 409 `RUNNING_ALREADY_IN_PROGRESS` |
| 12 | DELETE | `/api/v1/users/me/running-match` | 대기 취소 + 확정 후 나가기 겸용 (서버가 방 상태로 분기) |
| 13 | GET | `/api/v1/users/me/running-match` | 현재 매칭 상태 — 홈 진입·앱 재시작 시 파생 상태 조회 |
| 14 | GET | `/api/v1/running-matches/slots` | 시간대별 대기 인원 — 매칭 입력 모달의 "3명 대기 중" 표시 |
| 15 | GET | `/api/v1/running-matches/stream` | 매칭 이벤트 스트림 (SSE) |
| 16 | POST | `/api/v1/running-rooms/solo` | 솔로 러닝 개시 (매칭 방은 서버가 생성) |

**매칭 SSE** — 이벤트 3종. 연결 직후 현재 상태 스냅샷을 받는다.

| 이벤트 | 비고 |
|--------|------|
| `MATCH_PLAYERS_UPDATED` | 대기 인원 변동 (`RoomInfo`) |
| `MATCH_STARTED` | 매칭 성사 통지 (`RoomInfo`) |
| `MATCH_ROOM_UPDATED` | 방 상태 갱신 (`RoomInfo`) — 취소·러닝 시작 포함 |

**러닝 WebSocket** — `/api/v1/ws/running`, 메시지 7종. 매칭 러닝과 솔로 러닝이 같은 채널을 쓴다. 이 외에 **ack 2종**(`RUNNING_STARTED`·`RUNNING_FINISHED`)과 **헬스 체크 2종**(`HEALTH_CHECK`·`HEALTH_CHECKED`)이 있다.

| 그룹 | 메시지 | 방향 | 비고 |
|------|--------|------|------|
| 카운트 다운 | `RUNNING_START` | C→S | 방의 `STARTED` 확인 후 참가자 시작 통보 |
| 러닝 중 | `RUNNING_LOCATION_UPDATE` | C→S | 고빈도 — ack 없음 |
| 러닝 중 | `PLAYER_RUNNING_PROGRESS_UPDATED` | S→C | `paused` 포함 — 멈춘 것과 느려진 것을 구분 |
| 러닝 중 | `RUNNING_PAUSE` / `RUNNING_RESUME` | C→S | 일시정지·재개 — 본인 기록만 멈춘다 |
| 러닝 중 | `RUNNING_FINISH` | C→S | `forced` 플래그로 조기 종료 의사 포함 — 서버가 상태·기록 확정 |
| 공통 | `ERROR` | S→C | WS 요청 실패 통지 |

### 6. 러닝 중 / 러닝 후 대시보드

| # | Method | Path | 설명 |
|---|--------|------|------|
| 17 | GET | `/api/v1/running-rooms/{runningRoomId}/results` | 참가자 전원 결과 스냅샷 — 사용 화면: 러닝 후 대시보드 |
| 18 | GET | `/api/v1/running-rooms/{runningRoomId}/split-results` | 구간별 상세 + 경로 |

### 7. 기록 화면

| # | Method | Path | 설명 |
|---|--------|------|------|
| 19 | GET | `/api/v1/users/me/running-records` | 내 러닝 기록 목록(기간 필터, 캘린더용) — 사용 화면: 기록, 피드 작성(템플릿 선택) |
| 20 | GET | `/api/v1/running-records/{runningRecordId}` | 기록 상세 (경로·구간 포함) |

### 8. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 21 | GET | `/api/v1/feeds` | 피드 목록, `tab=FRIENDS\|ALL`, 무한 스크롤 |
| 22 | GET | `/api/v1/feeds/{feedId}` | 피드 단건 — 사용 화면: 푸시 랜딩, 프로필 그리드 탭 |
| 23 | POST | `/api/v1/feeds/{feedId}/like` | 좋아요 (응답에 갱신 카운트) |
| 24 | DELETE | `/api/v1/feeds/{feedId}/like` | 좋아요 취소 |
| 25 | GET | `/api/v1/feeds/{feedId}/comments` | 댓글 목록 (등록순, 답글 제외) |
| 26 | POST | `/api/v1/feeds/{feedId}/comments` | 댓글/답글 작성 (`parentCommentId` 옵션, depth 1 제한) |
| 27 | PATCH | `/api/v1/comments/{commentId}` | 댓글 수정 (작성자 본인만) |
| 28 | GET | `/api/v1/comments/{commentId}/replies` | 답글 지연 로딩 ("답글 N개 보기") |
| 29 | DELETE | `/api/v1/comments/{commentId}` | 댓글 삭제 (작성자 or 피드 소유자, 레딧 방식) |
| 30 | POST | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 |
| 31 | DELETE | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 취소 |

### 9. 피드 작성 페이지 (+프로필의 피드 편집) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 32 | POST | `/api/v1/feeds/images/presigned-url` | 피드 이미지 업로드 URL 발급 (여러 장) |
| 33 | POST | `/api/v1/feeds` | 피드 작성 (텍스트/이미지 최소 1, 공개범위, 기록 템플릿 `runningRecordId`) |
| 34 | PATCH | `/api/v1/feeds/{feedId}` | 피드 수정 (내용·이미지·공개범위) — 사용 화면: 프로필(피드 편집) |
| 35 | DELETE | `/api/v1/feeds/{feedId}` | 피드 삭제 (소프트delete) |

### 10. 프로필 페이지 (본인/타인)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 36 | GET | `/api/v1/users/me` | 내 기본 정보 — 사용 화면: 전역 |
| 37 | GET | `/api/v1/users/{userId}` | 프로필 요약 (기본 정보·친구 수·친구 상태) |
| 38 | GET | `/api/v1/users/{userId}/feeds` | 피드 그리드 (경량: 썸네일+장수) **[MVP 제외]** |
| 39 | POST | `/api/v1/users/{userId}/friend-request` | 친구 요청 — 사용 화면: 프로필, 사용자 검색 |
| 40 | DELETE | `/api/v1/users/{userId}/friend-request` | 요청 취소(보낸 쪽) · 거절(받은 쪽) |
| 41 | POST | `/api/v1/users/{userId}/friend` | 친구 요청 수락 |
| 42 | DELETE | `/api/v1/users/{userId}/friend` | 친구 삭제 |
| 43 | GET | `/api/v1/users/me/friends` | 내 친구 목록 (+이름 검색) |
| 44 | GET | `/api/v1/users/me/friend-requests` | 받은 친구 요청 목록 |
| 45 | GET | `/api/v1/users/{userId}/colors` | 컬러 컬렉션 (마스터 전체 + 획득 여부) **[MVP 제외]** |
| 46 | GET | `/api/v1/users/search` | 사용자 검색 — 친구 추가 진입점 (`?q=검색어`) |

### 11. 프로필 편집 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 47 | POST | `/api/v1/users/me/profile-image/presigned-url` | 프로필 사진 업로드 URL 발급 |
| 48 | PATCH | `/api/v1/users/me/profile-image` | 업로드한 사진 반영 — S3 존재·소유자 검증 |
| 49 | GET | `/api/v1/users/{userId}/profile-image` | 프로필 사진 URL 조회 — 인증 불필요 |
| 50 | DELETE | `/api/v1/users/me/profile-image` | 프로필 사진 삭제 — S3 객체는 남기고 키 연결만 끊음 |
| 51 | GET | `/api/v1/users/me/profile` | 프로필 편집용 조회 — 소개글·성별·생일·키·몸무게 |
| 52 | PATCH | `/api/v1/users/me/profile` | 프로필 수정 — 소개글·성별·생일·키·몸무게 부분 수정 |
| 53 | PATCH | `/api/v1/users/me/nickname` | 닉네임 변경 (중복 시 409) |
| 54 | POST | `/api/v1/users/nickname/availability` | 닉네임 중복 확인 — 사용 화면: 프로필 편집, 온보딩 |

### 12. 설정 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 55 | GET | `/api/v1/users/me/account` | 계정 정보 — 이메일 + 로그인 수단(비밀번호 변경 노출 판정) |
| 56 | PATCH | `/api/v1/users/me/password` | 비밀번호 변경 (로컬 계정만) |
| 57 | GET | `/api/v1/users/me/settings` | 알림 on/off(단일) + 프로필 공개범위 조회 |
| 58 | PATCH | `/api/v1/users/me/settings` | 설정 변경 |
| 59 | DELETE | `/api/v1/users/me` | 회원탈퇴 (스냅샷→하드delete, 테이블별 정책) |

**합계: REST 58개 + SSE 스트림 1개(이벤트 3종) + WebSocket 채널 1개(메시지 7종 + ack 2종 + 헬스 체크 2종)**

> 번호는 표의 순서를 그대로 따른다 — 결번을 두지 않는다. 중간에 API가 생기면 이후 번호를 밀고, 번호로 상호 참조하는 노션 명세도 함께 갱신한다.

---

# 상세 명세

## 0. 공통 규칙

- **refreshToken**: 바디로 전달하고 클라이언트 Keychain/Keystore에 보관한다.
- **경로**: 저장 원본은 Google Encoded Polyline, precision 5(소수점 5자리, 약 1m)다. **기록 하나를 크게 그리는 화면(6-1·6-2·7-2)은 서버가 풀어 좌표 배열 `routes`로 내리고**(6-2만 전체가 아니라 구간별로 잘라 `splits[].routes`에 싣는다), 목록·카드처럼 한 응답에 여러 건이 실리는 곳(7-1·8-1)은 `routePolyline` 문자열 그대로 내린다. 어느 쪽이든 정밀도는 precision 5를 넘지 않는다. **`route`라는 단수 필드는 어디에도 두지 않는다** — 경로 필드는 `routes` 하나로 통일한다.
- **좌표 배열 형식**: `[[위도, 경도], [위도, 경도], …]`. 안쪽 배열은 항상 **위도가 먼저**다 — GeoJSON은 경도가 먼저라 반대이므로 그 관례를 따르지 않는다. 단일 지점(`startPoint` 등)도 같은 `[위도, 경도]` 두 칸 배열이다. 키 이름을 반복하지 않아 점 수백 개를 실어도 응답이 작다.
- **친구 관계**: 토글이 아니며 요청·수락·삭제를 10-4~10-6으로 나눈다.
- **이미지 업로드 공통(Presigned)**: ① 업로드 URL 발급 API → ② 클라가 S3에 직접 업로드 → ③ 반환받은 `key`(또는 완료 API)를 본 API에 전달
- **탈퇴 유저 표시**: 작성자·러닝 참가자는 `{ "userId": "550e8400-...", "nickname": "탈퇴한 사용자", "profileImageUrl": null, "isDeleted": true }`로 반환한다(`userId`는 유지).
- **값이 없는 필드**: 조회 응답에서는 `null`이다(`profileImageUrl`·`introduction`·`friendStatus` 등). 수정 응답(11-2·11-6·11-7)은 보낸 필드만 담아 돌려주므로 그쪽의 `null`은 "보내지 않았다"를 뜻한다.
- **`[MVP 제외]` 표기**: 지금 만들지 않는 엔드포인트. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이며, 차수(1차·2차)는 적지 않는다.

### 공통 에러 응답

인증 필요(`인증: 필요`) API → **401**, 모든 API → **400**·**500** 공통 발생. 각 엔드포인트 명세엔 특유 에러만 표기. 검증 실패 시 `code`는 `INVALID_REQUEST` 공통이고 `message`로 사유를 구분한다.

검증에 실패한 필드가 둘 이상이거나 한 필드에서 제약이 둘 이상 깨지면 `message`는 각 사유를 공백으로 이어 붙인 한 문자열이며, **문장 순서는 보장하지 않는다**. 각 엔드포인트의 400 예시는 사유를 하나씩 보여주는 것이지 나올 수 있는 조합을 모두 나열한 것이 아니다. 클라이언트는 `message`를 문서 문구와 정확히 비교하거나 파싱해 어느 필드가 틀렸는지 역추적하지 않는다 — 필드 단위 안내는 클라이언트 자체 검증으로 처리하고, 서버 `message`는 그대로 노출한다.

- **에러 (401 Unauthorized — 인증 실패)**

```json
{
  "code": "TOKEN_EXPIRED",
  "message": "액세스 토큰이 만료되었습니다."
}

{
  "code": "TOKEN_BLOCKED",
  "message": "로그아웃된 액세스 토큰입니다."
}

{
  "code": "INVALID_TOKEN",
  "message": "유효하지 않은 토큰입니다."
}

{
  "code": "AUTHENTICATION_REQUIRED",
  "message": "인증이 필요합니다."
}
```

- **에러 (400 Bad Request — 요청 검증)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}

{
  "code": "MALFORMED_REQUEST_BODY",
  "message": "요청 본문을 읽을 수 없습니다."
}
```

- **에러 (500 Internal Server Error)**

```json
{
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 오류가 발생했습니다."
}
```

## 1. 인증·온보딩

### 1-1. `POST /api/v1/auth/email/verifications` — 이메일 인증번호 발급

로컬 회원가입 3단계(인증번호 발급 → 인증번호 확인 → 가입) 중 1단계. 입력한 이메일로 **6자리 숫자 인증 코드**를 메일 발송.

- **Request**

```json
{
  "email": "example@example.com"   // 필수
}
```

- **Response `204 No Content`** — 본문 없음

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "올바른 이메일 형식이 아닙니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이메일은 필수입니다."
}
```

- **에러 (409 Conflict)**

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **에러 (429 Too Many Requests)**

```json
{
  "code": "EMAIL_VERIFICATION_COOLDOWN",
  "message": "인증 메일을 방금 보냈습니다. 잠시 후 다시 시도해 주세요."
}

{
  "code": "EMAIL_VERIFICATION_DAILY_LIMIT_EXCEEDED",
  "message": "하루 인증 메일 발송 횟수를 초과했습니다."
}
```

- **에러 (503 Service Unavailable)**

```json
{
  "code": "EMAIL_SEND_FAILED",
  "message": "인증 메일 발송에 실패했습니다. 잠시 후 다시 시도해 주세요."
}
```

- **인증**: 불필요

### 1-2. `POST /api/v1/auth/email/verifications/confirm` — 이메일 인증번호 확인

메일로 받은 코드를 검증하고, 회원가입에 쓸 **인증 티켓(`verificationTicket`)** 을 발급.

- **Request**

```json
{
  "email": "example@example.com",   // 필수
  "code": "123456"                  // 필수 — 6자리 숫자(^\d{6}$), 공백 불가
}
```

- **Response `200 OK`**

```json
{
  "verificationTicket": "_YUW5lsbzTgNYp8-B6p73LnLjP6a4YgWlcQnaauHwhc"
}
```

- `verificationTicket`: 회원가입에 사용할 인증 티켓 (URL-safe Base64, 43자). 발급 후 **30분** 유효, **1회용**

- **에러 (400 Bad Request)** — 인증 코드 자체의 실패는 별도 `code`

```json
{
  "code": "INVALID_REQUEST",
  "message": "올바른 이메일 형식이 아닙니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이메일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "인증 코드는 6자리 숫자입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "인증 코드는 필수입니다."
}

{
  "code": "EMAIL_VERIFICATION_NOT_FOUND",
  "message": "인증 코드가 만료되었습니다. 다시 요청해 주세요."
}

{
  "code": "INVALID_VERIFICATION_CODE",
  "message": "인증 코드가 올바르지 않습니다."
}
```

- **에러 (429 Too Many Requests)**

```json
{
  "code": "TOO_MANY_VERIFICATION_ATTEMPTS",
  "message": "인증 시도 횟수를 초과했습니다. 코드를 다시 요청해 주세요."
}
```

- **인증**: 불필요

### 1-3. `POST /api/v1/auth/signup` — 로컬 회원가입

이메일 인증(1-1 → 1-2)으로 받은 티켓으로 가입. 이메일은 티켓에서 확인한 값을 쓰므로 요청에 담지 않는다.

- **Request**

```json
{
  "verificationTicket": "_YUW5lsbzTgNYp8-B6p73LnLjP6a4YgWlcQnaauHwhc",   // 필수 — 인증 확인 API에서 받은 티켓 원문
  "password": "********"                                                  // 필수 — 6~16자, 영문·숫자·특수문자 각 1자 이상 (확인 일치 검증은 클라이언트)
}
```

- **Response `201 Created`** — **자동 로그인** (로그인과 동일 형태로 토큰 발급)

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "accessToken": "ey...",
  "refreshToken": "ey..."
}
```

- **`isOnboarded`는 내리지 않는다** — 온보딩 완료 여부는 `GET /users/me`로 판정한다. 인증 응답 셋(회원가입·로그인·소셜 로그인)이 모두 같다

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "이메일 인증 티켓은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 6자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 필수입니다."
}
```

- **에러 (403 Forbidden)**

```json
{
  "code": "EMAIL_NOT_VERIFIED",
  "message": "이메일 인증이 만료되었습니다. 다시 인증해 주세요."
}
```

- **에러 (409 Conflict)**

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **인증**: 불필요

### 1-4. `POST /api/v1/auth/login` — 로컬 로그인

- **Request** (둘 다 필수)

```json
{
  "email": "...",
  "password": "..."
}
```

- **이메일 대소문자**: 서버는 입력값을 그대로 조회한다 — 가입 시 소문자로 정규화해 저장하므로 **클라이언트가 소문자로 변환해 보낸다**. 대문자가 섞이면 `401 INVALID_CREDENTIALS`

- **Response `200 OK`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "accessToken": "ey...",
  "refreshToken": "ey..."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "올바른 이메일 형식이 아닙니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이메일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 필수입니다."
}
```

- **에러 (401 Unauthorized — 이메일/비밀번호 불일치)**

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호가 일치하지 않습니다."
}
```

- **인증**: 불필요

### 1-5. `POST /api/v1/auth/oauth/google` / 1-6. `POST /api/v1/auth/oauth/kakao` — 소셜 로그인 (인가 코드 방식)

> **서버 매핑은 `POST /auth/oauth/{provider}` 하나다.** 위 두 경로는 클라이언트가 실제로 호출하는 구체 URL이다. `{provider}`는 `google`·`kakao`(대소문자 무시). 지원하지 않는 값은 404가 아니라 **400 `UNSUPPORTED_PROVIDER`**로 응답한다 — 경로 자체는 매칭되기 때문이다.

- **Request** (둘 다 필수, 구글·카카오 공통)

```json
{
  "authorizationCode": "...",
  "codeVerifier": "..."
}
```

- **동작**: 서버가 provider에 인가 코드 교환(PKCE `codeVerifier` 검증) → 유저 정보 조회 → `provider_id`로 `oauth_users` 조회, 없으면 생성(회원가입) → 자체 토큰 발급
- **Response `200 OK`**: 1-4 로그인과 동일 형태. 최초 가입 여부와 무관하게 토큰을 발급한다
- **에러 (401 Unauthorized — 코드 교환 실패 — 위조·만료·PKCE 불일치)**

```json
{
  "code": "OAUTH_CODE_EXCHANGE_FAILED",
  "message": "소셜 로그인에 실패했습니다. 다시 시도해 주세요."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "인가 코드는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "코드 검증값은 필수입니다."
}

{
  "code": "UNSUPPORTED_PROVIDER",
  "message": "지원하지 않는 로그인 제공자입니다."
}
```

- **에러 (403 Forbidden — 카카오 이메일 제공 미동의 — 가입 거부)**

```json
{
  "code": "OAUTH_EMAIL_NOT_PROVIDED",
  "message": "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다."
}
```

- **에러 (409 Conflict — 소셜 최초 가입인데 이메일이 기존 로컬 계정과 겹침)** — 자동 연동하지 않는다. 클라는 로컬 로그인으로 안내

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **인증**: 불필요

### 1-7. `POST /api/v1/auth/refresh` — 토큰 재발급

- **Request**: `{ "refreshToken": "ey..." }` (필수)
- **Response `200 OK`**

```json
{
  "accessToken": "ey...",
  "refreshToken": "ey..."
}
```

- 클라는 accessToken·refreshToken 둘 다 갱신 저장
- **에러 (400 Bad Request — 리프레시 토큰 누락)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "리프레시 토큰은 필수입니다."
}
```

- **에러 (401 Unauthorized — 만료·위조 → 재로그인 유도)**

```json
{
  "code": "INVALID_REFRESH_TOKEN",
  "message": "리프레시 토큰이 유효하지 않습니다. 다시 로그인해 주세요."
}
```

- **인증**: 불필요 (refreshToken 자체가 자격증명)

### 1-8. `POST /api/v1/auth/logout` — 로그아웃

- **Request**: 본문 없음 — 서버가 요청 토큰으로 본인 식별
- **동작**: ① **해당 access 토큰을 서버 차단(블랙리스트)** 처리해 만료 전이라도 무효화 (이후 그 토큰 요청은 `401 TOKEN_BLOCKED`) ② **저장된 리프레시 토큰을 삭제** — 남겨두면 1-7로 새 access 토큰을 받아 로그아웃이 무효가 된다
- **로그인 세션은 계정당 하나**라 이 삭제가 그 계정의 모든 기기에 미친다. 기기별 세션은 `feature-spec.md` 설정 페이지 절 참고 (**[MVP 제외]**)
- **Response**: `204 No Content`

- **인증**: 필요

### 1-9. `POST /api/v1/users/onboarding` — 온보딩 입력

- **Request** (전부 필수)

```json
{
  "nickname": "완두콩",
  "gender": "MALE",                  // MALE | FEMALE
  "birthday": "1998-12-16",
  "averagePaceSecondsPerKm": 359,    // 초/km 정수 (5'59") — 초기값. 이후 러닝 기록 기반 서버 자동 갱신 (수정 UI 없음)
  "weightKg": 77,
  "heightCm": 175
}
```

- **키·몸무게는 소수점 첫째 자리로 정규화한다** — 둘째 자리 이하는 반올림해 저장하며 범위는 20 이상 300 이하다(11-6과 같다)
- **약관 동의**: 별도 요청 필드 없음 — **가입 흐름 첫 화면**에서 받고 가입 자체를 동의로 갈음한다(근거는 `feature-spec.md` 약관 동의 화면 절)

- **Response `201 Created`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440001",
  "nickname": "완두콩"
}
```

- **에러 (409 Conflict)**

```json
{
  "code": "NICKNAME_ALREADY_EXISTS",
  "message": "이미 사용 중인 닉네임입니다."
}

{
  "code": "ALREADY_ONBOARDED",
  "message": "이미 온보딩을 완료한 계정입니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 2자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "성별은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "성별은 MALE 또는 FEMALE이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 미래일 수 없습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 1900년 1월 1일 이후여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "평균 페이스는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "평균 페이스는 120초 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "평균 페이스는 1800초 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 20kg 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 300kg 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 20cm 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 300cm 이하여야 합니다."
}
```

- **인증**: 필요

## 2. 공통 — 디바이스/푸시

### 2-1. `POST /api/v1/devices` — 디바이스 등록/갱신

- **화면**: 로그인 직후 전역 (푸시 수신 준비)
- **Request**

```json
{
  "pushToken": "fcm-token-...",   // 필수
  "platform": "IOS",              // 필수 — IOS | ANDROID
  "deviceId": "device-uuid-...",  // 필수 — 기기 고유 식별자
  "appVersion": "1.0.0"           // 선택
}
```

- **동작**: `deviceId` 기준 upsert(없으면 생성)
- **Response**: `204 No Content`

- **인증**: 필요

## 3. 홈 화면 — 날씨

홈 화면 날씨는 클라이언트가 키 없는 무료 날씨 API를 직접 호출한다. 서버 API는 없다.

## 4. 친구 초대 [MVP 제외]

엔드포인트와 방 생명주기는 정의하지 않는다. `INVITE`·`INVITED`는 미래 예약값이다.

- 초대방은 `running_rooms.type='INVITE'`로 새로 만든다 — 랜덤 매칭 후보 스캔(`type='MATCH'`)에 잡히면 모르는 사람이 배정된다.
- 방장은 없으며 현재 방 참가자 누구나 친구를 초대할 수 있다.
- 초대 발송 수는 제한하지 않는다.
- 방은 최대 4명이며 먼저 수락한 순서대로 입장시키고 이후 수락은 막는다.

## 5. 매칭·러닝 실시간 통신

**구간마다 통신 방식이 다르다.**

| 구간 | 방식 | 이유 |
|---|---|---|
| 매칭 신청 ~ 대기방 (5-A·5-B) | **REST + SSE** `/api/v1/running-matches/stream` | 클라가 보내는 건 신청·취소 둘뿐이고 나머지는 전부 서버 푸시다 — 양방향 채널을 쓸 이유가 없다 |
| 러닝 구간 (5-C·5-D) | **WebSocket** `/api/v1/ws/running` | 위치를 주기 발신하는 고빈도 양방향 구간 |

**솔로 러닝도 같은 WebSocket을 쓴다.** 매칭을 거치지 않을 뿐 좌표 수집·저장 경로는 동일하다. 시작할 때 `POST /running-rooms/solo`로 방을 만들어 `runningRoomId`를 받은 뒤 WS에 연결한다(5-C의 카운트다운은 건너뛴다 — 맞출 상대가 없다).

### `POST /api/v1/running-rooms/solo` — 솔로 러닝 개시

- **클라가 만들 수 있는 방은 솔로뿐이다.** 매칭 방은 신청 시 서버가 만들므로 요청 대상이 아니다. 그래도 경로에 `/solo`를 박아 종류를 드러낸다 — 나중에 초대 방이 붙어도 `type`을 본문으로 받지 않고 경로로 가른다
- **Request 본문이 없다.** 목표 거리도 받지 않는다 — 솔로는 사용자가 끝내야 끝나므로 방의 `target_distance`는 null이다. 페이스는 온보딩 값을 쓴다
- **Response `201 Created`**

```json
{
  "runningRoomId": 126
}
```

- **동작**: `running_rooms` 행을 `type='SOLO'`, `status='MATCHED'`, `max_player_count=1`, `current_player_count=1`로 만들고 본인 `running_players(status='JOINED')`와 배정 세션을 함께 만든다
  - **`STARTED`·`RUNNING`은 이 API가 만들지 않는다.** 모집을 건너뛴 확정 상태까지만 만들고, 시작 전이는 WS `RUNNING_START`가 일으킨다(5-C). 솔로 전용 스케줄러는 두지 않는다 — `start_at`이 개시 시각이라 `RUNNING_START`가 도착하는 순간 이미 지나 있다
- 이 방은 `GET /running-matches/slots`의 대기 인원 집계에 포함되지 않는다(`type='SOLO'`로 제외). 모집 중인 자리가 아니다
- **에러 (409 Conflict)**: `RUNNING_ALREADY_IN_PROGRESS` — 진행 중인 러닝이나 활성 매칭 신청이 있다
- **인증**: 필요

**솔로는 SSE를 사용하지 않는다.** POST 응답으로 `MATCHED` 방 ID를 받은 뒤 WS에 연결해 `RUNNING_START`를 보내고 `RUNNING_STARTED` ack를 받는다 — 카운트다운만 건너뛸 뿐 매칭과 같은 순서이며, 보내는 메시지도 똑같다.

- **DB row 트리거** — `running_room_sessions`가 신청과 방을 잇는다(신청 즉시 방이 생기므로 배정 row도 항상 있다). 현재 속한 방은 `is_connected=true`인 행이다
  - row 생성 = 매칭 신청·솔로 개시 시. 새 방을 만들거나 기존 모집 중인 방에 배정된다
  - 취소·나가기 요청 시 서버가 방 상태로 분기한다(5-A 참고). 어느 쪽이든 배정 행은 `is_connected=false`로 남아 이력이 된다
  - 참가자가 모두 빠져 `current_player_count`가 `0`이 되면 방을 닫는다 — **시작 전이면 항상 `CANCELLED`**, **시작 후면 유효 기록이 하나라도 저장됐을 때만 `FINISHED`**이고 없으면 `CANCELLED`다. 각 참가자 row는 유지하되 취소·이탈 시각을 `deleted_at`에 기록하고 배정 행은 `is_connected=false`로 남긴다

### 5-A. 매칭 중 (홈 → 매칭 대기 화면)

#### `GET /api/v1/running-matches/stream` — 매칭 이벤트 스트림 (SSE)

- **인증**: 필요 / **Content-Type**: `text/event-stream`
- **연결 시점**: 매칭 신청 성공 직후. 활성 신청이 없으면 연결하지 않는다 — 서버가 보낼 것이 없다.
  - 앱 재시작·포그라운드 복귀 시엔 `GET /users/me/running-match`로 활성 여부를 확인하고 있으면 재연결한다.
- **종료 시점**: `RUNNING_STARTED` ack 뒤 클라이언트가 닫는다. 매칭 취소·실패 시에는 서버가 닫는다.
- **연결을 화면 생명주기에 묶지 않는다.** 홈을 벗어나도 스트림은 살아 있어야 한다(근거는 `feature-spec.md` 매칭·러닝 설계 절)
- **이벤트 형식** — 타입은 SSE `event` 필드로, 본문은 `data`에 JSON으로 싣는다

```
event: MATCH_ROOM_UPDATED
data: {"runningRoomId":125,"status":"MATCHED", ...}
```

| 이벤트 | 시점 |
|---|---|
| `MATCH_PLAYERS_UPDATED` | 대기 인원 변동 — `data` = `RoomInfo` |
| `MATCH_STARTED` | 매칭 확정 — `data` = `RoomInfo` |
| `MATCH_ROOM_UPDATED` | 방 정보 갱신·취소·러닝 시작 — `data` = `RoomInfo` |

- 연결 직후 서버가 현재 상태를 보낸다. 각 이벤트는 변경분이 아니라 해당 객체의 전체 상태를 담으므로 `Last-Event-ID` 재개는 사용하지 않는다.
- **keep-alive**: 주기적으로 주석 라인(`: ping`)을 보내 프록시 유휴 타임아웃을 막는다. 주기는 운영값.
- 스트림은 수신 전용이라 요청 실패라는 개념이 없다 — 오류는 신청·취소 REST 응답으로 전달된다.

#### `GET /api/v1/running-matches/slots` — 시간대별 대기 인원

- **화면**: 매칭 정보 입력 모달 — 시간 선택 박스에 "19:00 · 3명 대기 중"처럼 표시한다
- **Query**: `date`(YYYY-MM-DD, 생략 시 오늘), `targetDistanceMeters`(선택 — 주면 해당 거리 조건만 집계)
- **Response `200 OK`**

```json
{
  "slots": [
    { "scheduledStartAt": "2026-07-25T18:00:00", "waitingCount": 0, "selectable": false },
    { "scheduledStartAt": "2026-07-25T18:30:00", "waitingCount": 3, "selectable": true },
    { "scheduledStartAt": "2026-07-25T19:00:00", "waitingCount": 1, "selectable": true }
  ]
}
```

- `waitingCount`는 아직 확정되지 않은 대기자 수다 — `type='MATCH' AND status='MATCHING'`인 방의 참가자만 센다. 이미 `MATCHED`된 방(들어갈 수 없는 자리)과 솔로 방(`type='SOLO'`)은 제외
- `selectable=false`는 마감이 지난 슬롯 — 목록에는 남기되 선택은 막는다
- **인증**: 필요

#### `POST /api/v1/running-matches` — 매칭 신청

- **화면**: 홈 (매칭 버튼)
- **Request**

```json
{
  "scheduledStartAt": "2026-07-25T19:00:00",
  "targetDistanceMeters": 5000
}
```

- **입력값은 정해진 선택지 안에서만 받는다** — 자유 입력이 아니다

| 필드 | 허용값 |
|---|---|
| `scheduledStartAt` | **18:00~22:00**, **30분 간격** (`18:00`, `18:30`, … `22:00`) |
| `targetDistanceMeters` | **3000 / 5000 / 10000** 셋 중 하나 |

- **활성 신청은 1개** — 이미 있으면 `409 RUNNING_ALREADY_IN_PROGRESS`. 마감이 지난 `MATCHING` 방은 먼저 `MATCHED`로 확정 처리하며, 확정된 신청도 활성이므로 재신청은 막힌다. 혼자 확정된 경우에는 페널티 없이 나갈 수 있고(5-B) 나가면 곧바로 다시 신청할 수 있다. 이 API로 만드는 방은 전부 공개 랜덤 매칭이라 공개 범위를 받지 않는다
- 페이스 조건은 입력받지 않음 — 서버가 보관한 사용자 평균 페이스 자동 사용
- **모집 인원도 입력받지 않음** — 서버가 2~4명 범위에서 자동 편성 (`desiredPlayerCount` 필드 없음)
- **Response `201 Created`** — 신청이 접수되면 `running_players` row와 `running_room_sessions` 배정 row가 생긴다. 같은 조건에 모집 중인 방이 있으면 거기 배정되고, 없으면 **1인 방**(`running_rooms`, `type='MATCH'`, `status='MATCHING'`, `max_player_count=4`, `current_player_count=1`)이 새로 생긴다
  - **응답 본문에 `runningRoomId`를 넣지 않는다.** 방은 있지만 매칭 단계의 클라는 방 ID로 호출할 곳이 없다 — 필요한 시점(참가자·방 갱신)에 SSE로 내려간다

```json
{
  "scheduledStartAt": "2026-07-25T19:00:00",
  "targetDistanceMeters": 5000,
  "closeAt": "2026-07-25T18:45:00"
}
```

- `closeAt`은 모집이 마감되는 시각 — 대기 배너의 "마감까지 남은 시간" 표시에 쓴다. 이 시각이 지나면 새 참가자가 들어올 수 없고 확정 판정이 돈다
  - **저장값이 아니라 서버가 `start_at - 운영 설정 오프셋`으로 계산해 내려주는 값이다.** `running_rooms.close_at`은 방이 닫힌 시각이라 이것과 다르다 — 이름이 겹치므로 주의
- **응답을 받은 뒤 SSE 스트림에 연결한다**
- **에러 (409 Conflict)**: `RUNNING_ALREADY_IN_PROGRESS` — 이미 활성 신청이나 확정된 방이 있다

```json
{
  "code": "RUNNING_ALREADY_IN_PROGRESS",
  "message": "이미 진행 중인 매칭이 있습니다."
}
```

- **에러 (409 Conflict)**: `MATCH_COOLDOWN` — 페널티 대상 이탈로 신청이 제한된 상태다. 응답에 해제 시각을 담는다

```json
{
  "code": "MATCH_COOLDOWN",
  "message": "매칭 또는 러닝 중 이탈해 일정 시간 신청이 제한됩니다.",
  "cooldownUntil": "2026-07-26T07:30:00"
}
```

- 솔로 러닝(`POST /running-rooms/solo`)은 이 제한을 받지 않는다
- **인증**: 필요

#### `DELETE /api/v1/users/me/running-match` — 매칭 취소·방 나가기 (겸용)

- **서버가 방 상태로 분기**
  - 대기 중(`MATCHING`) = 대기 취소(`deleted_at` 소프트 삭제). **본인이 마지막 참가자였으면 방도 `CANCELLED`**. 제재 없음
  - 확정 후(`MATCHED`) = 이탈(`status=MATCHED_LEFT_PENALTY` 또는 `MATCHED_LEFT_NO_PENALTY`, `deleted_at` 기록). 제재 대상 여부는 **이 시점에 모집 마감(`start_at - 오프셋`) + 유예, 그리고 `current_player_count`로 판정해 값에 굳힌다** — 혼자 남은 방(`1`)에서 나가면 유예가 지났어도 면제다. 쿨다운이 걸리는 경우에만 클라는 나가기 전에 그 사실을 안내한다
  - 남은 인원에게는 `MATCH_PLAYERS_UPDATED` 또는 `MATCH_ROOM_UPDATED`를 스트림으로 발신한다. **혼자 남아도 방은 취소하지 않는다**
- **시각으로 취소를 차단하지 않는다.** 시작 직전까지 호출할 수 있고 늦은 이탈은 쿨다운으로 다룬다
- **Response `204 No Content`** — 이후 클라는 SSE 스트림을 닫는다
- **에러 (404 Not Found)**: 활성 신청이 없다
- **인증**: 필요

#### `GET /api/v1/users/me/running-match` — 현재 매칭 상태 조회

- **화면**: 홈 진입·앱 재시작 — 스트림에 연결할지 판단하고 홈 상태를 그린다
- 스트림도 연결 직후 같은 정보를 보내지만 이 API를 따로 둔다 — **매칭을 걸지 않은 사용자가 대다수인데 전원에게 스트림을 여는 것은 서버 커넥션과 단말 배터리 양쪽에 부담**이라, 활성 신청이 있을 때만 연결한다
- **Response `200 OK`** — 아래 우선순위로 현재 상태를 반환한다

```json
{
  "state": "MATCHED",
  "runningRoomId": 125,
  "room": { ... }
}
```

- **`state`는 저장값이 아니라 파생값이다** — `running_players`와 방 상태·마감 시각으로 계산한다. `feature-spec.md`의 홈 화면 상태 표와 **같은 규칙**이며 이름만 한글/영문으로 다르다

| `state` | 조건 |
|---|---|
| `NONE` | 활성 신청이 없음 |
| `WAITING` | 방이 `MATCHING`이고 마감 전 |
| `MATCHED` | 방이 `MATCHED` — **인원 무관, 1인 확정도 여기 해당한다** |

- **`FAILED` 상태는 없다** — 마감은 인원과 무관하게 항상 `MATCHED`로 끝나므로 신청이 저절로 실패하는 경로가 없다. 취소는 사용자가 직접 한 행동이라 그 즉시 `NONE`으로 돌아간다
- 모집 마감이 지났지만 스케줄러가 아직 닫지 않은 `MATCHING` 방은 `MATCHED`로 판정한다 — 확정은 마감 시각에 일어난 사실이고 스케줄러는 반영이 늦을 뿐이다
- `room`은 `WAITING`·`MATCHED`일 때 `RoomInfo`로 채우고 `NONE`이면 null이다
- 클라이언트는 `MATCHED`에서 `players`가 1건인 경우를 **혼자 확정된 상태**로 그린다. 이때 나가기는 페널티가 없다(5-B)
- **인증**: 필요

#### `MATCH_PLAYERS_UPDATED` (SSE) — 매칭 참가자 갱신

- `data`는 `status='MATCHING'`인 `RoomInfo` 전체다. 현재 인원은 `players.length`로 계산한다.

- `runningRoomId`는 **항상 값이 있다**(신청 즉시 방에 배정된다). 다만 이 값이 "매칭이 확정됐다"는 뜻은 아니다. 확정 여부는 `MATCH_STARTED` 수신, `MATCH_ROOM_UPDATED.status`, 또는 `GET /users/me/running-match`의 `state`로 판단한다
- 방 취소(참가자 전원 이탈) 통지: 별도 이벤트 없음 — **`MATCH_ROOM_UPDATED`의 `status: "CANCELLED"`**로 전달. 이 SSE는 매칭 단계 채널이라 실제로는 시작 전 취소만 여기로 나간다

### 5-B. 매칭 방 (매칭완료 대기방)

#### 공통 객체 `RoomInfo` — 매칭방 전체 정보

세 SSE 이벤트와 현재 매칭 조회의 `room`은 아래 **동일 구조를 공유**한다.

```json
{
  "runningRoomId": 125,
  "status": "MATCHED",               // running_rooms.status: MATCHING|MATCHED|STARTED|FINISHED|CANCELLED — CANCELLED면 클라는 홈으로
  "scheduledStartAt": "2026-07-25T19:00:00",
  "closeAt": "2026-07-25T18:45:00",  // 모집 마감 시각 — start_at - 오프셋 계산값
  "targetDistanceMeters": 5000,
  "teamAveragePaceSecondsPerKm": 375,
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "status": "JOINED",              // PlayerStatus — 값 목록은 erd.md §6
      "profileImageUrl": "https://...",
      "introduction": "즐겁게 같이 달려요!",   // users.introduction
      "averagePaceSecondsPerKm": 360
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440013",
      "nickname": "철수",
      "status": "JOINED",
      "profileImageUrl": "https://...",
      "introduction": "천천히 오래 달려요.",
      "averagePaceSecondsPerKm": 390
    }
  ]
}
```

#### `MATCH_STARTED` (SSE) — 매칭 성사 통지

- `data` = `RoomInfo`. 수신 시 클라는 대기 화면 → 매칭방 화면으로 전환
- **발화 시점은 모집 마감(`start_at - 오프셋`)이다** — 방이 `MATCHING`→`MATCHED`로 넘어가는 순간 한 번. **방 생성 시점이 아니다** — 방은 신청 즉시 생기지만 그건 모집 시작이고, 그 구간의 인원 변동은 `MATCH_PLAYERS_UPDATED`가 담당한다. 자리가 다 차도 앞당겨 쏘지 않는다(`feature-spec.md` 확정 판정)

#### `MATCH_ROOM_UPDATED` (SSE) — 매칭방 정보 갱신

- `data` = `RoomInfo` 전체 재전송 — 방 정보가 갱신·취소되거나 서버가 방을 `STARTED`로 전환할 때

#### 방 나가기 — 별도 이벤트 없음

- 확정된 방에서 나가기도 **`DELETE /users/me/running-match`** 사용 (5-A 참고 — 서버가 방 상태로 분기)
- 나간 사람만 `MATCHED_LEFT_*` 처리, 방은 유지되고 그대로 러닝을 진행한다
- **확정 후 이탈에는 페널티가 붙는다** — 모집 마감(`start_at - 오프셋`) + 유예 이후에 나가면 일정 시간 매칭 신청이 제한된다(`409 MATCH_COOLDOWN`). 쿨다운 만료는 `deleted_at`으로 잰다(`feature-spec.md` 페널티 절)
- **혼자 남은 방은 예외다** — 이탈 시점 `current_player_count`가 `1`이면 면제(`MATCHED_LEFT_NO_PENALTY`)다. 1인으로 확정된 방과 이탈로 혼자 남은 방 모두 해당하며, 나가면 활성 신청이 끝나 곧바로 재신청할 수 있다

#### 대기방 참가자 목록 — 별도 조회 없음

- `RoomInfo`가 참가자 전체를 담고 있고 변동 시마다 재전송되므로, 목록만 따로 받는 요청은 두지 않는다
- 앱 재시작 등으로 스트림이 끊겼다면 `GET /users/me/running-match`가 같은 정보를 돌려준다

### 5-C. 러닝 카운트 다운 — SSE에서 WebSocket으로

**3-2-1 카운트다운은 클라이언트가 기기 시각으로 표시한다.** 실제 시작 가능 여부는 서버 방 상태가 결정한다.

절차는 다음과 같다.

1. `scheduledStartAt` 직전(리드타임은 운영값)에 클라가 WS를 연결한다
2. 기기 시각으로 **시작 3초 전부터 3-2-1 카운트다운**(화면·음성·햅틱)을 표시하고 뒤로가기를 차단한다
3. 서버가 `scheduledStartAt`에 방을 `STARTED`, 남아 있는 참가자를 `RUNNING`으로 바꾸고 `MATCH_ROOM_UPDATED`를 보낸다. 클라는 이를 받은 뒤 러닝 화면으로 전환해 `RUNNING_START`를 보낸다
4. `RUNNING_STARTED` ack를 받으면 SSE 스트림을 닫는다

#### WebSocket 연결 — `/api/v1/ws/running`

- **연결**: `wss://.../api/v1/ws/running` + `Authorization: Bearer {accessToken}`
- **인증 실패**: 업그레이드를 거부하고 **HTTP 401**로 응답한다 — 연결이 서기 전이라 `ERROR` 프레임을 쓸 수 없다. 본문은 REST 에러 포맷과 같다. 클라는 `POST /auth/refresh` 후 재연결하고, 다시 실패하면 재로그인으로 보낸다. 같은 이유로 아래 `ERROR`의 code 목록에는 인증 코드가 없다
- **토큰은 핸드셰이크에서 한 번만 검증한다** — 연결 유지 중 `accessToken`이 만료돼도 끊지 않는다. 러닝 구간이 토큰 수명보다 길 수 있어 중간에 끊으면 트랙이 갈린다. 단 로그아웃·탈퇴로 토큰이 차단되면 서버가 연결을 닫는다. 클라는 REST용 토큰을 평소대로 갱신하고, 새 토큰은 재연결할 때만 쓴다
- **중복 연결은 마지막 것만 남긴다** — 같은 사용자의 새 연결이 들어오면 서버가 기존 연결을 close code `4001`로 닫는다. 기기 전환·앱 재시작 때 이전 소켓이 남아 있을 수 있는데 둘 다 살려두면 같은 `(runningRoomId, userId, sequence)`에 서로 다른 트랙이 섞인다. `4001`을 받은 클라는 재연결하지 않는다 — 다른 기기가 이어받은 것이다
- **keep-alive**: 클라가 주기적으로 `HEALTH_CHECK`(C→S)를 보내고 서버가 `HEALTH_CHECKED`(S→C)로 응답한다. 둘 다 `data`는 비운다. **유휴 상태가 서버 설정 시간(운영값)을 넘으면 서버가 연결을 닫는다** — 좌표를 계속 보내는 러닝 중에는 별도 신호가 필요 없고, 시작 전 대기 구간에서 의미가 있다. 프록시 유휴 타임아웃을 막는 목적은 SSE와 같다
- **연결이 끊겨도 러닝은 끝나지 않는다** — 방·참가자 상태는 그대로 두고 재연결을 기다린다. 5-D의 종료 타임아웃은 **마지막 좌표 수신 시각** 기준이라 연결 상태와 축이 다르다(`running_room_sessions.is_connected`도 방 배정 여부이지 접속 여부가 아니다)
- **메시지 공통 형식**

```json
{
  "event": "...",
  "data": { ... }
}
```

- 봉투 키는 `event`다. 아래 각 메시지 절이 보여주는 JSON은 이 봉투의 `data`에 들어가는 부분이다

- **ack 규칙**: 상태가 걸린 요청에만 — `RUNNING_START`→`RUNNING_STARTED`, `RUNNING_FINISH`→`RUNNING_FINISHED`
  - **`RUNNING_LOCATION_UPDATE`는 ack 없음**
  - ack의 `data`는 비운다. `RUNNING_STARTED`에 진입·재연결 화면 복구용 스냅샷을 싣는 예외를 둘 예정이지만 아직 구현 전이다(5-C)
- **`ERROR` (S→C)** — WS 요청 실패 통지. REST 에러 포맷과 동일 계열

```json
{
  "event": "ERROR",
  "data": {
    "code": "ROOM_NOT_FOUND",
    "message": "러닝 정보를 찾을 수 없습니다.",
    "sourceType": "RUNNING_LOCATION_UPDATE"
  }
}
```

- **code** — 봉투 단계와 처리 단계로 나뉜다. 앞의 셋은 `data`를 읽기도 전에 나가므로 `sourceType`이 null일 수 있다

  | code | 언제 |
  |---|---|
  | `MALFORMED_MESSAGE` | 봉투 JSON을 파싱하지 못함 |
  | `MISSING_MESSAGE_TYPE` | `event`가 비어 있음 |
  | `UNSUPPORTED_MESSAGE_TYPE` | 모르는 `event`이거나 S→C 전용 타입을 클라가 보냄 |
  | `INVALID_REQUEST` | `data` 검증 실패 |
  | `RUNNING_NOT_STARTED` | `RUNNING_START` 없이 러닝 중 메시지를 보냄 — 형식은 맞지만 서버에 정해진 방이 없다 |
  | `RUNNING_SESSION_UNAVAILABLE` | 외부 저장소 장애로 세션을 등록하지 못함 — 러닝이 시작되지 않았으니 잠시 뒤 `RUNNING_START`를 재시도한다 |
  | `RUNNING_TRACK_UNAVAILABLE` | 외부 저장소 장애로 좌표를 저장하지 못함 — 러닝은 계속된다 |
  | `ROOM_NOT_FOUND` | 방 없음 |
  | `NOT_ROOM_PLAYER` | 이 방 참가자가 아님 |
  | `INVALID_ROOM_STATE` | 현재 상태에서 불가한 요청 |

- **`ERROR`로는 연결을 끊지 않는다.** 잘못된 메시지 하나 때문에 러닝 전체가 끊기면 안 되므로, 오류를 돌려주고 연결은 유지한다

#### `RUNNING_START` (C→S) — 러닝 준비 일괄 처리

```json
{
  "runningRoomId": 125
}
```

- **WS 연결 후 클라가 보내는 첫 메시지다.** 채널 등록·방 시작·참가자 시작을 이 하나가 다 한다 — 클라는 최초 진입인지 재연결인지 구분하지 않고 언제나 같은 메시지를 보낸다
  - **의도적으로 나간 사람은 돌아오지 못한다.** 나가기는 활성 신청을 닫으므로(`running_players.deleted_at`) 이후 `RUNNING_START`는 참가자 확인 단계에서 `NOT_ROOM_PLAYER`로 거부된다. 반면 네트워크가 끊긴 것뿐이면 신청도 배정도 그대로라 이어 뛴다 — 서버는 끊긴 원인을 추측하지 않고 나가기 요청이 있었는지만 본다
- `runningRoomId`는 이미 손에 있다 — 솔로는 `POST /running-rooms/solo`의 201 응답, 매칭은 SSE `RoomInfo`에서 받는다
- **서버 처리 순서**

  | | 하는 일 | 이미 그 상태면 |
  |---|---|---|
  | 1 | 활성 신청이 있고 이 방 참가자인지 확인 | 아니면 `NOT_ROOM_PLAYER` — **나간 사람은 신청이 닫혀 여기서 걸린다** |
  | 2 | 배정(`is_connected`)이 끊겨 있으면 거부한다 | `INVALID_ROOM_STATE` — 1번을 통과한 뒤 남는 방어선이다 |
  | 3 | 방이 `MATCHED`면 `STARTED`로 올린다 | 통과 |
  | 4 | 참가자가 `JOINED`면 `RUNNING`으로 올린다 | 통과 |
  | 5 | WS 세션을 방에 등록하고 세션이 `runningRoomId`를 기억한다(브로드캐스트 대상·이후 메시지의 방) | 덮어쓴다 |
  | 6 | ack 전송 | — |

- **3번에 `type` 분기가 없다.** 매칭은 `start_at`에 스케줄러가 이미 올려놨으니 통과하고, 솔로는 `start_at`이 개시 시각(과거)이라 여기서 올라간다. 같은 코드가 두 종류를 다 덮는다
- **전 단계가 멱등하다.** 중복 `RUNNING_START`는 아무 상태도 다시 바꾸지 않고 ack만 재전송한다
- **거부**: `start_at`이 아직 안 됐으면 `INVALID_ROOM_STATE`(매칭에서 미리 쏘는 것 차단). 방이 `FINISHED`·`CANCELLED`여도 같은 코드
- **ack**: `RUNNING_STARTED` — **현재 `data`는 비어 있다(`{}`).** 재연결마다 REST를 다시 때리지 않도록 방 상태·참가자 스냅샷을 싣는 것이 목표지만 아직 넣지 않았다. 그때까지 클라는 재연결 후 방 정보를 REST로 다시 읽는다
  - **[미정]** 스냅샷 payload 형태. `RoomInfo`를 재사용할지 별도로 둘지 정하지 않았다

### 5-D. 러닝 중

#### `RUNNING_LOCATION_UPDATE` (C→S) — 위치 정보 전송 (10초 배치)

```json
{
  "locations": [
    {
      "sequence": 15,                    // Long, 러닝 내 좌표 순번
      "latitude": 35.1795543,            // -90~90
      "longitude": 129.0756416,          // -180~180
      "altitudeMeters": 18.4,            // 단말 GPS 측정 고도(m), nullable
      "accuracyMeters": 6.2,             // GPS 수평 오차 반경 m
      "speedMetersPerSecond": 2.8,       // nullable
      "headingDegrees": 85.3,            // 0~360, nullable
      "cadenceSpm": 165,                 // nullable
      "currentPaceSecondsPerKm": 345,    // nullable
      "recordedAt": "2026-07-25T19:10:30"   // 측정 시각
    }
  ]
}
```

- **`runningRoomId`를 싣지 않는다.** 방은 `RUNNING_START`가 참가자 검증을 마치고 정한 뒤 서버가 WS 세션에 들고 있다. 10초마다 반복되는 메시지에 매번 실으면 클라가 참가하지 않은 방을 지정할 수 있게 된다
  - `RUNNING_START` 없이 이 메시지를 보내면 서버에 정해진 방이 없어 `RUNNING_NOT_STARTED`로 거부한다. 클라는 `RUNNING_START`부터 다시 보낸다
- **필수는 `sequence`·`latitude`·`longitude`·`accuracyMeters`·`recordedAt` 다섯뿐이다.** 나머지는 단말이 못 잴 수 있어 `null`로 보내도 되고, 서버는 그 좌표를 버리지 않고 값이 비었다는 사실만 남긴다 — 배치 하나가 통째로 거절되면 그 10초가 통으로 빈다. 케이던스는 보수 센서가, 속도·방위는 GPS 픽스가 있어야 온다
  - 비어 있으면 해당 지표를 표본에서 제외한다. 유효 표본이 없으면 지표 자체가 null이다(`running_records.avg_cadence`, erd.md)
- **클라는 1~2초 간격으로 수집해 로컬에 쌓으면서, 10초마다 모아서 보낸다.** 좌표 하나씩 10초마다 보내면 트랙이 성겨져 경로와 거리 정확도가 떨어진다
- 페이스·거리·케이던스·진행 시간은 러닝 중 표시용으로 클라이언트가 계산한다. 칼로리는 러닝 중 표시·전송하지 않고 종료 시 서버가 계산한다
- 서버는 Redis(`runningRoomId+userId` 키)에 버퍼링하고 기록을 생성할 때 S3에 업로드한다(`gpsTrackKey`) — `runningRoomId`는 세션이 들고 있는 값이다
- **ack 없음** — 고빈도 메시지라 건별 ack는 트래픽 낭비. 실패는 `ERROR`로 통지
  - 저장소 장애로 배치를 담지 못하면 `RUNNING_TRACK_UNAVAILABLE`을 보내되 **연결은 끊지 않고 러닝도 계속한다.** 원본이 로컬 트랙에 남아 있어 재연결로 복구되기 때문이다
  - 장애가 이어지면 배치마다 `ERROR`가 나간다. 클라는 건별 알림 대신 "저장 실패 중" 상태 표시 하나로 다룬다
- 재연결하면 클라이언트는 로컬 트랙 전체를 처음 `sequence`부터 다시 보내고, 서버는 `(runningRoomId, userId, sequence)`가 같은 좌표를 무시한다(`runningRoomId`는 재연결 뒤 `RUNNING_START`가 다시 정한다). ack가 없으므로 성공 경계를 추정하지 않으며 로컬 트랙은 `RUNNING_FINISHED` ack 뒤 삭제한다

#### `PLAYER_RUNNING_PROGRESS_UPDATED` (S→C) — 참가자 진행 정보

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440015",
  "distanceMeters": 1520,               // 현재까지 이동 거리(서버가 좌표로 누적)
  "targetDistanceMeters": 5000,         // 목표 거리(m)
  "currentPaceSecondsPerKm": 345,       // 현재 페이스(초/km), nullable
  "paused": false                       // 일시정지 중이면 true
}
```

- **갱신된 참가자 한 명만 싣는다.** 좌표 배치를 받아 진행이 바뀐 사람만 알리면 되고, 전원 스냅샷을 매번 보내면 인원수만큼 payload가 커진다
  - 클라는 참가자별 최신값을 로컬에 들고 이 메시지로 덮는다
  - **[미정]** 최초 진입·재연결 시 다른 참가자의 현재 진행을 받는 경로는 따로 정한다 — 이 메시지는 갱신분만 나르므로 그것만으로는 화면을 복구할 수 없다
  - `runningRoomId`를 싣지 않는다 — 클라는 `RUNNING_START`로 정한 방 하나에만 있다
  - `profileImageUrl`을 싣지 않는다 — 고빈도 메시지마다 presigned URL을 만들면 비싸다. 프로필은 `RUNNING_STARTED` 스냅샷에서 받는다
- **본인에게는 보내지 않는다.** 본인 진행은 클라가 이미 계산해 화면에 띄우고 있다
- `distanceMeters`는 **서버가 수신한 좌표로 누적한 값**이다. 클라 표시용 거리(5-D)와 미세하게 다를 수 있으나 다른 참가자 화면에 쓰는 값이라 서버 기준으로 통일한다
- `currentPaceSecondsPerKm`는 마지막 좌표의 값을 그대로 옮긴다 — 단말이 못 재면 `null`이다
- `paused`가 없으면 상대가 멈춘 것과 느려진 것을 구분할 수 없다 — 화면에서 갑자기 뒤처진 것처럼 보인다
  - **[미정]** `RUNNING_PAUSE`/`RUNNING_RESUME` 구현 전까지 항상 `false`로 나간다

#### `RUNNING_PAUSE` / `RUNNING_RESUME` (C→S) — 일시정지·재개

```json
{
  "runningRoomId": 125
}
```

- **일시정지 동안 경과 시간과 거리 계산이 멈춘다.** 클라는 좌표 전송도 중단한다 — 멈춰 있는 동안의 좌표는 트랙에 남길 이유가 없고, GPS 흔들림이 거리로 잡히면 기록이 부풀려진다
- **다른 참가자는 계속 진행한다.** 일시정지는 본인 기록에만 영향을 주며 다른 참가자를 멈추지 않는다
- 서버는 상태를 다른 참가자에게 `PLAYER_RUNNING_PROGRESS_UPDATED`의 `paused` 필드로 알린다
- **ack 없음** — 실패는 `ERROR`로 통지

#### `RUNNING_FINISH` (C→S) — 러닝 종료 (정상/강제 통합)

```json
{
  "runningRoomId": 125,
  "forced": false
}
```

- `forced`는 사용자가 조기 종료를 선택했는지 나타낼 뿐 최종 상태를 결정하지 않는다. 서버가 확정한 거리가 목표 이상이면 `COMPLETED`, 미달이면 `totalDistanceMeters / targetDistanceMeters`를 운영 설정 비율과 비교해 이상은 `RUNNING_LEFT_NO_PENALTY`, 미만은 `RUNNING_LEFT_PENALTY`로 전환한다
- 종료 시각을 `deleted_at`에 기록한다 — `COMPLETED`·`RUNNING_LEFT_*` 공통이다. 비우면 활성 신청으로 남아 다음 매칭을 신청할 수 없다
- 종료 신호나 타임아웃에 마지막 수신 데이터로 거리·페이스·구간·칼로리·고도 지표를 계산한다. 칼로리는 확정 거리·시간과 사용자 체중으로, 고도는 노이즈를 필터링한 기기 GPS 고도로 계산한다
- 거리·시간·경로를 산출할 수 있는 트랙이 있으면 `running_records`와 splits를 저장하고 GPS 트랙을 S3에 올려 `route_polyline`을 만든다. 그렇지 않으면 실제 거리를 0으로 판정하고 기록 없이 상태만 확정한다
- **목표 거리를 넘겨 뛰면 목표 지점에서 끊어 기록한다.** 목표를 사이에 둔 두 좌표에서 비율로 위치·시각을 보간해 그 지점을 기록의 끝으로 삼고, `totalDistanceMeters`·`endAt`·`totalDurationSeconds`를 모두 그 기준으로 확정한다 — 거리만 자르면 페이스가 실제보다 빨라진다. 목표 이후 좌표는 기록 계산에서만 빠지고 **S3 원본 트랙에는 그대로 남는다**. 목표 미달로 끝났으면 실제 거리를 그대로 쓴다
- **구간은 목표 거리를 10m로 나눈 고정 경계다**(0-10, 10-20…). 참가자별 실제 거리로 나누지 않으므로 같은 방 참가자의 `splitNumber` N은 언제나 같은 거리 구간을 가리킨다. 경계가 정확히 10m가 되도록 그 지점도 보간해 만든다
- **ack**: `RUNNING_FINISHED` — 수신 후 클라는 REST `GET /running-rooms/{id}/results`로 대시보드 진입
- `RUNNING_FINISH`는 멱등이다. 타임아웃이나 이전 요청으로 이미 확정됐으면 기록을 덮어쓰지 않고 `RUNNING_FINISHED`를 다시 보내 로컬 트랙을 정리하게 한다
- 방 시작 때 `RUNNING`으로 전환된 참가자 전원이 종료 상태가 되고 기록 확정이 끝나면 방을 `FINISHED`로 바꾼다. 타임아웃에는 남은 참가자를 먼저 같은 규칙으로 종료 처리한다

## 6. 러닝 중 / 러닝 후 대시보드 (REST)

> **러닝 사진**: 앱에서 촬영해 디바이스 갤러리에만 저장 — 서버 업로드/조회 API 없음. results 등 응답에 사진 필드 없음.

### 6-1. `GET /api/v1/running-rooms/{runningRoomId}/results` — 러닝 결과 (참가자 전원 요약)

- **화면**: 러닝 후 - 대시보드 (참가자 공통 정보). `RUNNING_FINISHED` 수신 후 진입
- 방이 아직 `STARTED`면 종료하지 않은 참가자는 `status='RUNNING'`, 기록 지표는 null인 현재 스냅샷을 반환한다. 다시 조회하면 최신 상태를 받고 방이 `FINISHED`면 최종 결과가 된다
- **Response `200 OK`**

```json
{
  "runningRoomId": 125,
  "startedAt": "2026-07-25T19:00:30",   // 현재 사용자 기록 기준, 기록 없으면 null
  "finishedAt": "2026-07-25T19:30:30",  // 현재 사용자 기록 기준, 기록 없으면 null
  "routes": [                             // 현재 사용자 경로 [위도, 경도] — 본인 기록이 없으면 null
    [35.1795543, 129.0756416],
    [35.1796012, 129.0757104]
  ],
  "players": [
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "profileImageUrl": "https://...",   // nullable
      "status": "COMPLETED",              // COMPLETED | RUNNING 두 값뿐이다
      "isDeleted": false,
      "isMe": true,
      "totalDistanceMeters": 5020,
      "totalDurationSeconds": 1800,
      "totalCaloriesKcal": 352,
      "averagePaceSecondsPerKm": 359,
      "averageCadenceSpm": 165,
      "totalElevationGainMeters": 42
    },
    {
      "userId": "550e8400-e29b-41d4-a716-446655440031",
      "nickname": "러닝초보",
      "profileImageUrl": null,
      "status": "COMPLETED",
      "isDeleted": false,
      "isMe": false,
      "totalDistanceMeters": 4870,
      "totalDurationSeconds": 1800,
      "totalCaloriesKcal": 315,
      "averagePaceSecondsPerKm": 370,
      "averageCadenceSpm": 158,
      "totalElevationGainMeters": 28
    }
  ]
}
```

- **`status`는 `COMPLETED`·`RUNNING` 두 값뿐이다** — 러닝을 끝낸 사람은 완주든 중도이탈이든 `COMPLETED`, 아직 뛰는 중이면 `RUNNING`이다. DB의 `running_players.status`(`RUNNING_LEFT_PENALTY` 등, `erd.md` §6)를 그대로 노출하지 않는다: **페널티 여부는 본인 매칭 쿨다운 판정에 쓰는 내부 값이라 남의 화면에 실을 이유가 없다.** 얼마나 뛰었는지는 `totalDistanceMeters`로 드러난다
- `players`에는 방에서 러닝 단계에 들어간 참가자 전원을 유지하고 시작 전 이탈자는 제외한다. 기록이 없으면 사용자 정보와 `status`만 채우고 `totalDistanceMeters`·`totalDurationSeconds`·`totalCaloriesKcal`·`averagePaceSecondsPerKm`·`averageCadenceSpm`·`totalElevationGainMeters`는 null로 내려 화면에 "기록 없음"으로 표시한다
- 기록이 있어도 케이던스·유효 고도 표본이 부족하면 `averageCadenceSpm`·`totalElevationGainMeters`는 null일 수 있다
- 탈퇴한 참가자는 공통 탈퇴 유저 형식으로 표시하고 `isDeleted=true`로 반환한다

- **`startedAt`·`finishedAt`·`routes`는 본인 기록 기준이다**(`running_records.start_at`/`end_at`/`route_polyline`). 본인 기록이 없으면 null이며 6-2의 최상위 필드도 같은 기준이다
- **`routes`는 서버가 폴리라인을 풀어서 내린다** — 저장은 `running_records.route_polyline`(encoded polyline)이지만 응답은 좌표 배열이다. 클라가 디코더를 붙일 필요도, 6-2를 기다릴 필요도 없이 진입 즉시 지도를 그린다. 좌표 정밀도는 폴리라인을 따라 소수점 5자리(약 1m)이며 그보다 정밀한 값은 존재하지 않는다. **전체 경로를 한 덩어리로 주는 곳은 여기뿐이다** — 6-2의 `routes`는 같은 경로를 구간별로 자른 조각이다
- **지도 마커용 시작·끝 좌표는 따로 싣지 않는다** — `routes`의 첫 원소와 끝 원소가 그대로 시작·끝 지점이다
- **목록·카드 응답은 `routePolyline`을 그대로 유지한다**(7-1·8-1) — 한 응답에 기록이 여러 건이라 좌표 배열로 바꾸면 응답 크기가 건수만큼 곱해진다. 좌표 배열은 기록 하나를 크게 그리는 화면(6-1·6-2·7-2)에만 쓴다

- **에러 (403 Forbidden — 같은 방 참가자만 열람)**

```json
{
  "code": "NOT_ROOM_PLAYER",
  "message": "같은 방 참가자만 조회할 수 있습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (같은 방 참가자)

### 6-2. `GET /api/v1/running-rooms/{runningRoomId}/split-results` — 구간별 상세 + 경로

- **화면**: 러닝 후 - 대시보드 (본인 경로 확인 + 참가자 상세·구간별 비교)
- **Response `200 OK`** (구조 요약)

```json
{
  "runningRoomId": 125,
  "splitDistanceMeters": 10,            // 고정 구간 거리
  "totalDistanceMeters": 5000,          // 현재 사용자 총 거리 — 목표를 넘겼으면 목표에서 끊은 값
  "totalElevationGainMeters": 42,       // 현재 사용자 누적 상승 고도
  "startedAt": "2026-07-25T19:00:30",
  "finishedAt": "2026-07-25T19:30:30",
  "players": [                           // 참가자 메타데이터는 여기 한 번만 — 구간마다 반복하지 않는다
    {
      "userId": "550e8400-e29b-41d4-a716-446655440015",
      "nickname": "동완러너",
      "profileImageUrl": "https://...",
      "status": "COMPLETED",             // 6-1과 같은 규칙 — COMPLETED | RUNNING
      "isDeleted": false,
      "isMe": true
    }
  ],
  "splits": [
    {
      "splitNumber": 1,                  // 1부터 시작
      "startDistanceMeters": 0,
      "endDistanceMeters": 10,
      "distanceMeters": 10,              // 고정 10m
      "routes": [                        // 이 구간의 현재 사용자 경로 [위도, 경도]
        [35.1795543, 129.0756416],
        [35.1795661, 129.0756588]
      ],
      "players": [
        {
          "userId": "550e8400-e29b-41d4-a716-446655440015",  // 최상위 players와 조인
          "durationSeconds": 3,
          "averagePaceSecondsPerKm": 345,
          "averageCadenceSpm": 162,
          "caloriesKcal": 1,
          "elevationChangeMeters": null     // 10m 구간에서는 대체로 null
        }
      ]
    }
  ]
}
```

- **참가자 메타데이터는 최상위 `players`에 한 번만 싣고, `splits[].players`에는 `userId`와 수치만 둔다.** 구간이 목표 5,000m 기준 500개라 `nickname`·`profileImageUrl`을 구간마다 반복하면 응답이 MB 단위가 된다(presigned URL만 500×인원×수백 바이트). 클라는 `userId`로 조인한다
- **구간 경계는 방 전체가 공유한다** — `running_rooms.target_distance`를 10m로 나눈 고정 경계라, `splitNumber` N은 모든 참가자에게 같은 거리 구간이다. 목표에 못 미치고 끝난 참가자는 도달하지 못한 구간의 `players`에서 빠진다
- `running_records` 행이 없는 참가자는 `splits[].players`와 최상위 `players` 양쪽에서 제외한다. 탈퇴한 참가자는 공통 탈퇴 유저 형식과 `isDeleted=true`로 표시한다
- 구간의 `averageCadenceSpm`·`elevationChangeMeters`도 유효 표본이 부족하면 null이다 — **10m 구간의 `elevationChangeMeters`는 대체로 null이다**(GPS 수직 오차가 구간 길이에 맞먹어 노이즈 임계값을 넘는 표본이 거의 없다)
- 최상위 `totalElevationGainMeters`도 유효 고도 표본이 부족하면 null이다
- 조회하는 본인의 기록이 없으면 `totalDistanceMeters`·`totalElevationGainMeters`·`startedAt`·`finishedAt`는 null이고 `splits`는 빈 배열이다 — 경로도 `splits` 안에만 있으므로 함께 사라진다
- **경로는 최상위가 아니라 구간마다 실린다.** 이 화면은 구간별로 색을 달리해 그리므로 자른 조각이 곧 그리는 단위다. 전체 경로 하나가 필요하면 6-1의 `routes`를 쓴다 — 같은 값을 두 응답에 중복해 싣지 않는다
- **`splits[].routes`는 조회하는 본인의 경로다.** 같은 객체의 `players`가 참가자 전원인 것과 다르다 — `running_splits.route_start_index`·`route_end_index`가 각자 자기 `route_polyline`의 위치를 가리키므로 남의 구간 좌표는 이 배열에 섞이지 않는다
- **이어붙일 때 경계점이 겹친다.** N번 구간의 끝 원소와 N+1번의 첫 원소는 같은 점이다 — 전체 경로를 만들려면 두 번째 구간부터 첫 원소를 건너뛴다
- 경로는 `running_records.route_polyline`에서 나온다 — 서버가 풀어 구간 범위로 잘라 내리므로 S3를 조회하지 않는다. 저장된 좌표 컬럼은 없다
- 점별 고도·정확도·순간 페이스·케이던스·시각은 반환하지 않고 구간 단위 값만 제공한다.
- **고도는 두 층위가 서로 다른 값이다** — 최상위 `totalElevationGainMeters`는 **누적 상승**(올라간 것만 합산, `running_records.total_elevation_gain`), 구간의 `elevationChangeMeters`는 **순고도차**(끝 − 시작, `running_splits.elevation_change`)다. **구간값을 더해도 최상위 값이 되지 않는다** — 계산 기준이 다르다(`erd.md` 러닝 기록 절)

- **에러 (403 Forbidden)**

```json
{
  "code": "NOT_ROOM_PLAYER",
  "message": "같은 방 참가자만 조회할 수 있습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (같은 방 참가자)

## 7. 기록 화면

### 7-1. `GET /api/v1/users/me/running-records` — 내 러닝 기록 목록

- **화면**: 기록(캘린더), 피드 작성(러닝기록 템플릿 선택)
- **Query는 두 모드 중 하나다.** 캘린더는 `from`·`to`(`YYYY-MM-DD`)를 함께 보내 양 끝을 포함한 최대 31일의 전체 기록을 받고 `nextCursor=null`로 반환한다. 최근 목록은 `from`·`to` 없이 `cursor`·`limit`(기본 20, 최대 50)로 페이지네이션한다
- `from`·`to`는 `running_records.start_at`의 KST 달력 날짜 기준이다. 두 모드의 파라미터를 섞거나 한쪽만 보내거나 `from > to`이거나 31일을 초과하면 `400 INVALID_REQUEST`다
- **Response `200 OK`**

```json
{
  "items": [
    {
      "runningRecordId": 501,
      "runningRoomId": 125,           // 항상 값이 있다
      "startedAt": "2026-07-25T19:00:30",
      "totalDistanceMeters": 5020,
      "totalDurationSeconds": 1800,
      "averagePaceSecondsPerKm": 359,
      "routePolyline": "u{~vFvyys@fS]pT_@..."   // 카드 경로 미리보기용
    }
  ],
  "nextCursor": null
}
```

- **`routePolyline`은 카드의 경로 미리보기용이다** — 기록 카드와 피드 작성 템플릿 카드에 달린 모양을 작게 띄운다(`feature-spec.md` 기록·피드 작성 절)
- **인증**: 필요 (본인 기록만)

### 7-2. `GET /api/v1/running-records/{runningRecordId}` — 기록 상세

- **화면**: 기록(일정 상세 — 경로·러닝 기록)
- **Response `200 OK`**

```json
{
  "runningRecordId": 501,
  "runningRoomId": 125,
  "startedAt": "2026-07-25T19:00:30",
  "finishedAt": "2026-07-25T19:30:30",
  "totalDistanceMeters": 5020,
  "totalDurationSeconds": 1800,
  "averagePaceSecondsPerKm": 359,
  "averageCadenceSpm": 165,
  "totalCaloriesKcal": 352,
  "totalElevationGainMeters": 42,
  "routes": [                            // 본인 경로 [위도, 경도]
    [35.1795543, 129.0756416],
    [35.1842012, 129.0831421]
  ],
  "splits": [
    {
      "splitNumber": 1,
      "distanceMeters": 1000,
      "durationSeconds": 345,
      "averagePaceSecondsPerKm": 345,
      "averageCadenceSpm": 162,
      "caloriesKcal": 68,
      "elevationChangeMeters": 12
    }
  ]
}
```

- `averageCadenceSpm`·`totalElevationGainMeters`와 각 구간의 `averageCadenceSpm`·`elevationChangeMeters`는 유효 표본이 부족하면 null이다
- 마지막 구간의 `distanceMeters`는 기본 구간 거리인 1000m보다 짧을 수 있다
- 최상위 `totalElevationGainMeters`는 누적 상승 고도이고 구간의 `elevationChangeMeters`는 순고도차이므로 구간값의 합과 일치하지 않을 수 있다
- **경로는 `routes` 좌표 배열로 내린다**(6-1·6-2와 같은 형식) — 상세 화면은 기록 하나를 크게 그리므로 서버가 `running_records.route_polyline`을 풀어 보낸다. 시작·종료 마커는 `routes`의 첫 원소·끝 원소다. 목록(7-1)은 카드에 선만 그리고 한 응답에 여러 건이 실려 `routePolyline` 문자열을 그대로 쓴다
- 같은 방 참가자 비교는 6-1·6-2(러닝 결과 API) 사용 — 이 API는 **본인 기록 전용**

- **에러 (403 Forbidden — 본인 기록 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (본인)

## 8. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

**피드 카드 공통 객체** (8-1/8-2/9-2 응답):

```json
{
  "feedId": 77,
  "author": {
    "userId": "550e8400-e29b-41d4-a716-446655440015",
    "nickname": "동완러너",
    "profileImageUrl": "...",
    "isDeleted": false
  },
  "content": "오늘도 5km 완주!",
  "images": [
    {
      "feedImageId": 11,
      "url": "https://...",
      "sortOrder": 0
    }
  ],
  "visibility": "PUBLIC",              // FRIENDS | PUBLIC | PRIVATE
  "likeCount": 12,
  "commentCount": 3,
  "likedByMe": false,
  "record": {                           // nullable — 러닝기록 템플릿 카드
    "runningRecordId": 501,
    "totalDistanceMeters": 5020,
    "totalDurationSeconds": 1800,
    "averagePaceSecondsPerKm": 359,
    "routePolyline": "u{~vFvyys@fS]pT_@..."   // 다운샘플 경로(encoded polyline) — 카드 지도 미리보기. running_records.route_polyline
  },
  "createdAt": "2026-07-25T11:00:00",
  "updatedAt": "2026-07-25T11:00:00"
}
```

### 8-1. `GET /api/v1/feeds` — 피드 목록 (무한 스크롤)

- **Query**: `tab=FRIENDS|ALL`(필수), `cursor`/`limit`
- **공개범위 필터**: `FRIENDS` = 친구의 `FRIENDS`/`PUBLIC` 피드 + 내 피드 전부, 최신순 / `ALL` = `PUBLIC` 피드 + 친구의 `FRIENDS` 피드, 최신순 + 가벼운 가중치(개인화 추천은 이후 확장)
- **Response `200 OK`**

```json
{
  "items": [피드 카드],
  "nextCursor": "..."
}
```

- **인증**: 필요

### 8-2. `GET /api/v1/feeds/{feedId}` — 피드 단건

- **화면**: 푸시 랜딩(좋아요/댓글 알림), 프로필 그리드 탭
- **Response `200 OK`**: 피드 카드

- **에러 (403 Forbidden — 비공개 — `PRIVATE` 타인, `FRIENDS` 비친구)**

```json
{
  "code": "FEED_NOT_VISIBLE",
  "message": "비공개 피드입니다."
}
```

- **에러 (404 Not Found — 삭제 포함)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 8-3. `POST /api/v1/feeds/{feedId}/like` — 좋아요 / 8-4. `DELETE` — 취소

- **Response `200 OK`** (재조회 방지)

```json
{
  "likeCount": 13,
  "likedByMe": true
}
```

- 좋아요 시 피드 소유자에게 푸시 (수신 동의 시)

- **에러 (403 Forbidden)**

```json
{
  "code": "FEED_NOT_VISIBLE",
  "message": "비공개 피드입니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 8-5. `GET /api/v1/feeds/{feedId}/comments` — 댓글 목록

- **정렬**: 등록순(오래된 것부터). 답글은 미포함 — `replyCount`만 제공(“답글 N개 보기” 지연 로딩)
- **Response `200 OK`**

```json
{
  "items": [
    {
      "commentId": 201,
      "author": {
        "userId": "550e8400-e29b-41d4-a716-446655440013",
        "nickname": "철수",
        "profileImageUrl": "...",
        "isDeleted": false
      },
      "content": "고생하셨어요!",
      "likeCount": 2,
      "likedByMe": false,
      "replyCount": 1,
      "isDeleted": false,               // true면 톰스톤(댓글 삭제) — content=null, "삭제된 댓글입니다" 자리표시. author.isDeleted(작성자 탈퇴)와는 다른 의미
      "createdAt": "2026-07-25T11:05:00"
    }
  ],
  "nextCursor": null
}
```

- **인증**: 필요

### 8-6. `POST /api/v1/feeds/{feedId}/comments` — 댓글/답글 작성

- **Request** — `parentCommentId`는 답글일 때만(선택)

```json
{
  "content": "...",
  "parentCommentId": 201
}
```

- **depth 1 제한**: `parentCommentId`가 이미 답글인 댓글이면 `400 REPLY_DEPTH_EXCEEDED`
- **Response `201 Created`**: 작성된 댓글 객체 (8-5 형식)
- 피드 소유자(답글이면 원댓글 작성자)에게 푸시 (수신 동의 시)

- **에러 (403 Forbidden)**

```json
{
  "code": "FEED_NOT_VISIBLE",
  "message": "비공개 피드입니다."
}
```

- **에러 (404 Not Found — feedId 피드 없음, 또는 parentCommentId 부모 댓글 없음)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "REPLY_DEPTH_EXCEEDED",
  "message": "답글에는 답글을 달 수 없습니다."
}
```

- **인증**: 필요

### 8-7. `PATCH /api/v1/comments/{commentId}` — 댓글 수정

- **Request**: `{ "content": "..." }` (필수, 빈 값 불가)
- **권한**: 댓글 **작성자 본인만** (피드 소유자는 삭제만 가능 — 남의 발언 내용 변경 불가)
- 톰스톤(삭제된 댓글)은 수정 불가. 수정 시 이전 내용을 `delete_comments`에 스냅샷 저장(피드와 동일 — 신고 시 원본 확인용), `updated_at` 갱신
- **Response `200 OK`**: 수정된 댓글 객체 (8-5 형식)

- **에러 (403 Forbidden — 작성자 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (409 Conflict — 톰스톤)**

```json
{
  "code": "COMMENT_DELETED",
  "message": "이미 삭제된 댓글입니다."
}
```

- **인증**: 필요 (작성자)

### 8-8. `GET /api/v1/comments/{commentId}/replies` — 답글 목록 (지연 로딩)

- **Response `200 OK`**: 8-5와 동일 형식(등록순, `replyCount` 없음)
- **인증**: 필요

### 8-9. `DELETE /api/v1/comments/{commentId}` — 댓글 삭제

- **권한**: 댓글 작성자 본인 **또는** 그 댓글이 달린 피드의 소유자
- **동작(레딧 방식)**: 답글 없으면 하드delete, 답글 있으면 톰스톤(내용 비움 + `deleted_at`, 스레드 유지). 두 경우 모두 `delete_comments` 스냅샷 선저장
- **Response**: `204 No Content`

- **에러 (403 Forbidden)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 8-10. `POST /api/v1/comments/{commentId}/like` — 댓글 좋아요 / 8-11. `DELETE` — 취소

- **Response `200 OK`**

```json
{
  "likeCount": 3,
  "likedByMe": true
}
```

- **인증**: 필요

## 9. 피드 작성 페이지 (+피드 편집) [MVP 제외]

### 9-1. `POST /api/v1/feeds/images/presigned-url` — 피드 이미지 업로드 URL 발급 (여러 장)

- **Request**

```json
{
  "files": [
    {
      "originalFileName": "a.jpg",
      "mimeType": "image/jpeg"
    }
  ]
}
```

- **Response `200 OK`** — 순서대로 매핑

```json
{
  "uploads": [
    {
      "feedImageKey": "feeds/2026/07/....jpg",
      "uploadUrl": "https://..."
    }
  ]
}
```

- **인증**: 필요

### 9-2. `POST /api/v1/feeds` — 피드 작성

- **Request**

```json
{
  "content": "오늘도 5km 완주!",          // 선택
  "imageKeys": ["feeds/2026/07/....jpg"],  // 선택 — 업로드 완료된 key, 배열 순서 = sortOrder
  "visibility": "PUBLIC",                  // 필수. FRIENDS|PUBLIC|PRIVATE — 기본 선택값 PUBLIC(클라 프리셋)
  "runningRecordId": 501                   // 선택 — 러닝기록 템플릿 (대시보드 진입 시 방금 기록 기본 선택). DB 매핑은 feeds.running_record_id
}
```

- **검증**: `content`/`imageKeys` 둘 다 비면 `400 EMPTY_FEED` (최소 하나 필수)
- **Response `201 Created`**: 피드 카드 — 목록 최상단 노출

- **에러 (400 Bad Request)**

```json
{
  "code": "EMPTY_FEED",
  "message": "피드 내용이나 이미지를 최소 하나 입력해 주세요."
}
```

- **에러 (404 Not Found — runningRecordId 없음/본인 것 아님)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 9-3. `PATCH /api/v1/feeds/{feedId}` — 피드 수정

- **화면**: 프로필(피드 편집 — 게시글 수정, 노출 범위 설정)
- **Request**: `{ "content"?, "imageKeys"?, "visibility"? }` (부분 수정). 수정 시마다 **이전 내용을 `delete_feeds`에 스냅샷 저장** (신고/차단 등 활용 기능은 **[MVP 제외]**이나 이력은 처음부터 축적)
- **Response `200 OK`**: 수정된 피드 카드

- **에러 (403 Forbidden — 본인 피드 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "EMPTY_FEED",
  "message": "피드 내용이나 이미지를 최소 하나 입력해 주세요."
}
```

- **인증**: 필요 (소유자)

### 9-4. `DELETE /api/v1/feeds/{feedId}` — 피드 삭제

- **동작**: `deleted_at` 소프트delete — 전체 조회에서 제외
- **Response**: `204 No Content`

- **에러 (403 Forbidden — 본인 피드 아님)**

```json
{
  "code": "FORBIDDEN",
  "message": "권한이 없습니다."
}
```

- **에러 (404 Not Found)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요 (소유자)

## 10. 프로필 페이지

### 10-1. `GET /api/v1/users/me` — 내 기본 정보

- **화면**: 전역 (앱 진입 시 `isOnboarded`로 홈/온보딩 분기)
- **Response `200 OK`**: `{ "userId", "nickname", "isOnboarded" }`
- **표시 전용 값은 싣지 않는다** — 앱을 열 때마다 타는 경로라 특정 화면에서만 쓰는 값을 담지 않는다. 이메일은 12-1, 소개글은 10-2, 프로필 사진은 11-3이 각각 담당한다
- **`profileImageUrl`을 내리지 않는 이유** — 11-3이 전용 조회를 제공하고, presigned URL은 TTL이 있어 표시 시점에 다시 받아야 한다. 앱 진입마다 발급하면 쓰이지 않을 URL에 S3 호출만 늘어난다
- **`userId`는 남긴다** — `{userId}` 경로 API(10-2·10-4 등)에서 본인 여부를 가리는 데 쓴다. 액세스 토큰에도 들어 있지만 클라이언트가 토큰을 파싱하게 만들지 않는다
- **인증**: 필요

### 10-2. `GET /api/v1/users/{userId}` — 프로필 요약

- **화면**: 프로필 (본인/타인 공통 — 본인이면 편집·설정 버튼, 타인이면 친구 요청 버튼 노출은 `isMe`로 분기)
- **Response `200 OK`**

```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440015",
  "isMe": false,
  "nickname": "동완러너",
  "profileImageUrl": "https://...",
  "introduction": "즐겁게 달려요",
  "friendCount": 42,                   // friendships에서 COUNT (status=ACCEPTED)
  "friendStatus": "ACCEPTED"           // NONE | PENDING_SENT | PENDING_RECEIVED | ACCEPTED
}
```

- **러닝 통계(마일리지·최고 페이스·러닝 횟수)는 싣지 않는다** — FE와 합의해 프로필 화면에서 제외했다. 다시 필요해지면 유효 러닝 판정(`feature-spec.md`)을 거친 집계로 추가한다
- `friendStatus`로 버튼을 가른다(10-6 표). 본인 프로필(`isMe=true`)이면 `null`이다

- **에러 (404 Not Found — 탈퇴 포함)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 필요

### 10-3. `GET /api/v1/users/{userId}/feeds` — 피드 그리드 (경량) [MVP 제외]

- **Response `200 OK`** — 탭하면 8-2 단건 조회로 상세

```json
{
  "items": [
    {
      "feedId": 77,
      "thumbnailUrl": "https://...",
      "imageCount": 3
    }
  ],
  "nextCursor": "..."
}
```

- **공개범위**: 본인 = 전부(`PRIVATE` 포함) / 타인 = `PUBLIC` (+친구면 `FRIENDS`)
- **인증**: 필요

### 10-4. `POST /api/v1/users/{userId}/friend-request` — 친구 요청

- **Response `201 Created`**

```json
{
  "friendStatus": "PENDING_SENT"
}
```

- `friendships`에 `(요청자, 대상, PENDING)` 행을 만들고 대상에게 "친구 요청 도착" 푸시를 보낸다
- **역방향에 `PENDING`이 있으면 새 요청을 만들지 않고 수락으로 처리한다.** 서로 요청을 주고받은 상황은 이미 합의된 것이라 한 번 더 수락을 요구할 이유가 없다. 이때 응답은 `ACCEPTED`다
- **에러 (400 Bad Request)**: `CANNOT_FRIEND_SELF` — 자기 자신에게는 요청할 수 없다
- **에러 (409 Conflict)**: `FRIEND_REQUEST_ALREADY_EXISTS` — 이미 요청했거나 이미 친구다
- **에러 (404 Not Found)**: 대상이 없다

```json
{
  "code": "CANNOT_FRIEND_SELF",
  "message": "자기 자신에게는 친구 요청을 보낼 수 없습니다."
}

{
  "code": "FRIEND_REQUEST_ALREADY_EXISTS",
  "message": "이미 요청했거나 이미 친구입니다."
}
```
- **인증**: 필요

### 10-5. `DELETE /api/v1/users/{userId}/friend-request` — 요청 취소 · 거절

- **호출자가 보낸 쪽이면 취소, 받은 쪽이면 거절이다.** 이름만 다를 뿐 하는 일은 같아서(`PENDING` 행 DELETE) 하나로 둔다
- **이력을 남기지 않는다**
- **Response `204 No Content`**
- **에러 (404 Not Found)**: `PENDING` 요청이 없다
- **인증**: 필요

### 10-6. `POST /api/v1/users/{userId}/friend` — 요청 수락 / `DELETE` — 친구 삭제

- **POST(수락)**: 경로의 `{userId}`는 **요청을 보낸 사람**이다. `status`를 `ACCEPTED`로 바꾸고 요청자에게 "친구 요청 수락됨" 푸시를 보낸다
  - **Response `201 Created`**: `{ "friendStatus": "ACCEPTED" }`
  - **에러 (404 Not Found)**: 받은 요청이 없다
- **DELETE(친구 삭제)**: **양쪽 누구나 호출할 수 있다** — 성립한 뒤로는 방향에 의미가 없다. 이력을 남기지 않는다
  - **Response `204 No Content`**
  - **에러 (404 Not Found)**: 친구가 아니다
- **인증**: 필요

> | `friendStatus` | 화면 버튼 | 호출 |
> |---|---|---|
> | `NONE` | 친구 요청 | `POST .../friend-request` |
> | `PENDING_SENT` | 요청 취소 | `DELETE .../friend-request` |
> | `PENDING_RECEIVED` | 수락 / 거절 | `POST .../friend` / `DELETE .../friend-request` |
> | `ACCEPTED` | 친구 삭제 | `DELETE .../friend` |

### 10-7. `GET /api/v1/users/me/friends` — 친구 목록 / `GET /api/v1/users/me/friend-requests` — 받은 요청 목록

- **화면**: 친구 목록 페이지 (친구 탭 + 받은 요청 탭)
- **둘 다 본인 것만 조회한다.** 타인의 친구 목록은 열지 않는다(근거는 `feature-spec.md` 친구 목록 페이지 절). 타인 프로필에는 **친구 수만** 표시된다
- **Query**: `q`(이름 필터, 친구 목록만), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl" } ], "nextCursor": "..." }`
- 친구 목록은 `status='ACCEPTED'`이면서 `requester_id`·`receiver_id` 중 하나가 본인인 행이다
- 받은 요청 목록은 본인이 `receiver_id`이고 `status='PENDING'`인 행이다. 보낸 요청 목록은 화면이 없어 API도 두지 않는다
- **인증**: 필요

### 10-8. `GET /api/v1/users/{userId}/colors` — 컬러 컬렉션 [MVP 제외]

- 자동 획득과 `user_colors` 신규 적재도 MVP에서 제외한다.
- **화면**: 프로필 — 획득한 색을 `보유 수 / 전체 수`와 함께 보여준다
- **Response `200 OK`** — 마스터 전체를 내리고 각 색에 획득 여부를 표시한다
- 아래 조건·개수 값은 응답 형식 예시이며 실제 조건표는 확정하지 않았다

```json
{
  "unlockedCount": 17,
  "totalCount": 42,
  "colors": [
    {
      "colorId": 2,
      "category": "ENDURANCE",
      "shadeNumber": 2,
      "name": "딥 블루",
      "hexCode": "#3c62e2",
      "unlockDescription": "10km 이상 완주",
      "unlocked": true,
      "unlockedAt": "2026-08-01T09:12:00"
    },
    {
      "colorId": 3,
      "category": "ENDURANCE",
      "shadeNumber": 3,
      "name": "심해 블루",
      "hexCode": "#1a3a8f",
      "unlockDescription": "누적 100km",
      "unlocked": false,
      "unlockedAt": null
    }
  ]
}
```

- **못 얻은 색도 함께 내린다.** 컬렉션 화면은 "무엇을 더 모을 수 있는지"를 보여주는 것이 목적이라, 미획득 색과 그 조건(`unlockDescription`)이 있어야 화면이 성립한다
- `totalCount`는 마스터 행 수다 — **총 개수를 명세에 박지 않으므로** 클라도 이 값을 그대로 쓴다
- **지인 마스킹**: `profile_visibility=FRIENDS`인 사용자를 친구가 아닌 사람이 조회하면 `403 PROFILE_NOT_VISIBLE`

```json
{
  "code": "PROFILE_NOT_VISIBLE",
  "message": "비공개 프로필입니다."
}
```

- **에러 (404 Not Found)**: 대상이 없다
- **인증**: 필요

### 10-9. `GET /api/v1/users/search` — 사용자 검색

- **화면**: 사용자 검색 — **친구를 추가하려면 먼저 사람을 찾아야 하므로 친구 기능의 진입점이다**
- **Query**: `q`(필수, 닉네임), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl", "friendStatus" } ], "nextCursor": "..." }`
- `friendStatus`는 10-2와 같은 값이며, 버튼을 무엇으로 그릴지가 이 값에 달렸다
- **인증**: 필요

## 11. 프로필 편집 페이지

### 11-1. `POST /api/v1/users/me/profile-image/presigned-url` — 프로필 사진 업로드 URL

- **Request**

```json
{
  "mimeType": "image/jpeg",
  "fileSizeBytes": 204800
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `mimeType` | String | 필수. `image/jpeg`·`image/png`·`image/webp`만 허용(대소문자 무시) |
| `fileSizeBytes` | Long | 필수. 1 이상 10,485,760(10MB) 이하 — 서명에 포함되므로 실제 업로드 크기와 같아야 한다 |

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/550e8400-.../9f1c2b7e-....jpg",
  "uploadUrl": "https://..."
}
```

`profileImageKey` 포맷은 `profiles/{userId}/{imageId}.{확장자}` — 소유자 검증에 쓰이므로 클라가 임의로 만들지 않는다. 확장자는 `mimeType`이 정한다(`image/jpeg`→`jpg`, `image/png`→`png`, `image/webp`→`webp`). 클라는 `uploadUrl`로 S3에 직접 업로드하며, 업로드 헤더의 `Content-Type`은 요청한 `mimeType`과 일치해야 한다(서명에 포함).

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "이미지 형식은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이미지는 JPEG, PNG, WEBP 형식만 업로드할 수 있습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "파일 크기는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "파일 크기는 1바이트 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "이미지는 10MB 이하만 업로드할 수 있습니다."
}
```

- **인증**: 필요

### 11-2. `PATCH /api/v1/users/me/profile-image` — 프로필 사진 반영

11-1로 받은 `uploadUrl`에 업로드를 마친 뒤 호출한다. 서버가 S3에 실제로 올라왔는지 확인하고 `users.profile_image_key`를 갱신한다.

- **Request**

```json
{
  "profileImageKey": "profiles/550e8400-.../9f1c2b7e-....jpg"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `profileImageKey` | String | 필수, 255자 이하. 11-1이 발급한 키 그대로 |

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/550e8400-.../9f1c2b7e-....jpg"
}
```

- **에러 (400 Bad Request — 요청 검증)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "프로필 이미지 키는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "프로필 이미지 키는 255자 이하여야 합니다."
}
```

- **에러 (400 Bad Request — 본인 키가 아니거나 형식이 어긋남)**

```json
{
  "code": "INVALID_PROFILE_IMAGE",
  "message": "프로필 이미지가 올바르지 않습니다."
}
```

- **에러 (400 Bad Request — 키에 해당하는 객체가 S3에 없음)**

```json
{
  "code": "PROFILE_IMAGE_NOT_UPLOADED",
  "message": "업로드되지 않은 이미지입니다."
}
```

- **인증**: 필요

### 11-3. `GET /api/v1/users/{userId}/profile-image` — 프로필 사진 URL 조회

- **Response `200 OK`**

```json
{
  "profileImageUrl": "https://..."
}
```

사진이 등록돼 있지 않으면 `profileImageUrl`은 `null`이다.

- **에러 (404 Not Found — 대상 사용자 없음)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 불필요

### 11-4. `DELETE /api/v1/users/me/profile-image` — 프로필 사진 삭제

`users.profile_image_key`를 비운다. **S3 객체는 지우지 않고 DB의 키 연결만 끊는다.** 사진이 없는 상태에서 호출해도 에러 없이 성공한다(idempotent).

- **Request**: 본문 없음
- **Response `204 No Content`**

- **인증**: 필요

### 11-5. `GET /api/v1/users/me/profile` — 프로필 편집용 조회

- **화면**: 프로필 편집 (진입 시 입력 칸을 현재 값으로 채운다)
- **11-6 `PATCH`와 같은 필드 집합이다** — 고치기 전에 읽는 자리라 둘이 짝이다
- **Response `200 OK`**

```json
{
  "introduction": "즐겁게 달려요",
  "gender": "MALE",
  "birthday": "1998-12-16",
  "weightKg": 70.5,
  "heightCm": 175.0
}
```

| 필드 | 타입 | 설명 | 출처 |
|---|---|---|---|
| `introduction` | String | 소개글. 없으면 `null` | `users` |
| `gender` | String | `MALE` \| `FEMALE`. 온보딩 전이면 `null` | `user_onboardings` |
| `birthday` | String | `YYYY-MM-DD`. 온보딩 전이면 `null` | `user_onboardings` |
| `weightKg` | Number | 저장된 값(소수점 첫째 자리). 온보딩 전이면 `null` | `user_onboardings` |
| `heightCm` | Number | 저장된 값(소수점 첫째 자리). 온보딩 전이면 `null` | `user_onboardings` |

- **닉네임·사진은 여기 없다** — 편집 화면에 함께 있지만 저장 흐름이 달라 엔드포인트가 갈린다(11-7·11-3)
- **온보딩 전에도 `200`이다** — `user_onboardings`에 행이 없으면 그 출처의 네 필드가 `null`로 나가고 소개글만 채워진다. 11-6이 소개글만 보내는 요청을 온보딩 전에도 받아주므로 여기서도 막지 않는다
- **키·몸무게는 이 API로만 나간다** — 프로필 화면(10-2)에는 싣지 않는다
- **인증**: 필요

### 11-6. `PATCH /api/v1/users/me/profile` — 프로필 수정

저장 버튼 하나로 끝나는 값들을 한 요청에 담는다. 사진(11-1~11-4)은 저장 버튼과 무관한 3단계 업로드라, 닉네임(11-7)은 중복 검사와 409가 붙어 각각 전용 엔드포인트를 쓴다. 평균 페이스는 수정 불가(서버가 러닝 기록으로 갱신).

- **Request** (부분 수정 — 보낸 필드만 바꾼다)

```json
{
  "introduction": "즐겁게 달려요",
  "gender": "MALE",
  "birthday": "1998-12-16",
  "weightKg": 70.5,
  "heightCm": 175.0
}
```

| 필드 | 타입 | 제약 | 저장 위치 |
|---|---|---|---|
| `introduction` | String | 선택. 100자 이하. 빈 문자열이면 소개글을 지운다 | `users` |
| `gender` | String | 선택. `MALE` \| `FEMALE` | `user_onboardings` |
| `birthday` | String | 선택. `YYYY-MM-DD`, 1900-01-01 이후이며 미래일 수 없다 | `user_onboardings` |
| `weightKg` | Number | 선택. 20 이상 300 이하. 소수점 둘째 자리 이하는 반올림해 저장한다 | `user_onboardings` |
| `heightCm` | Number | 선택. 20 이상 300 이하. 소수점 둘째 자리 이하는 반올림해 저장한다 | `user_onboardings` |

- **필드를 생략하면 현재 값을 그대로 둔다.** 소개글만 빈 문자열(`""`)로 지울 수 있고, 나머지 넷은 온보딩에서 필수라 지우는 개념이 없다
- **키·몸무게는 소수점 첫째 자리로 정규화한다.** `70.55`를 보내면 거부하지 않고 `70.6`으로 저장하며, 응답에도 정규화된 값이 나가 클라이언트가 저장된 값을 알 수 있다. 정상 범위의 값을 자릿수만으로 400으로 막지 않기 위해서다
- **화면 구성이 확정되기 전이라 편집 가능한 값을 한 엔드포인트에 모았다** — 저장 버튼이 하나로 묶이든 여러 화면으로 갈리든 클라이언트가 자기 필드만 보내면 된다. 화면이 정해지면 이 구성을 다시 본다
- **`profileVisibility`는 여기 없다** — 프로필 편집이 아니라 설정 페이지 값이라 12-4가 담당한다

- **Response `200 OK`** — 갱신본. 보낸 필드만 담아 돌려준다

```json
{
  "introduction": "즐겁게 달려요",
  "weightKg": 70.5
}
```

- **바꾼 값만 돌려준다** — 사진 반영(11-2)·닉네임(11-7)과 같은 형태다
- **11-5 `GET`의 `null`과 뜻이 다르다** — 여기서 빠진 필드는 "안 보냈다", 11-5의 `null`은 "아직 값이 없다"다
- **소개글을 `""`로 지우면 응답도 `""`다** — 조회는 `null`이지만 여기서는 `null`이 "안 보냈다"라 겹쳐 쓸 수 없다

- **에러 (400 Bad Request)** — 문구는 온보딩(1-9)과 같다

```json
{
  "code": "INVALID_REQUEST",
  "message": "소개글은 100자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "성별은 MALE 또는 FEMALE이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "생년월일은 미래일 수 없습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "몸무게는 20kg 이상이어야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "키는 300cm 이하여야 합니다."
}
```

- **에러 (409 Conflict — 온보딩 미완료)**

```json
{
  "code": "ONBOARDING_NOT_COMPLETED",
  "message": "온보딩을 먼저 완료해 주세요."
}
```

- **`user_onboardings`에 저장하는 필드(`gender`·`birthday`·`weightKg`·`heightCm`)가 하나라도 있는데 온보딩을 마치지 않았으면 아무것도 바꾸지 않고 409로 답한다.** 소개글만 보냈다면 온보딩 전에도 성공한다
- **정상 흐름에서는 발생하지 않는다** — 앱 진입이 `isOnboarded`로 갈리므로 온보딩 전에는 편집 화면에 닿지 못한다. 구버전 앱과 직접 호출 때문에 서버가 막는다
- **인증**: 필요

### 11-7. `PATCH /api/v1/users/me/nickname` — 닉네임 변경

닉네임은 `user_onboardings.nickname`에 있어 온보딩을 마쳐야 바꿀 수 있다. 서비스 전반의 표시명이 이 값이다.

- **Request**

```json
{
  "nickname": "완두콩"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `nickname` | String | 필수, 2~16자, 한글·영문·숫자·`_`만 |

- **Response `200 OK`**

```json
{
  "nickname": "완두콩"
}
```

- **바꾼 값만 돌려준다** — 본인 대상 수정이라 `userId`는 클라이언트가 이미 알고 있다(10-1·로그인 응답). 온보딩(1-9)은 `201 Created`로 리소스를 만들며 식별자를 돌려주지만, 수정은 그 자리가 아니다
- **`userId` 제거는 `api-convention.md`의 "기존 필드를 제거하지 않는다"에 대한 예외다** — 클라이언트가 아직 이 필드를 쓰지 않는 것을 확인하고 걷어냈다

현재 닉네임과 같은 값을 보내면 아무것도 바꾸지 않고 그대로 반환한다(idempotent).

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 2자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
}
```

- **에러 (409 Conflict — 남이 쓰고 있음)**

```json
{
  "code": "NICKNAME_ALREADY_EXISTS",
  "message": "이미 사용 중인 닉네임입니다."
}
```

- **에러 (409 Conflict — 온보딩 미완료)**

```json
{
  "code": "ONBOARDING_NOT_COMPLETED",
  "message": "온보딩을 먼저 완료해 주세요."
}
```

- **인증**: 필요

### 11-8. `POST /api/v1/users/nickname/availability` — 닉네임 중복 확인

저장하기 전에 쓸 수 있는 닉네임인지 미리 확인한다. 확인과 저장 사이에 남이 선점할 수 있으므로 최종 방어는 11-7·1-9의 409다. 사용 화면은 프로필 편집·온보딩이다.

- **Request**

```json
{
  "nickname": "완두콩"
}
```

| 필드 | 타입 | 제약 |
|---|---|---|
| `nickname` | String | 필수, 2~16자, 한글·영문·숫자·`_`만 |

- **Response `200 OK`**

```json
{
  "nickname": "완두콩",
  "available": true
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 2자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "닉네임은 한글, 영문, 숫자, _만 사용할 수 있습니다."
}
```

- **인증**: 불필요
- **인증을 요구하지 않는 이유는 확인이 필요하다.** 회원가입 폼에서 닉네임을 받던 설계(토큰 발급 전이라 공개가 필요했다)의 잔재로 보인다. 지금은 닉네임을 온보딩에서 받고 그 시점엔 토큰이 있어, 사용 화면인 온보딩·프로필 편집 어느 쪽도 공개를 필요로 하지 않는다
- **공개로 두면 닉네임 존재 여부를 토큰 없이 무한히 조회할 수 있다.** 인증이 없어 `userId` 기준 호출 제한을 걸 축도 없다(이 서비스에 IP 기준 제한 장치는 없다). 정리하려면 `SecurityConfig`의 `PUBLIC_ENDPOINTS`에서 이 경로를 빼면 되고, 화면 흐름상 잃는 것은 없다

## 12. 설정 페이지

### 12-1. `GET /api/v1/users/me/account` — 계정 정보

- **화면**: 설정 (계정 항목)
- **Response `200 OK`**

```json
{
  "email": "run@example.com",
  "loginType": "GOOGLE"                  // LOCAL | GOOGLE | KAKAO
}
```

- **`loginType` 판정**: `oauth_users`에 row가 있으면 그 `provider`, 없으면 `LOCAL`. `users.password_hash`의 null 여부로 판정하지 않는다 — 결과는 같지만 "무슨 계정인가"에 직접 답하는 데이터는 `oauth_users`다
- **계정은 로컬·소셜 중 하나로 배타적이다** — 소셜 최초 가입 시 이메일이 기존 로컬 계정과 겹치면 `409`로 거부하므로(1-5/1-7) 단일 값으로 표현된다
- **클라 표시 규칙**: `LOCAL`이면 로그인 수단 문구 없이 "비밀번호 변경" 메뉴를 노출하고, 소셜이면 "구글/카카오 계정으로 로그인 중"을 표시하고 메뉴를 감춘다(근거는 `feature-spec.md` 설정 페이지 절)
- **인증**: 필요

### 12-2. `PATCH /api/v1/users/me/password` — 비밀번호 변경

로컬 계정만 가능. 현재 비밀번호로 본인을 재확인한다.

- **화면**: 설정 (계정 항목 → 비밀번호 변경)
- **Request**

```json
{
  "currentPassword": "********",                                          // 필수
  "newPassword": "********"                                               // 필수 — 6~16자, 영문·숫자·특수문자 각 1자 이상 (확인 일치 검증은 클라이언트)
}
```

- **Response**: `204 No Content`
- **기존 토큰은 무효화하지 않는다** — 다른 기기 세션이 유지된다. 변경 즉시 전 기기 로그아웃은 **[MVP 제외]**
- **새 비밀번호가 현재와 같아도 거부하지 않는다** — 별도 검증을 두지 않는다

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "현재 비밀번호는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 6자 이상 16자 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "비밀번호는 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다."
}
```

- **에러 (401 Unauthorized — 현재 비밀번호 불일치)**

```json
{
  "code": "INVALID_CURRENT_PASSWORD",
  "message": "현재 비밀번호가 올바르지 않습니다."
}
```

- **클라이언트는 이 `401`을 토큰 만료로 오인하면 안 된다.** `code`가 `INVALID_CURRENT_PASSWORD`이면 refresh 후 재시도하지 말고 입력 오류로 처리한다

- **에러 (409 Conflict — 소셜 계정)**

```json
{
  "code": "PASSWORD_NOT_SET",
  "message": "소셜 로그인으로 가입한 계정은 비밀번호를 변경할 수 없습니다."
}
```

- 클라는 12-1의 `loginType`으로 메뉴를 감추지만 서버도 막는다 — 구버전 앱과 직접 호출이 있다

- **인증**: 필요

> 비밀번호 찾기(로그인 전 재설정)는 명세에 없다. 이 API는 로그인 상태 전용이다.

### 12-3. `GET /api/v1/users/me/settings` — 설정 조회

- **화면**: 설정
- **Response `200 OK`**

```json
{
  "alertConsent": true,                  // 전체 알림 on/off (단일 토글, 기본 on)
  "profileVisibility": "PUBLIC"          // FRIENDS | PUBLIC — 지인 마스킹 on/off
}
```

- **`alertConsent` = 단일 토글** — 매칭 확정/실패, 러닝 시작 리마인더, 친구 요청 도착/수락을 한 번에 on/off (`users.alert_consent`). **기본값 `true`**, OS 알림 권한과는 별개로 동작한다(둘 중 하나라도 꺼져 있으면 미도달)
- **공개범위 설정**: `profileVisibility` 하나뿐이다. **피드 작성 기본값은 서버에 두지 않는다** — 매 피드마다 `feeds.visibility`를 개별 선택하고, 기본 선택값은 클라이언트가 PUBLIC으로 고정한다
- **인증**: 필요

### 12-4. `PATCH /api/v1/users/me/settings` — 설정 변경

- **Request**: 12-3 필드 부분 수정 / **Response `200 OK`**: 갱신본
- **인증**: 필요

### 12-5. `DELETE /api/v1/users/me` — 회원탈퇴

- **화면**: 설정 (확인 팝업 후)
- **동작 (테이블별 정책)**:
  - 탈퇴는 활성 상태 때문에 막지 않는다. `MATCHING`이면 5-A의 대기 취소, `MATCHED`이면 방 나가기, `STARTED`이면 마지막 수신 데이터로 5-D의 종료 처리를 먼저 적용한다. 방 인원·상태를 갱신하고 남은 참가자에게 이벤트를 보낸다.
  - `delete_users` 스냅샷 후 `users`를 하드 삭제한다. `delete_users.created_at`은 스냅샷 시각이다.
  - **유지**: `feeds`/`comments`/`running_records`(+splits)/좋아요. 이미 시작한 방의 `running_players`와 `running_room_sessions`도 기록 없는 참가자를 결과에 남기기 위해 유지한다. 사용자는 공통 탈퇴 유저 형식으로 표시한다.
  - **CASCADE 삭제**: `user_onboardings`/`user_devices`/`oauth_users`/`friendships`/`user_colors`.
  - **명시적 삭제**: 시작 전 신청의 `running_players`; 연결된 `running_room_sessions`는 CASCADE 삭제한다.
- **Response**: `204 No Content` (토큰 즉시 무효화)
- **인증**: 필요
