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
| 5 | POST | `/api/v1/auth/oauth/google` | 구글 로그인 — ID 토큰 서버 검증 → 토큰 발급 |
| 6 | POST | `/api/v1/auth/oauth/kakao` | 카카오 로그인 — 인가 코드+PKCE 서버 교환 → 토큰 발급 |
| 7 | POST | `/api/v1/auth/oauth/apple` | 애플 로그인 — ID 토큰+nonce 서버 검증 → 토큰 발급 |
| 8 | POST | `/api/v1/auth/refresh` | 토큰 재발급 (rotation — accessToken·refreshToken 모두 교체) |
| 9 | POST | `/api/v1/auth/logout` | 로그아웃 — access 토큰 서버 차단(블랙리스트) + 리프레시 토큰 삭제 — 사용 화면: 설정 페이지 |
| 10 | POST | `/api/v1/users/onboarding` | 온보딩 입력 (닉네임 포함, 1회성) |

### 2. 공통 — 디바이스/푸시 · 유저 상태 · 앱 버전 · 알림

| # | Method | Path | 설명 |
|---|--------|------|------|
| 11 | POST | `/api/v1/users/me/devices` | 푸시 기기 등록/갱신 — 이 기기만 켜고 내 다른 기기는 끈다 — 사용 화면: 로그인 직후 전역 |
| 12 | GET | `/api/v1/users/me/status` | 유저 현재 상태 — 앱 진입·포그라운드 복귀 시 어느 화면으로 갈지 결정 — 사용 화면: 전역 |
| 13 | GET | `/api/v1/app/version` | 플랫폼별 최소·최신 앱 버전 — 업데이트 강제·안내 판정, 인증 불필요 — 사용 화면: 앱 진입(스플래시)·포그라운드 복귀 |
| 14 | GET | `/api/v1/app/stream` | 앱 스트림 (SSE) — 앱이 켜져 있는 동안 유지하는 개인 채널 — 사용 화면: 전역 |
| 15 | GET | `/api/v1/users/me/notifications` | 인앱 알림 목록 (최신순, 항목별 현재 상태 포함) + 안 읽은 수 — 사용 화면: 앱 진입(배지)·알림함 |
| 16 | POST | `/api/v1/users/me/notifications/read` | 알림 모두 읽음 — 알림함을 열 때 |

### 3. 홈 화면

- 날씨: 서버 API 없음 — 클라이언트가 직접 호출 (상세 3번)
- 매칭 시작·취소: REST (아래 5번). 대기 현황은 SSE 스트림으로 수신

### 4. 매칭완료 대기방 · 초대 러닝 (초대 페이지 / 초대 방)

- 대기방 정보·참가자 목록: 매칭 SSE 스트림 (아래 5번) — 초대 방도 같은 스트림을 쓰되 이벤트는 `INVITE_ROOM_ENTRANCE`·`INVITE_ROOM_UPDATED`이고, 초대 현황이 `RoomInfo`에 실린다
- 나가기: `POST /api/v1/match-rooms/leave` — 초대 방도 같다

| # | Method | Path | 설명 |
|---|--------|------|------|
| 17 | POST | `/api/v1/match-rooms/invite` | 초대 방 개설 (시각·거리 자유 입력) — 사용 화면: 초대 페이지 |
| 18 | POST | `/api/v1/match-rooms/{runningRoomId}/invitations` | 친구 초대 (한 명씩, 성공 204) — 사용 화면: 초대 방 |
| 19 | POST | `/api/v1/match-rooms/{runningRoomId}/invitations/accept` | 초대 수락 → 방 입장 — 사용 화면: 알림함 |
| 20 | POST | `/api/v1/match-rooms/{runningRoomId}/invitations/decline` | 초대 거절 — 사용 화면: 알림함 |

### 5. 매칭·러닝 실시간 통신

매칭은 **REST + SSE**, 러닝 구간은 **WebSocket**이다. 전환 절차는 상세 5번 머리말 참고.

**매칭 HTTP**

- 모집 단계(매칭 방·초대 방)의 API는 `/match-rooms` 아래 둔다. 시작 뒤의 방 단위 동작(솔로 개설·결과 조회)은 `/running-rooms`다
  - 예전 경로 `/running-matches`(신청·취소·슬롯·스트림)와 `/running-rooms/invite`·`/running-rooms/{runningRoomId}/invitations`는 없앴다 — 남겨 두지 않는다. 취소는 `DELETE`가 아니라 `POST /match-rooms/leave`다

| # | Method | Path | 설명 |
|---|--------|------|------|
| 21 | POST | `/api/v1/match-rooms` | 매칭 신청 (시각+거리) — 409 `MATCH_SLOT_CLOSED`·`MATCH_COOLDOWN`·`MATCH_ALREADY_IN_PROGRESS`·`ONBOARDING_NOT_COMPLETED` |
| 22 | POST | `/api/v1/match-rooms/leave` | 대기 취소 + 확정 후 나가기 겸용 (서버가 모집 마감 시각으로 분기) |
| 23 | GET | `/api/v1/match-rooms/slots` | 거리·시간대별 대기 인원 — 매칭 입력 모달의 "3명 대기 중" 표시 |
| 24 | GET | `/api/v1/match-rooms/stream` | 방 상태 이벤트 스트림 (SSE) — 매칭 방·초대 방 공용 |
| 25 | POST | `/api/v1/running-rooms/solo` | 솔로 러닝 개시 (매칭 방은 서버가 생성) |

**매칭 SSE** — 이벤트 5종. 연결 직후 현재 상태 스냅샷을 받는다. 방 상태 이벤트는 방 종류로 갈린다 — 매칭 방은 `MATCH_*`, 초대 방은 `INVITE_ROOM_*`만 받는다.

| 이벤트 | 비고 |
|--------|------|
| `MATCH_STARTED` | 매칭 성사 통지 (`RoomInfo`) — 매칭 방만 |
| `MATCH_ROOM_UPDATED` | 방 상태 갱신 (`RoomInfo`) — 인원 변동·연결 직후 스냅샷. 매칭 방만 |
| `INVITE_ROOM_ENTRANCE` | 초대 방 연결 직후 스냅샷 (`RoomInfo` + `startsInMs`) — 초대 방만 |
| `INVITE_ROOM_UPDATED` | 초대 방 상태 갱신 (`RoomInfo`) — 인원·초대 현황 변동. 초대 방만 |
| `RUNNING_READY` | 곧 시작 통지 — `start_at` 직전(리드타임은 운영값)에 서버가 한 번 보낸다. 클라의 `RUNNING_START` 발사 타이머 기준 (5-C) |

**러닝 WebSocket** — `/api/v1/ws/running`, 메시지 9종. 매칭 러닝과 솔로 러닝이 같은 채널을 쓴다. 이 외에 **ack 2종**(`RUNNING_STARTED`·`RUNNING_FINISHED`)과 **헬스 체크 2종**(`HEALTH_CHECK`·`HEALTH_CHECKED`)이 있다.

| 그룹 | 메시지 | 방향 | 비고 |
|------|--------|------|------|
| 카운트 다운 | `RUNNING_START` | C→S | 방 시작(`MATCHED`면 `STARTED`로)과 참가자 시작을 함께 처리 |
| 러닝 중 | `RUNNING_LOCATION_UPDATE` | C→S | 고빈도 — ack 없음. 이 배치로 확정 거리가 목표를 채우면 서버가 종료하고 `RUNNING_FINISHED`를 보낸다 |
| 러닝 중 | `RUNNING_PROGRESS_UPDATED` | S→C | `status` 포함 — 멈춘 것·끊긴 것·끝난 것을 느려진 것과 구분. 상태가 바뀔 때도 나간다 |
| 러닝 중 | `RUNNING_GOAL_PENDING` | S→C | `RUNNING_FINISH`(`forced=false`)의 확정 거리가 목표에 못 미침 — 종료를 확정하지 않고 본인에게 남은 거리를 알린다 |
| 러닝 중 | `RUNNING_COMBO_UPDATED` | S→C | 나란히 달리는 상대와의 콤보 — 참가자 쌍마다 따로 센다 |
| 러닝 중 | `RUNNING_PAUSE` / `RUNNING_RESUME` | C→S | 일시정지·재개 — 상대 화면의 `status` 표시용. 기록 계산은 이 신호가 아니라 좌표의 `pauseSegment`로 한다 |
| 러닝 중 | `RUNNING_FINISH` | C→S | `forced` 플래그로 조기 종료 의사 포함 — 서버가 상태·기록 확정. `forced=false`인데 확정 거리가 목표에 못 미치면 확정하지 않고 `RUNNING_GOAL_PENDING`으로 답한다 |
| 공통 | `ERROR` | S→C | WS 요청 실패 통지 |

### 6. 러닝 중 / 러닝 후 대시보드

| # | Method | Path | 설명 |
|---|--------|------|------|
| 26 | GET | `/api/v1/running-rooms/{runningRoomId}/results` | 참가자 전원 결과 스냅샷 — 사용 화면: 러닝 후 대시보드 |
| 27 | GET | `/api/v1/running-rooms/{runningRoomId}/split-results` | 구간별 상세 + 경로 |

### 7. 기록 화면

| # | Method | Path | 설명 |
|---|--------|------|------|
| 28 | GET | `/api/v1/users/me/running-records` | 내 러닝 기록 목록(기간 조회, 최대 31일) — 사용 화면: 기록 |

### 8. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 29 | GET | `/api/v1/feeds` | 피드 목록, `tab=FRIENDS\|ALL`, 무한 스크롤 |
| 30 | GET | `/api/v1/feeds/{feedId}` | 피드 단건 — 사용 화면: 푸시 랜딩, 프로필 그리드 탭 |
| 31 | POST | `/api/v1/feeds/{feedId}/like` | 좋아요 (응답에 갱신 카운트) |
| 32 | DELETE | `/api/v1/feeds/{feedId}/like` | 좋아요 취소 |
| 33 | GET | `/api/v1/feeds/{feedId}/comments` | 댓글 목록 (등록순, 답글 제외) |
| 34 | POST | `/api/v1/feeds/{feedId}/comments` | 댓글/답글 작성 (`parentCommentId` 옵션, depth 1 제한) |
| 35 | PATCH | `/api/v1/comments/{commentId}` | 댓글 수정 (작성자 본인만) |
| 36 | GET | `/api/v1/comments/{commentId}/replies` | 답글 지연 로딩 ("답글 N개 보기") |
| 37 | DELETE | `/api/v1/comments/{commentId}` | 댓글 삭제 (작성자 or 피드 소유자, 레딧 방식) |
| 38 | POST | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 |
| 39 | DELETE | `/api/v1/comments/{commentId}/like` | 댓글 좋아요 취소 |

### 9. 피드 작성 페이지 (+프로필의 피드 편집) [MVP 제외]

| # | Method | Path | 설명 |
|---|--------|------|------|
| 40 | POST | `/api/v1/feeds/images/presigned-url` | 피드 이미지 업로드 URL 발급 (여러 장) |
| 41 | POST | `/api/v1/feeds` | 피드 작성 (텍스트/이미지 최소 1, 공개범위, 기록 템플릿 `runningRecordId`) |
| 42 | PATCH | `/api/v1/feeds/{feedId}` | 피드 수정 (내용·이미지·공개범위) — 사용 화면: 프로필(피드 편집) |
| 43 | DELETE | `/api/v1/feeds/{feedId}` | 피드 삭제 (소프트delete) |

### 10. 프로필 페이지 (본인/타인)

| # | Method | Path | 설명 |
|---|--------|------|------|
| 44 | GET | `/api/v1/users/me` | 내 기본 정보 — 이메일·로그인 수단 포함(설정 화면이 재사용) — 사용 화면: 전역 |
| 45 | GET | `/api/v1/users/{userId}` | 프로필 요약 (기본 정보·친구 수·관계) |
| 46 | GET | `/api/v1/users/{userId}/feeds` | 피드 그리드 (경량: 썸네일+장수) **[MVP 제외]** |
| 47 | POST | `/api/v1/users/{userId}/friend-request` | 친구 요청 — 사용 화면: 프로필, 사용자 검색 |
| 48 | PUT | `/api/v1/users/{userId}/friend-request` | 친구 요청 수락 — 요청을 친구로 확정 |
| 49 | DELETE | `/api/v1/users/{userId}/friend-request` | 요청 취소(보낸 쪽) · 거절(받은 쪽) |
| 50 | DELETE | `/api/v1/users/{userId}/friend` | 친구 삭제 |
| 51 | GET | `/api/v1/users/{userId}/friends` | 친구 목록 (+이름 검색) — 본인이면 확정·보낸 요청·받은 요청, 타인이면 확정만 |
| 52 | GET | `/api/v1/users/{userId}/colors` | 컬러 컬렉션 (마스터 전체 + 획득 여부) **[MVP 제외]** |
| 53 | GET | `/api/v1/users/search` | 사용자 검색 — 친구 추가 진입점 (`?q=검색어`) |
| 54 | GET | `/api/v1/users/{userId}/achievements` | 업적 (활성 업적별 단계·기준값) |
| 55 | GET | `/api/v1/users/me/friend-recommendations` | 추천 친구 (함께 뛴 사람 + 페이스 비슷한 사람) — 사용 화면: 친구 목록 페이지 |
| 56 | POST | `/api/v1/users/{userId}/block` | 유저 차단 — 사용 화면: 타인 프로필 |
| 57 | DELETE | `/api/v1/users/{userId}/block` | 차단 해제 — 사용 화면: 차단 목록 |
| 58 | GET | `/api/v1/users/me/blocks` | 내 차단 목록 |

### 11. 프로필 편집 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 59 | POST | `/api/v1/users/me/profile-image/presigned-url` | 프로필 사진 업로드 URL 발급 |
| 60 | PATCH | `/api/v1/users/me/profile-image` | 업로드한 사진 반영 — S3 존재·소유자 검증 |
| 61 | GET | `/api/v1/users/{userId}/profile-image` | 프로필 사진 URL 조회 — 인증 불필요 |
| 62 | DELETE | `/api/v1/users/me/profile-image` | 프로필 사진 삭제 — S3 객체는 남기고 키 연결만 끊음 |
| 63 | POST | `/api/v1/users/me/background-image/presigned-url` | 배경 사진 업로드 URL 발급 |
| 64 | PATCH | `/api/v1/users/me/background-image` | 업로드한 배경 사진 반영 — S3 존재·소유자 검증 |
| 65 | GET | `/api/v1/users/{userId}/background-image` | 배경 사진 URL 조회 — 인증 불필요 |
| 66 | DELETE | `/api/v1/users/me/background-image` | 배경 사진 삭제 — S3 객체는 남기고 키 연결만 끊음 |
| 67 | GET | `/api/v1/users/me/profile` | 프로필 편집용 조회 — 소개글·성별·생일·키·몸무게 |
| 68 | PATCH | `/api/v1/users/me/profile` | 프로필 수정 — 소개글·성별·생일·키·몸무게 부분 수정 |
| 69 | PATCH | `/api/v1/users/me/nickname` | 닉네임 변경 (중복 시 409) |
| 70 | POST | `/api/v1/users/nickname/availability` | 닉네임 중복 확인 — 사용 화면: 프로필 편집, 온보딩 |

### 12. 설정 페이지

| # | Method | Path | 설명 |
|---|--------|------|------|
| 71 | PATCH | `/api/v1/users/me/password` | 비밀번호 변경 (로컬 계정만) |
| 72 | GET | `/api/v1/users/me/settings` | 알림 on/off(단일) + 프로필 공개범위 조회 |
| 73 | PATCH | `/api/v1/users/me/settings` | 설정 변경 |
| 74 | DELETE | `/api/v1/users/me` | 회원탈퇴 (스냅샷→하드delete, 테이블별 정책) |

**합계: REST 72개 + SSE 스트림 2개(매칭 이벤트 5종 · 앱 이벤트 3종) + WebSocket 채널 1개(메시지 9종 + ack 2종 + 헬스 체크 2종)**

> 번호는 표의 순서를 그대로 따른다 — 결번을 두지 않는다. 중간에 API가 생기면 이후 번호를 밀고, 번호로 상호 참조하는 노션 명세도 함께 갱신한다.

---

# 상세 명세

## 0. 공통 규칙

- **refreshToken**: 바디로 전달하고 클라이언트 Keychain/Keystore에 보관한다.
- **경로**: 저장 원본은 Google Encoded Polyline, precision 5(소수점 5자리, 약 1m)다. **기록 하나를 크게 그리는 화면(6-1·6-2)은 서버가 풀어 좌표 배열 `routes`로 내리고**(6-1은 조각별 배열, 6-2는 구간별로 잘라 `splits[].routes`에 싣는다), 피드 카드(8-1)는 `routePolyline` 문자열로 내린다. 기록 목록(7-1)은 경로를 싣지 않는다. 어느 쪽이든 정밀도는 precision 5를 넘지 않는다. **`route`라는 단수 필드는 어디에도 두지 않는다** — 좌표 배열은 `routes`, 문자열은 `routePolyline` 두 이름뿐이다.
  - **경로는 끊긴 곳에서 조각으로 나뉘어 저장된다**(`running_segments`). 서버가 기록에서 뺀 구간(일시정지·관측하지 못한 이동·GPS 튐)이 경계다. 버튼 없이 멈춘 정지 구간은 끊지 않는다 — 제자리라 이어 그려도 모양이 틀어지지 않는다([feature-spec.md](feature-spec.md) 트랙 필터)
  - 6-1 `routes`는 조각마다 배열 하나로 내린다. 클라는 조각 안의 점만 선으로 잇고 조각 사이는 잇지 않는다
  - 6-2 `splits[].routes`는 구간이 속한 조각 하나에서 자른 배열이라 조각 사이를 걸치지 않는다. 끊긴 곳에 걸친 구간은 점 하나만 실린다
  - `routePolyline`(8-1)은 서버가 조각을 이어 한 줄로 다시 인코딩한 값이다 — 이웃한 두 점이 30m 넘게 떨어져 있으면 끊긴 곳으로 보고 잇지 않는다. 카드 미리보기용이라 일시정지 중 짧게(약 40m 이하) 옮긴 자리가 이어 그려지는 것은 감수한다
- **좌표 배열 형식**: `[[위도, 경도], [위도, 경도], …]`. 안쪽 배열은 항상 **위도가 먼저**다 — GeoJSON은 경도가 먼저라 반대이므로 그 관례를 따르지 않는다. 단일 지점도 같은 `[위도, 경도]` 두 칸 배열이다. 키 이름을 반복하지 않아 점 수백 개를 실어도 응답이 작다.
  - **조각으로 나뉜 경로**(6-1 `routes`)는 이 배열을 한 겹 더 감싼다 — `[[[위도, 경도], …], [[위도, 경도], …]]`. 끊긴 곳이 없어도 바깥 배열은 있다(조각 하나). 조각에는 점이 하나만 있을 수 있다.
- **친구 관계**: 토글이 아니다. **성립 전 요청은 `/users/{userId}/friend-request` 한 리소스를 메서드로 다룬다** — 요청 `POST`(10-4), 수락 `PUT`(10-5), 취소·거절 `DELETE`(10-6). 성립한 친구의 삭제만 `/users/{userId}/friend`(10-6)다.
- **관계 `relationshipType`**: `NONE` | `FRIEND_REQUEST_SENT` | `FRIEND_REQUEST_RECEIVED` | `FRIEND` — 언제나 **조회하는 사람 기준**이다. 대상이 본인이면 `null`이다(10-2·10-4·10-5·10-7·10-9·2-4가 같은 값을 쓴다). `user_relationships.type`과 같은 값이다.
  - **친구 상태가 아니라 관계라서 `relationshipType`이다** — 친구가 아닌 관계(차단)도 같은 필드에 싣는다.
  - **값이 추가될 수 있다** — 차단(`feature-spec.md` 차단 절)이 붙으면 `BLOCKED`가 생긴다. 클라는 모르는 값을 받으면 친구 버튼을 그리지 않는다.
- **이미지 업로드 공통(Presigned)**: ① 업로드 URL 발급 API → ② 클라가 S3에 직접 업로드 → ③ 반환받은 `key`(또는 완료 API)를 본 API에 전달
- **탈퇴 유저 표시**: 작성자·러닝 참가자는 `{ "userId": "01a0f2d4-...", "nickname": "탈퇴한 사용자", "profileImageUrl": null, "isDeleted": true }`로 반환한다(`userId`는 유지).
- **값이 없는 필드**: 조회 응답에서는 `null`이다(`profileImageUrl`·`introduction`·`relationshipType` 등). 수정 응답(11-2·11-6·11-10·11-11)은 보낸 필드만 담는다 — 11-10에서 보내지 않은 필드는 키째 빠진다(11-2·11-11·11-6은 필드가 하나뿐이다).
- **수정 응답의 범위**: `PATCH`가 본문을 반환하면 저장 후의 리소스 전체 표현을 담는다(12-3). 반환할 표현이 없으면 `204 No Content`다. 위 네 API(11-2·11-6·11-10·11-11)는 예외로 보낸 필드만 담는다. 저장 위치가 여러 테이블로 나뉘는지는 기준이 아니다.
- **`[MVP 제외]` 표기**: 지금 만들지 않는 엔드포인트. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이며, 차수(1차·2차)는 적지 않는다.
- **앱 버전 헤더**: 앱은 서버로 가는 모든 요청(REST·SSE·WebSocket 연결)에 `X-App-Version`(버전 이름, 예: `1.1.0`)과 `X-App-Platform`(`IOS` | `ANDROID`)을 싣는다. 서버는 버전이 그 플랫폼의 최소 버전(2-3 `minimumVersion`)보다 낮으면 **요청을 처리하기 전에** `403 APP_UPDATE_REQUIRED`로 거절한다 — 호환을 깨는 배포 뒤에 구버전 요청이 기록·상태를 틀어지게 하지 않기 위해서다. 앱이 켤 때·돌아올 때 하는 확인(2-3)은 화면을 막을 뿐, 그 사이 이미 떠 있던 앱의 요청은 막지 못한다.
  - **헤더가 없거나 형식이 틀려도 같은 `403 APP_UPDATE_REQUIRED`로 거절한다** — 헤더를 싣지 않는 구버전 앱(안드로이드 `1.0.0`)을 막고, 아무 값이나 실어 검사를 건너뛰지 못하게 한다. 두 헤더 중 하나만 있어도 마찬가지다. 형식은 버전이 `x.y.z`(부분마다 숫자 1~9자리), 플랫폼이 `IOS`·`ANDROID`(대소문자 무시)다
  - **최소 버전을 읽지 못하면 통과시키지 않는다** — 다른 DB 오류와 같이 `500`이다. 통과시키면 막아야 할 요청이 들어온다
  - **토큰 검증보다 먼저 검사한다** — 업데이트가 필요하면 토큰 상태와 무관하게 이 에러가 나간다
  - **검사하지 않는 경로**: 2-3(구버전도 업데이트가 필요하다는 답을 받아야 한다), 헬스 체크·메트릭(`/actuator/*`). 브라우저의 CORS 사전 요청(`OPTIONS`)도 검사하지 않는다 — 헤더 값 없이 이름만 싣기 때문이며, 본 요청은 검사한다
  - **WebSocket은 연결할 때만 검사한다** — 이미 열린 연결은 끊지 않는다. 호환을 깨는 배포는 서버 교체로 연결이 끊기므로 다시 연결할 때 걸린다
  - 서버는 요청마다 최소 버전을 읽는다(캐시 없음) — 바꾸면 다음 요청부터 바로 반영된다

### 공통 에러 응답

인증 필요(`인증: 필요`) API → **401**, 모든 API → **400**·**500** 공통 발생. 등록되지 않은 경로는 **404** `NOT_FOUND`("요청한 리소스를 찾을 수 없습니다.")다. 경로는 있지만 받지 않는 메서드는 **405** `METHOD_NOT_ALLOWED`이고 `Allow` 헤더에 받는 메서드를 담는다. 받지 않는 본문 형식(`Content-Type`)은 **415** `UNSUPPORTED_MEDIA_TYPE`이다. 셋 다 공개 경로가 아니면 토큰 검증이 먼저라 토큰 없이 부르면 401이 나간다(앱 버전 검사는 그보다 먼저라, 걸리면 아래 403이다). 각 엔드포인트 명세엔 특유 에러만 표기. 검증 실패 시 `code`는 `INVALID_REQUEST` 공통이고 `message`로 사유를 구분한다.
`인증: 불필요` API도 `Authorization` 헤더가 있으면 토큰을 검증하며, 실패하면 아래 401을 낸다 — 클라이언트는 공개 API(1-1~1-8·2-3·11-3·11-7·11-12)에 헤더를 싣지 않는다.
앱 버전 헤더가 없거나 형식이 틀리거나 그 플랫폼의 최소 버전보다 낮으면 2-3·`/actuator/*`를 뺀 모든 경로 → **403** `APP_UPDATE_REQUIRED`(위 공통 규칙의 앱 버전 헤더). 클라이언트는 이 에러를 받으면 화면과 무관하게 업데이트 화면으로 보낸다.

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

- **에러 (403 Forbidden — 앱 업데이트 필요)**

```json
{
  "code": "APP_UPDATE_REQUIRED",
  "message": "앱 업데이트가 필요합니다."
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

- **에러 (405 Method Not Allowed — 받지 않는 메서드, `Allow` 헤더 포함)**

```json
{
  "code": "METHOD_NOT_ALLOWED",
  "message": "지원하지 않는 요청 메서드입니다."
}
```

- **에러 (415 Unsupported Media Type — 받지 않는 본문 형식)**

```json
{
  "code": "UNSUPPORTED_MEDIA_TYPE",
  "message": "지원하지 않는 요청 본문 형식입니다."
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
  "userId": "01a0f2d4-6180-7000-8000-446655440001",
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
- **푸시 기기**: 로그인하면 그 유저의 기기를 전부 끈다 — 다른 기기의 세션이 이 로그인으로 끝나서다. 이 기기는 등록(2-1)하면 켜진다. 기존 회원의 소셜 로그인(1-5~1-7)도 같다

- **Response `200 OK`**

```json
{
  "userId": "01a0f2d4-6180-7000-8000-446655440001",
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

### 1-5. `POST /api/v1/auth/oauth/google` — 구글 로그인 (ID 토큰)

- **Request** (필수)

```json
{
  "idToken": "ey..."
}
```

앱은 `google_sign_in`을 `serverClientId`(서버의 웹 클라이언트 ID)로 초기화하고, 로그인 결과의 ID 토큰을 그대로 보낸다. iOS는 iOS 클라이언트 ID를 `clientId`로 함께 넘겨야 한다 — 빠지면 토큰의 `aud`가 iOS 클라이언트 ID로 찍혀 401이다.

- **동작**
  - 서버가 구글 공개키로 ID 토큰 서명을 검증하고 `iss`(구글)·`aud`(서버의 웹 클라이언트 ID)·만료를 확인한다. `email_verified`가 `true`인 이메일만 쓴다
  - `provider_id`로 `oauth_users` 조회, 없으면 생성(회원가입) → 자체 토큰 발급
- **Response `200 OK`**: 1-4 로그인과 동일 형태. 최초 가입 여부와 무관하게 토큰을 발급한다
- **에러 (401 Unauthorized — ID 토큰 검증 실패(위조·만료·다른 앱용))**

```json
{
  "code": "OAUTH_LOGIN_FAILED",
  "message": "소셜 로그인에 실패했습니다. 다시 시도해 주세요."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "ID 토큰은 필수입니다."
}
```

- **에러 (403 Forbidden — 이메일 제공 미동의 — 가입·로그인 거부)** — 이미 가입한 회원도 로그인할 때마다 검사한다. 소유가 확인되지 않은 이메일(`email_verified`가 `true`가 아님)도 여기에 해당한다

```json
{
  "code": "OAUTH_EMAIL_NOT_PROVIDED",
  "message": "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다."
}
```

- **에러 (409 Conflict — 소셜 최초 가입인데 이메일이 기존 계정(로컬 또는 다른 소셜)과 겹침)** — 자동 연동하지 않는다. 응답에 기존 가입 수단이 없으므로 클라는 수단을 특정하지 않고 아래 문구로 안내한다

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **에러 (503 Service Unavailable — 구글 공개키를 받아 오지 못함)** — 사용자 자격 증명 문제가 아니다. 클라는 잠시 후 다시 시도하게 한다

```json
{
  "code": "OAUTH_PROVIDER_UNAVAILABLE",
  "message": "소셜 로그인에 잠시 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
}
```

- **인증**: 불필요

### 1-6. `POST /api/v1/auth/oauth/kakao` — 카카오 로그인 (인가 코드 + PKCE)

- **Request** (둘 다 필수)

```json
{
  "authorizationCode": "...",
  "codeVerifier": "..."
}
```

- **동작**
  - 서버가 카카오에 인가 코드를 교환(PKCE `codeVerifier` 검증)하고 유저 정보를 조회한다. 유효하고(`is_email_valid`) 인증된(`is_email_verified`) 이메일만 쓴다
  - `provider_id`로 `oauth_users` 조회, 없으면 생성(회원가입) → 자체 토큰 발급
- **Response `200 OK`**: 1-4 로그인과 동일 형태. 최초 가입 여부와 무관하게 토큰을 발급한다
- **에러 (401 Unauthorized — 코드 교환 실패(위조·만료·PKCE 불일치))**

```json
{
  "code": "OAUTH_LOGIN_FAILED",
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
```

- **에러 (403 Forbidden — 이메일 제공 미동의 — 가입·로그인 거부)** — 이미 가입한 회원도 로그인할 때마다 검사한다. 인증되지 않은 이메일, 다른 카카오계정에 사용돼 만료된 이메일(`is_email_verified`·`is_email_valid`가 `true`가 아님)도 여기에 해당한다. 만료된 이메일은 카카오가 마스킹해서 준다

```json
{
  "code": "OAUTH_EMAIL_NOT_PROVIDED",
  "message": "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다."
}
```

- **에러 (409 Conflict — 소셜 최초 가입인데 이메일이 기존 계정(로컬 또는 다른 소셜)과 겹침)** — 자동 연동하지 않는다. 응답에 기존 가입 수단이 없으므로 클라는 수단을 특정하지 않고 아래 문구로 안내한다

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **에러 (503 Service Unavailable — 카카오 5xx·호출 한도 초과·연결 실패)** — 사용자 자격 증명 문제가 아니다. 한도 초과는 429 또는 400 + 카카오 `code: -10`으로 온다. 클라는 잠시 후 다시 시도하게 한다

```json
{
  "code": "OAUTH_PROVIDER_UNAVAILABLE",
  "message": "소셜 로그인에 잠시 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
}
```

- **인증**: 불필요

### 1-7. `POST /api/v1/auth/oauth/apple` — 애플 로그인 (ID 토큰 + nonce)

- **Request** (둘 다 필수)

```json
{
  "idToken": "ey...",
  "nonce": "..."
}
```

앱은 로그인마다 무작위 `nonce`를 만들고, 그 **SHA-256 해시(소문자 hex)**를 애플 로그인 요청의 `nonce`로 넘긴 뒤, 애플이 돌려준 ID 토큰과 **해시 전 원본 `nonce`**를 그대로 보낸다.

- **동작**
  - 서버가 애플 공개키로 ID 토큰 서명을 검증하고 `iss`(애플)·`aud`(iOS 앱 번들 ID)·만료를 확인한다. 토큰의 `nonce` 클레임이 보낸 `nonce`의 SHA-256 해시와 같아야 한다 — 다른 곳에서 빼낸 토큰으로 로그인하는 것을 막는다. `email_verified`가 `true`인 이메일만 쓴다(프라이빗 릴레이 이메일도 그대로 쓴다)
  - `provider_id`로 `oauth_users` 조회, 없으면 생성(회원가입) → 자체 토큰 발급
- **Response `200 OK`**: 1-4 로그인과 동일 형태. 최초 가입 여부와 무관하게 토큰을 발급한다
- **에러 (401 Unauthorized — ID 토큰 검증 실패(위조·만료·다른 앱용·`nonce` 불일치))**

```json
{
  "code": "OAUTH_LOGIN_FAILED",
  "message": "소셜 로그인에 실패했습니다. 다시 시도해 주세요."
}
```

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "ID 토큰은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "nonce는 필수입니다."
}
```

- **에러 (403 Forbidden — 이메일 제공 미동의 — 가입·로그인 거부)** — 이미 가입한 회원도 로그인할 때마다 검사한다. 소유가 확인되지 않은 이메일(`email_verified`가 `true`가 아님)도 여기에 해당한다

```json
{
  "code": "OAUTH_EMAIL_NOT_PROVIDED",
  "message": "이메일 제공에 동의해야 소셜 로그인을 할 수 있습니다."
}
```

- **에러 (409 Conflict — 소셜 최초 가입인데 이메일이 기존 계정(로컬 또는 다른 소셜)과 겹침)** — 자동 연동하지 않는다. 응답에 기존 가입 수단이 없으므로 클라는 수단을 특정하지 않고 아래 문구로 안내한다

```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "이미 가입된 이메일입니다. 로그인해 주세요."
}
```

- **에러 (503 Service Unavailable — 애플 공개키를 받아 오지 못함)** — 사용자 자격 증명 문제가 아니다. 클라는 잠시 후 다시 시도하게 한다

```json
{
  "code": "OAUTH_PROVIDER_UNAVAILABLE",
  "message": "소셜 로그인에 잠시 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
}
```

- **인증**: 불필요

### 소셜 로그인 미지원 provider — `POST /api/v1/auth/oauth/{provider}`

> 1-5(`google`)·1-6(`kakao`)·1-7(`apple`) 외의 경로를 받는 자리다. 호출할 API가 아니라 목록에 없는 provider를 거절하는 규칙이라 번호를 붙이지 않는다.

- **동작**: `{provider}`가 소문자 `google`·`kakao`·`apple`이 아니면 404가 아니라 400으로 거절한다. 본문은 보지 않는다. `GOOGLE`·`KAKAO`처럼 대소문자가 다른 경로도 400이다
- **에러 (400 Bad Request)**

```json
{
  "code": "UNSUPPORTED_PROVIDER",
  "message": "지원하지 않는 로그인 제공자입니다."
}
```

- **인증**: 불필요

### 1-8. `POST /api/v1/auth/refresh` — 토큰 재발급

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

### 1-9. `POST /api/v1/auth/logout` — 로그아웃

- **Request**: 본문 없음 — 서버가 요청 토큰으로 본인 식별
- **동작**: ① **해당 access 토큰을 서버 차단(블랙리스트)** 처리해 만료 전이라도 무효화 (이후 그 토큰 요청은 `401 TOKEN_BLOCKED`) ② **저장된 리프레시 토큰을 삭제** — 남겨두면 1-8로 새 access 토큰을 받아 로그아웃이 무효가 된다 ③ **그 유저의 푸시 기기(2-1)를 전부 끈다** — 다시 로그인해 등록하면 켜진다
- **로그인 세션은 계정당 하나**라 이 삭제가 그 계정의 모든 기기에 미친다. 기기별 세션은 `feature-spec.md` 설정 페이지 절 참고 (**[MVP 제외]**)
- **Response**: `204 No Content`

- **인증**: 필요

### 1-10. `POST /api/v1/users/onboarding` — 온보딩 입력

- **Request** (전부 필수)

```json
{
  "nickname": "완두콩",
  "gender": "MALE",                  // MALE | FEMALE (대소문자 무시, 저장·응답은 대문자)
  "birthday": "1998-12-16",
  "averagePaceSecondsPerKm": 359,    // 초/km 정수 (5'59") — 초기값. 이후 러닝 기록 기반 서버 자동 갱신 (수정 UI 없음)
  "weightKg": 77,
  "heightCm": 175
}
```

- **키·몸무게는 소수점 첫째 자리로 정규화한다** — 둘째 자리 이하는 반올림해 저장하며 범위는 20 이상 300 이하다(11-10과 같다)
- **약관 동의**: 별도 요청 필드 없음 — **가입 흐름 첫 화면**에서 받고 가입 자체를 동의로 갈음한다(근거는 `feature-spec.md` 약관 동의 화면 절)
- **만 14세 미만은 받지 않는다** — 법정대리인 동의 절차가 없어 개인정보를 처리할 수 없다. 생년월일로 만 나이를 계산해 400으로 막고, 프로필 수정(11-10)에서 생년월일을 바꿀 때도 같은 기준이다

- **Response `201 Created`**

```json
{
  "userId": "01a0f2d4-6180-7000-8000-446655440001",
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
  "message": "만 14세 미만은 서비스를 이용할 수 없습니다."
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

## 2. 공통 — 디바이스/푸시 · 유저 상태 · 앱 버전

### 2-1. `POST /api/v1/users/me/devices` — 푸시 기기 등록/갱신

- **화면**: 전역 — 앱이 아래 때마다 부른다. 실패해도 로그인과 화면 진행을 막지 않는다
  - 스플래시에서 버전 확인(2-3) 뒤 로그인 상태일 때
  - 로그인·가입·소셜 로그인(첫 가입 포함)에 성공했을 때
  - 알림 권한을 허용해 토큰이 처음 생겼을 때, 토큰이 바뀌었을 때
  - 포그라운드 복귀 때는 앞선 등록이 실패로 남아 있을 때만
  - 다른 계정으로 바꾸면 다시 등록하고, 로그아웃하면 대기 중인 등록을 취소한다. `403 APP_UPDATE_REQUIRED`를 받으면 재시도하지 않는다
- **Request**

```json
{
  "deviceId": "3f1c2a9e-7b4d-4e8a-9c21-5d6f0b8e1a37",
  "pushToken": "fcm-token-..."
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `deviceId` | String | O | 앱이 설치 때 만들어 보관하는 기기 식별자(앱은 UUID를 쓴다). 100자 이하. 서버는 형식을 보지 않는다 |
| `pushToken` | String | O | FCM 등록 토큰. 4096자 이하 |

- **플랫폼·앱 버전은 본문에 싣지 않는다** — 모든 요청에 싣는 `X-App-Platform`·`X-App-Version` 헤더(0. 공통 규칙)에서 읽어 저장한다
- **동작**: `deviceId` 기준 upsert — 없으면 만들고, 있으면 토큰·플랫폼·앱 버전·주인을 덮어쓰고 켠다. 같은 요청을 다시 보내도 행이 늘지 않는다
  - **푸시는 유저당 마지막에 등록한 기기 하나로만 간다** — 등록하면 그 유저의 다른 기기는 꺼진다. 로그인 세션이 계정당 하나라서다(1-9)
  - 다른 계정이 쓰던 `deviceId`면 행의 주인을 요청한 유저로 바꾼다 — 한 기기에서 계정을 바꿔 로그인한 경우다
  - 로그인(1-4~1-7)·로그아웃(1-9)하면 그 유저의 기기가 전부 꺼지고, 탈퇴(12-4)하면 지워진다 — 로그인 뒤 등록에 실패해도 이전 기기로 푸시가 가지 않는다. 단 이전 기기가 남은 액세스 토큰으로 다시 등록하면 그 기기로 옮겨 간다
- **Response**: `204 No Content`
- **인증**: 필요
- **에러 (400 Bad Request)**: 필드가 없거나 비었거나 길이를 넘으면 공통 문구로 나간다

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

### 2-2. `GET /api/v1/users/me/status` — 유저 현재 상태

- **화면**: 앱 진입·포그라운드 복귀 — 어느 화면으로 갈지, 어떤 채널에 연결할지 정한다
- **이 경로는 "지금 무엇을 하는 중인가"만 담는다.** 계정 상태·온보딩 여부·설정처럼 활동과 무관한 것은 넣지 않는다(`api-convention.md`)
- **Response `200 OK`**

```json
{
  "status": "RUNNING",                          // IDLE | WAITING | READY | RUNNING
  "type": "MATCH",                              // MATCH | SOLO | INVITE — status가 IDLE이면 null
  "runningRoomId": 125,                         // status가 IDLE이면 null
  "scheduledStartAt": "2026-07-25T19:00:00",    // status가 IDLE이면 null
  "targetDistanceMeters": 5000,                 // status가 IDLE이거나 목표 없는 솔로 방이면 null
  "cooldownUntil": null                         // 제재 쿨다운이 남아 있으면 그 시각
}
```

- **`status`는 저장값이 아니라 파생값이다** — 상태 컬럼을 따로 두지 않고 활성 신청·방 상태·모집 마감 시각으로 계산한다. `feature-spec.md`의 홈 화면 상태 규칙과 같은 판정이며 러닝 구간까지 확장한 것이다

| `status` | 조건 | 클라이언트의 다음 동작 |
|---|---|---|
| `IDLE` | 활성 신청이 없음 | 홈. `cooldownUntil`이 있으면 매칭 버튼에 남은 시간을 표시 |
| `WAITING` | 방이 `MATCHING`이고 모집 마감 전 | 매칭 스트림 연결 — 방 정보는 연결 직후 `RoomInfo` 스냅샷이 나른다 |
| `READY` | 방이 `MATCHED` | `MATCH`·`INVITE`면 스트림 연결 + 카운트다운, `SOLO`면 곧바로 WS `RUNNING_START` |
| `RUNNING` | 방이 `STARTED` | WS 연결 → `RUNNING_START` → 로컬 트랙 재전송 |

- **마감이 지난 `MATCHING` 방은 `READY`로 답한다.** 확정 예약이 아직 깨지 않은 틈에 조회하면 방은 `MATCHING`이지만 곧 확정될 자리다 — 대기 화면을 그리게 두면 잠시 뒤 화면이 다시 바뀐다. 매칭 취소가 방 상태가 아니라 마감 시각으로 분기하는 것과 같은 이유다
- **참가자 상태(`running_players.status`)는 보지 않는다.** 방이 `STARTED`인데 본인은 아직 `JOINED`(WS 미접속)일 수 있으나 클라이언트가 할 일은 같고 `RUNNING_START`가 멱등이라 구분할 이유가 없다
- **방 정보를 담지 않는다.** 참가자 목록·팀 평균 페이스·모집 마감 시각은 방의 속성이고, 매칭 중에는 스트림이 `RoomInfo`로, 러닝 중에는 `RUNNING_STARTED` ack가 나른다. 여기 실으면 같은 값이 두 경로로 내려가 어긋날 여지가 생긴다
  - 다만 `scheduledStartAt`·`targetDistanceMeters`는 담는다 — 러닝 중 복구에서 경과 시간·목표를 그리는 데 필요하고, 방 정보를 조립하지 않고도 한 행에서 나온다
  - 두 값 모두 **`running_rooms` 쪽이 정본이다.** `running_players.target_distance`는 NOT NULL이라 목표 없는 솔로에도 값이 들어가므로, `null`을 그대로 보내려면 방의 값을 써야 한다
- **`type`이 `INVITE`면 초대 방이다** — 상태 판정과 클라가 할 일은 `MATCH`와 같다(초대 방 화면을 그릴지만 가른다). 초대를 받기만 한 사람은 활성 신청이 없어 `IDLE`이다
- **`WAITING`은 `MATCH`·`INVITE`만 가능하다** — 솔로는 모집 단계 없이 `MATCHED`로 태어난다
- **인증**: 필요

### 2-3. `GET /api/v1/app/version` — 앱 버전 확인

- **화면**: 앱 진입(스플래시)·포그라운드 복귀 — 스플래시에서는 토큰 복원보다 먼저 부른다
- **Query**: `platform`(필수, `IOS` | `ANDROID`, 대소문자 무시)
- **Response `200 OK`**

```json
{
  "minimumVersion": "1.1.0",
  "latestVersion": "1.2.0"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `minimumVersion` | String | O | 이 버전보다 낮으면 앱을 쓸 수 없다 |
| `latestVersion` | String | O | 스토어에 공개된 최신 버전. `minimumVersion` 이상이다 |

- **화면 판정은 클라이언트가 한다** — 앱 자기 버전과 두 값을 비교한다. 서버가 최소 버전 미만 요청을 거절하는 것은 별개다(0. 공통 규칙의 앱 버전 헤더) — 이 API는 그 검사에서 빠진다

| 앱 버전 | 클라이언트 동작 |
|---|---|
| `minimumVersion` 미만 | 업데이트 화면. 닫을 수 없고 스토어 이동만 둔다. 화면이 떠 있는 동안에도 포그라운드 복귀·재시도 때 다시 확인해, 최소 버전이 내려가 있으면 풀어 준다 |
| `minimumVersion` 이상, `latestVersion` 미만 | 업데이트 안내. 닫고 계속 쓸 수 있다 |
| `latestVersion` 이상 | 그대로 진행 |

- **버전은 `MAJOR.MINOR.PATCH` 숫자 세 부분이고, 부분마다 9자리까지다.** 부분마다 정수로 비교한다 — 문자열로 비교하면 `1.10.0`이 `1.9.0`보다 낮게 나온다
- **비교 대상은 빌드 번호가 아니라 버전 이름이다**(스토어에 표시되는 버전). 빌드 번호는 업로드 순번이라 기준으로 쓰지 않는다 — 대신 운영에 내보내는 빌드는 버전 이름을 반드시 올린다
- **값은 플랫폼마다 따로 둔다** — 심사·공개 시점이 플랫폼마다 달라, 한쪽에만 나온 버전을 다른 쪽에 요구하지 않기 위해서다
- **스토어 주소는 내려주지 않는다** — 바뀌지 않는 값이라 앱이 가진다
- **`Authorization` 헤더를 싣지 않는다** — 공개 API도 헤더가 있으면 토큰을 검증하므로, 만료된 토큰을 실으면 버전 확인이 401로 막힌다
- 스플래시에서 서버에 닿지 못하면 다른 요청과 같이 재시도한다 — 판정 없이 진입시키지 않는다. 포그라운드 복귀 때 실패하면 쓰던 화면을 그대로 둔다

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "플랫폼은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "플랫폼은 IOS 또는 ANDROID이어야 합니다."
}
```

- **인증**: 불필요

#### 앱 스트림 — `GET /api/v1/app/stream` (SSE)

- **인증**: 필요 / **Content-Type**: `text/event-stream`
- **앱이 켜져 있는 동안 연다** — 앱 진입·포그라운드 복귀 때 연결한다. 활성 신청과 무관하게 **로그인만 하면 언제나 열린다**(매칭 스트림 5-A와 다르다 — 거절하는 경우가 없다)
- **연결 직후 보내는 스냅샷은 없다** — 첫 바이트는 주석 라인(`:connected`)이다. 상태는 2-2로, 알림 목록과 안 읽은 수는 2-4로 받는다
- **앱과의 연결이라 `/app` 아래에 둔다** — 사용자 정보가 아니라 앱이 켜져 있는 동안 서버와 잇는 채널이다. 인증은 필요하다(인증 없이 열린 경로는 2-3 하나뿐이다)
- **매칭 스트림과 별개다** — 매칭 중에는 둘 다 열려 있다. 한쪽 연결이 다른 쪽을 밀어내지 않는다
- **keep-alive**: 15초마다 주석 라인(`:ping`)을 보낸다 — 이벤트가 아니므로 클라 이벤트 핸들러에 잡히지 않는다
- **서버도 스트림을 닫는다** — 열린 지 운영값(현재 30분)이 지나면, 같은 사용자의 새 연결이 들어오면 닫는다(새 연결은 그 요청을 처리한 서버 인스턴스에 붙은 스트림만 닫는다). 클라는 다시 연결한다 — 끊겨 있던 사이의 알림은 2-4로 다시 받는다
  - 탈퇴 시 닫기는 아직 없다 — 탈퇴 개편 때 매칭 스트림과 같은 방식으로 붙인다. 그전까지 탈퇴한 사람의 스트림은 30분 만료로 정리된다
- **이벤트 3종 — 알림 종류가 곧 이벤트 이름이다**(매칭 스트림처럼 SSE `event`로 가른다): `FRIEND_REQUESTED`(친구 요청 받음) · `FRIEND_ACCEPTED`(내 요청이 수락됨) · `RUNNING_INVITED`(러닝 초대 받음, 다시 초대 포함)
  - `data`는 셋 다 2-4 `items[]` 원소에서 **`type`만 뺀 모양**이다(같은 조립 코드로 만든다). 종류는 이벤트 이름이 말한다. 클라는 이벤트 이름을 `type`으로 붙여 목록 맨 위에 넣고 배지를 하나 올린다
  - 모르는 `event`는 무시한다 — 종류가 늘 수 있다
  - **받는 사람에게만 간다** — 알림을 만든 동작(친구 요청·수락·초대)이 커밋된 뒤에 보낸다. 실패해 롤백된 동작은 아무것도 보내지 않는다
  - **서버가 여러 대라 Redis를 거친다** — 커밋한 서버가 받는 사람과 알림 ID만 발행하고, 받는 사람이 붙은 서버만 알림을 읽어 조립해 보낸다
  - **놓칠 수 있다** — 앱이 꺼져 있었거나, 연결이 끊긴 사이이거나, Redis가 잠시 닿지 않으면 오지 않는다. 알림은 DB에 남아 다음 진입 때 2-4로 보인다
  - **`unreadCount`를 싣지 않는다** — 클라가 하나씩 올리고, 다음 진입 때 2-4 값으로 다시 맞춘다

```
:connected

event:FRIEND_REQUESTED
data:{"notificationId":912,"isRead":false,"createdAt":"2026-10-28T12:03:00","actor":{"userId":"01a0f2d4-...","nickname":"철수","profileImageUrl":null,"isDeleted":false,"relationshipType":"FRIEND_REQUEST_RECEIVED"},"invitation":null}

:ping
```

### 2-4. `GET /api/v1/users/me/notifications` — 인앱 알림 목록

- **화면**: 알림함
- **Query**: `cursor`/`limit` — 최신순
- **Response `200 OK`**

```json
{
  "items": [
    {
      "notificationId": 912,
      "type": "RUNNING_INVITED",            // FRIEND_REQUESTED | FRIEND_ACCEPTED | RUNNING_INVITED
      "isRead": false,
      "createdAt": "2026-07-25T12:03:00",
      "actor": {
        "userId": "01a0f2d4-6180-7000-8000-446655440013",
        "nickname": "철수",
        "profileImageUrl": "https://...",
        "isDeleted": false,
        "relationshipType": "FRIEND"        // 지금 나와 이 사람의 관계 — 모든 종류에 있다
      },
      "invitation": {                       // RUNNING_INVITED만
        "runningRoomId": 130,
        "status": "PENDING",                // PENDING | ACCEPTED | DECLINED | LEFT | EXPIRED — 지금 상태
        "scheduledStartAt": "2026-07-25T19:30:00",
        "targetDistanceMeters": 7000
      }
    }
  ],
  "nextCursor": "...",
  "unreadCount": 3                          // 보관 기간 안의 안 읽은 알림 전체 수 — 페이지와 무관하다
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `type` | String | O | 알림 종류 — erd §6 `user_notifications.type` |
| `isRead` | Boolean | O | 읽음 여부 |
| `actor` | Object | O | 알림을 일으킨 사람. 탈퇴했으면 §0 탈퇴 유저 형식 |
| `actor.relationshipType` | String | X | **지금** 나와 이 사람의 관계(§0). 알림 종류와 무관하게 늘 싣는다. 탈퇴한 사람이면 `null` |
| `invitation` | Object | X | `RUNNING_INVITED`일 때만 있고 그 밖에는 `null` |
| `invitation.status` | String | O | 초대의 **지금** 상태 — `PENDING`이면 수락·거절 버튼을 그린다 |

- **알림은 사건의 기록이고 버튼은 지금 상태로 그린다** — 요청이 취소됐거나 초대가 만료됐어도 알림은 남는다. 그래서 `actor.relationshipType`·`invitation.status`는 저장값이 아니라 조회 시점 값이다(근거는 `feature-spec.md` 인앱 알림 저장 절)
  - 친구 요청 알림: `actor.relationshipType`이 `FRIEND_REQUEST_RECEIVED`면 수락(10-5)·거절(10-6) 버튼, 그 밖이면 결과만 보여준다
  - **관계를 `actor` 안에 둔다** — 사람에 딸린 값이라 사람 객체에 붙인다. 종류마다 있고 없고가 갈리지 않아 클라 분기가 줄고, 다른 목록(10-7·10-9)이 사람마다 관계를 싣는 것과 같은 결이다
  - 초대 알림: `PENDING`이면 수락(4-3)·거절(4-4) 버튼, `ACCEPTED`면 탭하면 초대 방으로 간다
- **`unreadCount`로 하단 탭 배지를 그린다** — 앱 진입 때 첫 페이지와 함께 받는다. 이후 새 알림은 앱 스트림(2-3 아래) 이벤트로 받아 클라가 하나씩 올리고, 다음 진입 때 이 값으로 다시 맞춘다. 별도 안 읽은 수 API는 두지 않는다
  - 행 기준으로 센다 — 목록에서 빠지는 어긋난 행(되살리지 못한 payload·사라진 초대)까지 세어, 아주 드물게 배지가 목록보다 클 수 있다. 알림함을 열면 2-5로 0이 된다
- **보관 기간(운영값, 현재 30일)이 지난 알림은 나오지 않고 세지도 않는다**
- **어긋난 행은 그 항목만 빼고 나머지를 돌려준다** — 알림 하나 때문에 알림함 전체가 열리지 않게 하지 않는다
- **`cursor`가 깨졌으면 `400 INVALID_REQUEST`다**
- **이 조회는 읽음 처리를 하지 않는다** — 읽음은 2-5가 한다. 알림함을 열 때 이 목록을 먼저 그린 뒤 2-5를 불러야 새 알림을 표시할 수 있다
- **인증**: 필요

### 2-5. `POST /api/v1/users/me/notifications/read` — 알림 모두 읽음

- **화면**: 알림함 — 열 때 한 번 호출한다
- 본인의 안 읽은 알림을 모두 읽음으로 바꾼다. 항목별 읽음 처리는 두지 않는다
- 안 읽은 알림이 없어도 성공한다
- **Response `204 No Content`**
- **인증**: 필요

## 3. 홈 화면 — 날씨

홈 화면 날씨는 클라이언트가 키 없는 무료 날씨 API를 직접 호출한다. 서버 API는 없다.

## 4. 초대 러닝

친구끼리 시각·거리를 정해 뛰는 방이다. **방이 생기는 방식(4-1)과 사람이 들어오는 방식(4-2~4-4), 그리고 모집 마감 시각과 제재 없음만 매칭과 다르고, 그 밖은 매칭 방과 같은 경로를 탄다** — 방 정보는 매칭 스트림의 `RoomInfo`(5-B)로 받고, 나가기는 `POST /match-rooms/leave`(5-A), 카운트다운·러닝은 5-C·5-D 그대로다. 생명주기 근거는 `feature-spec.md` 초대 러닝 생명주기 절.

- 초대 방은 `running_rooms.type='INVITE'`로 만든다 — 랜덤 매칭 후보 스캔(`type='MATCH'`)과 슬롯별 대기 인원 집계에 잡히지 않는다.
- 방장은 없으며 현재 방 참가자 누구나 친구를 초대할 수 있다. 초대할 수 있는 친구 수는 제한하지 않고, 한 사람이 같은 친구에게 보내는 횟수(처음 포함)만 운영 설정(`invite.max-invite-count`, 현재 5)까지다.
- 방은 최대 4명이며 먼저 수락한 순서대로 입장시키고, 자리가 차면 이후 수락은 막는다.
- **매칭 방에서는 초대할 수 없다** — 4-2는 초대 방이 아니면 404다.
- **모집 마감(`closeAt`)은 `start_at - 초대 마감 오프셋`(운영 설정 `invite.close-offset`, 현재 1분)이다** — 매칭의 마감 오프셋(10분)과 따로 둔다. 초대·수락은 그 전까지만 된다. 시작 통지(`RUNNING_READY`, 시작 10초 전)는 매칭 방과 같다.
- **제재가 없다** — 언제 나가든(5-A), 조기 종료하든(5-D) `_NO_PENALTY`이고 쿨다운을 걸지 않는다.
- **방 상태 이벤트가 따로 있다** — 연결 직후 스냅샷은 `INVITE_ROOM_ENTRANCE`, 그 뒤 갱신은 `INVITE_ROOM_UPDATED`다(5-B). 매칭 방의 `MATCH_STARTED`·`MATCH_ROOM_UPDATED`는 보내지 않는다 — 초대 방의 마감은 "더 이상 초대받지 않는다"일 뿐 매칭 성사가 아니라 확정 연출을 두지 않는다. `RUNNING_READY`(5-C)는 매칭 방과 같다.

### 4-1. `POST /api/v1/match-rooms/invite` — 초대 방 개설

- **화면**: 초대 페이지
- **Request**

```json
{
  "scheduledStartAt": "2026-07-25T07:30:00",
  "targetDistanceMeters": 7000
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `scheduledStartAt` | String | O | KST 시작 시각. **분 단위**(초는 `00`). 선택지가 아니라 자유 입력이다 |
| `targetDistanceMeters` | Integer | O | 목표 거리(m). 운영 설정의 최소·최대 거리 사이(현재 1,000~42,195) |

- **시각·거리를 선택지로 좁히지 않는다** — 매칭(5-A)과 다른 점이다. 범위만 운영 설정으로 막는다
  - 거리가 범위를 벗어나거나 시각이 분 단위가 아니면 `400 INVALID_REQUEST`. 둘 다 어긋나면 두 문장을 공백으로 이어 한 번에 보낸다(매칭 `INVALID_MATCH_OPTION`과 같다). 거리 문구의 숫자는 운영값으로 만든다
    - "시작 시각은 분 단위로만 선택할 수 있습니다."
    - "목표 거리는 1000m 이상 42195m 이하여야 합니다."
  - 필드 누락은 Request DTO 검증이다 — "시작 시각은 필수입니다." / "목표 거리는 필수입니다."
  - 시작 시각은 "지금 + 최소 준비 시간(운영 설정 `invite.min-lead-time`, 현재 10분)" 이후, **오늘(KST) 23:59** 이하여야 한다 — 벗어나면 `409 INVITE_START_AT_OUT_OF_RANGE`. 지금 시각에 따라 갈리는 판정이라 요청 검증이 아니다(매칭의 `MATCH_SLOT_CLOSED`와 같다)
- **동작**: 방을 `type='INVITE'`, `status='MATCHING'`, `max_player_count=4`, `current_player_count=1`로 만들고 본인 `running_players(status='JOINED')`와 배정 세션을 함께 만든다. 예약도 매칭 방과 같게 건다
- 페이스는 온보딩 값을 쓴다(매칭과 같다)
- **매칭 쿨다운 중에도 만들 수 있다** — 쿨다운은 매칭 신청만 막는다
- 응답을 받으면 클라는 `GET /match-rooms/stream`을 연다 — 연결 직후 `INVITE_ROOM_ENTRANCE` 스냅샷이 온다
- **Response `201 Created`**

```json
{
  "runningRoomId": 130
}
```

- **에러 (409 Conflict)**
  - `INVITE_START_AT_OUT_OF_RANGE` — 시작 시각이 만들 수 있는 범위 밖이다
  - `RUNNING_ALREADY_IN_PROGRESS` — 진행 중인 러닝이나 활성 신청(매칭·초대 방)이 있다
  - `ONBOARDING_NOT_COMPLETED` — 온보딩 전이라 쓸 페이스가 없다

```json
{
  "code": "INVITE_START_AT_OUT_OF_RANGE",
  "message": "선택할 수 없는 시작 시각입니다."
}
```

- **인증**: 필요

### 4-2. `POST /api/v1/match-rooms/{runningRoomId}/invitations` — 친구 초대

- **화면**: 초대 방 — 친구 초대 버튼. **한 번에 한 명씩 보낸다** — 여러 명을 고르는 화면이면 클라가 사람마다 한 번씩 부르고 결과를 사람마다 보여준다(방 정원이 4명이라 많아야 3번이다)
- **Request**

```json
{
  "inviteeId": "01a0f2d4-6180-7000-8000-446655440013"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `inviteeId` | String | O | 초대할 친구의 `userId` |

- **요청 검증 (`400 INVALID_REQUEST`)** — Request DTO 검증이다
  - 필드 누락: "초대할 친구는 필수입니다."
  - `userId`가 UUID 형식이 아니면 검증 전에 본문 파싱이 실패한다 — `400 MALFORMED_REQUEST_BODY`("요청 본문을 읽을 수 없습니다.", §0 공통)
- **조건**: 호출자가 이 방의 현재 참가자이고, 방이 `INVITE`·`MATCHING`이며 모집 마감 전이고, 대상이 친구(`FRIEND`)이며 지금 아무 활성 신청도 없어야 한다
  - **못 보내면 건너뛰지 않고 이유를 에러로 돌려준다** — 아래 에러 표. 클라는 메시지를 그대로 보여주면 된다
  - **대기 중인 친구에게도 다시 보낼 수 있다** — 초대 상태는 `PENDING` 그대로이고 호출자의 보낸 횟수만 오른다(독촉)
  - 거절(`DECLINED`)했거나 수락 뒤 나간(`LEFT`) 친구는 같은 초대를 `PENDING`으로 되살린다. 보낸 횟수는 이어서 센다
- **부수 효과**: 초대받은 사람에게 인앱 알림(`RUNNING_INVITED`)을 쌓고 푸시를 보내고, 방 참가자에게 `INVITE_ROOM_UPDATED`를 보낸다 — 초대 현황이 `RoomInfo.invitations`에 실린다
- **Response `204 No Content`** — 본문이 없다. 초대 현황은 스트림의 `INVITE_ROOM_UPDATED`가 나른다
- **에러 (404 Not Found)**: 방이 없거나, 초대 방이 아니거나, 호출자가 이 방의 현재 참가자가 아니다
- **에러 (409 Conflict)** — 판정 순서대로

| 코드 | 메시지 | 경우 |
|---|---|---|
| `RUNNING_INVITATION_CLOSED` | 초대를 받을 수 없는 방입니다. | 모집 중인 방이 아니거나 모집 마감이 지났다 |
| `NOT_FRIEND` | 친구가 아닙니다. | 친구가 아니다(자기 자신 포함) |
| `INVITEE_ALREADY_IN_ROOM` | 이미 방에 들어와 있는 친구입니다. | 친구가 이미 이 방의 참가자다 |
| `INVITEE_IN_MATCHING` | 지금 매칭 중인 친구입니다. | 친구가 다른 매칭 방·초대 방에서 대기 중이다(모집 중·확정 후 시작 전) |
| `INVITEE_RUNNING` | 지금 러닝 중인 친구입니다. | 친구가 러닝 중이다 — 시작된 방에 있거나, 솔로 방을 열었다(솔로는 모집 단계가 없어 시작 전이어도 러닝으로 본다) |
| `INVITE_COUNT_EXCEEDED` | 더 이상 이 친구에게 초대를 보낼 수 없습니다. | 호출자가 이 친구에게 보낸 횟수가 상한(운영 설정 `invite.max-invite-count`, 현재 5)에 닿았다 |

- **친구의 지금 상태(매칭 중·러닝 중)를 초대한 사람에게 알려준다** — 친구 사이라 드러내도 된다고 본다
- **인증**: 필요

### 4-3. `POST /api/v1/match-rooms/{runningRoomId}/invitations/accept` — 초대 수락

- **화면**: 알림함 (초대 알림의 수락 버튼)
- **Request 본문이 없다** — 받는 사람은 토큰 주체다. 경로의 방에서 나에게 온 초대를 수락한다. 누가 보냈든 초대는 받는 사람 기준으로 하나라 보낸 사람을 고르지 않는다
- **동작**: 방을 잠그고 다시 확인한 뒤 매칭 참가와 같게 `running_players(JOINED)`·배정 세션을 만들고, 인원·방 평균 페이스를 갱신하고, 나에게 온 초대 행을 모두 `ACCEPTED`로 바꾼다. 기존 방 참가자에게 `INVITE_ROOM_UPDATED`를 보낸다. 수락한 사람은 응답을 받은 뒤 스트림을 열어 `INVITE_ROOM_ENTRANCE`를 받는다. 수락한 사람을 초대했고 방에 남은 사람에게 초대 수락 푸시(`INVITATION_ACCEPTED`)를 보낸다
  - **먼저 수락한 순서로 자리가 찬다** — 방 행 잠금이 순서를 정한다
  - 매칭 쿨다운 중에도 수락할 수 있다
- 응답을 받으면 클라는 `GET /match-rooms/stream`을 연다
- **Response `201 Created`**

```json
{
  "runningRoomId": 130
}
```

- **에러 (404 Not Found)**: 방이 없거나, 이 방에서 나에게 온 초대가 없다
- **에러 (409 Conflict)** — 판정 순서대로
  - `RUNNING_INVITATION_CLOSED` — 만료됐거나(모집 마감·방 취소) 방이 모집 중이 아니다
  - `RUNNING_INVITATION_NOT_PENDING` — 이미 수락했거나 거절한 초대다. 거절 뒤 다시 들어가려면 다시 초대받아야 한다
  - `RUNNING_ROOM_FULL` — 자리가 다 찼다. **초대는 `PENDING`으로 남는다** — 누가 나가면 다시 수락할 수 있다
  - `RUNNING_ALREADY_IN_PROGRESS` — 진행 중인 러닝이나 활성 신청이 있다
  - `ONBOARDING_NOT_COMPLETED` — 온보딩 전이라 쓸 페이스가 없다

```json
{
  "code": "RUNNING_INVITATION_NOT_PENDING",
  "message": "이미 응답한 초대입니다."
}

{
  "code": "RUNNING_ROOM_FULL",
  "message": "방이 가득 찼습니다."
}
```

- **인증**: 필요

### 4-4. `POST /api/v1/match-rooms/{runningRoomId}/invitations/decline` — 초대 거절

- **화면**: 알림함 (초대 알림의 거절 버튼)
- **Request 본문이 없다** — 받는 사람은 토큰 주체다
- 이 방에서 나에게 온 초대 행을 모두 `DECLINED`로 바꾸고 방 참가자에게 `INVITE_ROOM_UPDATED`를 보낸다 — 거절한 사람은 현황(`RoomInfo.invitations`)에서 빠진다. **행은 지우지 않는다**(친구 요청 거절과 다르다) — 다시 초대할 때 보낸 횟수를 이어서 센다
- **Response `204 No Content`**
- **에러 (404 Not Found)**: 방이 없거나, 이 방에서 나에게 온 초대가 없다
- **에러 (409 Conflict)**: `RUNNING_INVITATION_NOT_PENDING` — 대기중인 초대가 아니다(이미 응답했거나 만료됐다)
- **인증**: 필요

## 5. 매칭·러닝 실시간 통신

**구간마다 통신 방식이 다르다.**

| 구간 | 방식 | 이유 |
|---|---|---|
| 매칭 신청 ~ 대기방 (5-A·5-B) | **REST + SSE** `/api/v1/match-rooms/stream` | 클라가 보내는 건 신청·취소 둘뿐이고 나머지는 전부 서버 푸시다 — 양방향 채널을 쓸 이유가 없다 |
| 러닝 구간 (5-C·5-D) | **WebSocket** `/api/v1/ws/running` | 위치를 주기 발신하는 고빈도 양방향 구간 |

**솔로 러닝도 같은 WebSocket을 쓴다.** 매칭을 거치지 않을 뿐 좌표 수집·저장 경로는 동일하다. 시작할 때 `POST /running-rooms/solo`로 방을 만들어 `runningRoomId`를 받은 뒤 WS에 연결한다(5-C의 카운트다운은 건너뛴다 — 맞출 상대가 없다).

### `POST /api/v1/running-rooms/solo` — 솔로 러닝 개시

- **클라가 만드는 방은 솔로(이 API)와 초대 방(4-1 `POST /match-rooms/invite`)이다.** 매칭 방은 신청 시 서버가 만들므로 요청 대상이 아니다. 방 종류는 `type`을 본문으로 받지 않고 경로(`/solo`·`/invite`)로 가른다
- **Request 본문이 없다.** 목표 거리도 받지 않는다 — 솔로는 사용자가 끝내야 끝나므로 방의 `target_distance`는 null이다. 페이스는 온보딩 값을 쓴다
- **Response `201 Created`**

```json
{
  "runningRoomId": 126
}
```

- **동작**: `running_rooms` 행을 `type='SOLO'`, `status='MATCHED'`, `max_player_count=1`, `current_player_count=1`로 만들고 본인 `running_players(status='JOINED')`와 배정 세션을 함께 만든다
  - **`STARTED`·`RUNNING`은 이 API가 만들지 않는다.** 모집을 건너뛴 확정 상태까지만 만들고, 시작 전이는 WS `RUNNING_START`가 일으킨다(5-C). 솔로에는 시작 예약을 걸지 않는다 — `start_at`이 개시 시각이라 `RUNNING_START`가 도착하는 순간 이미 지나 있다
  - **강제 종료 예약도 걸지 않는다.** 앱이 방 번호를 잃었을 때의 복구는 `GET /users/me/status`의 `runningRoomId`로 한다 — `READY`면 이어 뛸 때 `RUNNING_START`, 그만둘 때 `POST /match-rooms/leave`이고, `RUNNING`이면 `RUNNING_START`로 다시 붙어 `RUNNING_FINISH`로 끝낸다
- 이 방은 `GET /match-rooms/slots`의 대기 인원 집계에 포함되지 않는다(`type='SOLO'`로 제외). 모집 중인 자리가 아니다
- **에러 (409 Conflict)**
  - `RUNNING_ALREADY_IN_PROGRESS` — 진행 중인 러닝이나 활성 매칭 신청이 있다
  - `ONBOARDING_NOT_COMPLETED` — 온보딩 전이라 쓸 페이스가 없다
- **인증**: 필요

**솔로는 SSE를 사용하지 않는다.** POST 응답으로 `MATCHED` 방 ID를 받은 뒤 WS에 연결해 `RUNNING_START`를 보내고 `RUNNING_STARTED` ack를 받는다 — 카운트다운만 건너뛸 뿐 매칭과 같은 순서이며, 보내는 메시지도 똑같다.

- **DB row 트리거** — `running_room_sessions`가 신청과 방을 잇는다(신청 즉시 방이 생기므로 배정 row도 항상 있다). 현재 속한 방은 `is_connected=true`인 행이다
  - row 생성 = 매칭 신청·솔로 개시 시. 새 방을 만들거나 기존 모집 중인 방에 배정된다
  - 취소·나가기 요청 시 서버가 모집 마감 시각으로 분기한다(5-A 참고). 어느 쪽이든 배정 행은 `is_connected=false`로 남아 이력이 된다
  - 참가자가 모두 빠져 `current_player_count`가 `0`이 되면 방을 `CANCELLED`로 닫는다. **시작 후에는 접속 전(`JOINED`) 참가자의 취소·탈퇴만 인원을 줄이며**, `RUNNING` 참가자가 모두 종료되면 유효 기록이 하나라도 저장됐을 때 `FINISHED`, 없으면 `CANCELLED`다. 각 참가자 row는 유지하되 취소·이탈 시각을 `deleted_at`에 기록하고 배정 행은 `is_connected=false`로 남긴다

### 5-A. 매칭 중 (홈 → 매칭 대기 화면)

#### `GET /api/v1/match-rooms/stream` — 방 상태 이벤트 스트림 (SSE)

- **인증**: 필요 / **Content-Type**: `text/event-stream`
- **매칭 방·초대 방이 같은 스트림을 쓴다** — 활성 신청이 사람당 하나라 스트림도 하나면 된다. 방 종류는 이벤트 이름(`MATCH_*`·`INVITE_ROOM_*`)과 `RoomInfo.type`이 가른다
  - 예전 경로 `/running-matches/stream`은 없앴다 — 남겨 두지 않는다
- **연결 시점**: 매칭 신청·초대 방 개설(4-1)·초대 수락(4-3) 성공 직후. 활성 신청이 없으면 연결하지 않는다 — 서버가 보낼 것이 없다.
  - **어기면 서버가 `404 Not Found`로 거절한다(본문 없음).** 열어주면 붙을 방이 없어 아무 채널도 구독하지 못하고, 그 뒤에 신청해도 이벤트가 영영 오지 않는 연결이 된다 — 에러도 없이 조용히 망가지므로 연결 자체를 막는다.
  - **이 응답에만 본문이 없다.** 요청 `Accept`가 `text/event-stream`이라 다른 에러처럼 JSON 본문을 실을 수 없다. 클라는 상태 코드로 판단하고, 신청 응답을 받기 전에는 스트림을 열지 않는다.
  - 앱 재시작·포그라운드 복귀 시엔 스트림을 다시 열고, **연결 직후 내려오는 `RoomInfo` 스냅샷으로 상태를 복원한다.** 스냅샷 이벤트는 매칭 방이 `MATCH_ROOM_UPDATED`, 초대 방이 `INVITE_ROOM_ENTRANCE`다.
- **종료 시점**: `RUNNING_STARTED` ack 뒤, 또는 매칭 취소(`POST /match-rooms/leave`)의 204를 받은 뒤 클라이언트가 닫는다.
- **서버도 스트림을 닫는다** — 열린 지 운영값(현재 30분)이 지나면, 같은 사용자의 새 연결이 들어오면, 탈퇴하면 닫는다(새 연결·탈퇴는 그 요청을 처리한 서버 인스턴스에 붙은 스트림만 닫는다 — 다른 인스턴스의 옛 스트림은 30분 만료나 전송 실패로 정리된다). 활성 신청이 남아 있으면 클라는 다시 연결하고 연결 직후 스냅샷으로 상태를 복원한다.
- **연결을 화면 생명주기에 묶지 않는다.** 홈을 벗어나도 스트림은 살아 있어야 한다(근거는 `feature-spec.md` 매칭·러닝 설계 절)
- **이벤트 형식** — 타입은 SSE `event` 필드로, 본문은 `data`에 JSON으로 싣는다

```
event: MATCH_ROOM_UPDATED
data: {"runningRoomId":125,"status":"MATCHED", ...}
```

| 이벤트 | 시점 |
|---|---|
| `MATCH_STARTED` | 매칭 확정 — `data` = `RoomInfo`. 매칭 방만 |
| `MATCH_ROOM_UPDATED` | 인원 변동·연결 직후 스냅샷 — `data` = `RoomInfo`. 러닝 시작은 이 이벤트로 알리지 않는다. 매칭 방만 |
| `INVITE_ROOM_ENTRANCE` | 연결 직후 스냅샷 — `data` = `RoomInfo` + `startsInMs`. 초대 방만 |
| `INVITE_ROOM_UPDATED` | 인원 변동·초대 현황 변동·모집 마감(초대 만료) — `data` = `RoomInfo`. 초대 방만 |
| `RUNNING_READY` | 곧 시작 통지 — `start_at` 직전에 한 번 (5-C) |

- 연결 직후 서버가 현재 상태를 보낸다. 각 이벤트는 변경분이 아니라 해당 객체의 전체 상태를 담으므로 `Last-Event-ID` 재개는 사용하지 않는다.
- **keep-alive**: 주기적으로 주석 라인(`:ping`)을 보내 프록시 유휴 타임아웃을 막는다. 주기는 운영값.
- 스트림은 수신 전용이라 요청 실패라는 개념이 없다 — 오류는 신청·취소 REST 응답으로 전달된다.

#### `GET /api/v1/match-rooms/slots` — 시간대별 대기 인원

- **화면**: 매칭 정보 입력 모달 — 시간 선택 박스에 "19:00 · 3명 대기 중"처럼 표시한다
- **Query**: `date`(YYYY-MM-DD, 생략 시 오늘 — 오늘 이전 날짜는 `400`). 거리는 받지 않는다 — 고를 수 있는 모든 거리를 한 번에 내려 주므로, 모달에서 거리를 바꿔도 다시 요청하지 않는다
- **Response `200 OK`**

```json
{
  "distances": [
    {
      "targetDistanceMeters": 3000,
      "slots": [
        { "scheduledStartAt": "2026-07-25T18:00:00", "waitingCount": 0, "selectable": false },
        { "scheduledStartAt": "2026-07-25T18:30:00", "waitingCount": 2, "selectable": true },
        { "scheduledStartAt": "2026-07-25T19:00:00", "waitingCount": 0, "selectable": true }
      ]
    },
    {
      "targetDistanceMeters": 5000,
      "slots": [
        { "scheduledStartAt": "2026-07-25T18:00:00", "waitingCount": 0, "selectable": false },
        { "scheduledStartAt": "2026-07-25T18:30:00", "waitingCount": 3, "selectable": true },
        { "scheduledStartAt": "2026-07-25T19:00:00", "waitingCount": 1, "selectable": true }
      ]
    }
  ]
}
```

- `distances`는 신청 가능한 거리(현재 운영값 3000·5000·10000) 전부를 **`targetDistanceMeters` 오름차순**으로 담는다. 대기자가 하나도 없는 거리도 빠지지 않는다. 거리가 다르면 같은 방에 모이지 않으므로 거리끼리 인원을 합산하지 않는다
- 각 거리의 `slots`는 `date` 하루의 신청 가능 시각(현재 운영값 18:00~22:00, 30분 간격) 9개를 **`scheduledStartAt` 오름차순**으로 담는다. 대기자가 없는 슬롯도 `waitingCount: 0`으로 빠짐없이 내려준다. 시각 범위·간격·거리 목록은 운영 설정값(`match.allowed-*`)이라, 매칭 신청이 받는 선택지와 이 목록은 항상 같다
- 응답에 조회 날짜를 따로 싣지 않는다 — `scheduledStartAt`이 날짜를 담는다
- `waitingCount`는 아직 확정되지 않은 대기자 수다 — `type='MATCH' AND status='MATCHING'`인 방의 참가자만 센다. 이미 `MATCHED`된 방(들어갈 수 없는 자리)과 솔로 방(`type='SOLO'`)은 제외. 같은 슬롯에 방이 여럿이면 인원을 합산한다
- `selectable=false`는 마감이 지난 슬롯 — 목록에는 남기되 선택은 막는다. 경계는 매칭 신청의 `409 MATCH_SLOT_CLOSED`와 같다(마감 정각도 마감). 마감은 시각으로만 갈리므로 같은 시각이면 거리가 달라도 값이 같다
- **인증**: 필요
- **에러 (400 Bad Request)**: 날짜가 형식에 맞지 않거나 오늘 이전이면 요청 검증 단계에서 걸린다 — `code`는 `INVALID_REQUEST`다

```json
{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "조회 날짜는 오늘 이전일 수 없습니다."
}
```

- 형식 오류(`date=2026/07/25`)는 공통 문구 하나로 나가고, 날짜 형식이 틀리면 "오늘 이전" 문구는 함께 나가지 않는다

#### `POST /api/v1/match-rooms` — 매칭 신청

- **화면**: 홈 (매칭 버튼)
- **Request**

```json
{
  "scheduledStartAt": "2026-07-25T19:00:00",
  "targetDistanceMeters": 5000
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `scheduledStartAt` | String | O | KST 시작 시각. **18:00~22:00**, **30분 간격**(`18:00`, `18:30`, … `22:00`) |
| `targetDistanceMeters` | Integer | O | 목표 거리(m). **3000 / 5000 / 10000** 셋 중 하나 |

- **입력값은 정해진 선택지 안에서만 받는다** — 자유 입력이 아니다. 위 시각 범위·간격·거리 목록은 운영 설정값(`match.allowed-*`)이며, 설정이 바뀌면 선택지와 400 문구가 함께 바뀐다. 시각 선택지는 하드코딩하지 말고 `GET /match-rooms/slots`가 내려 주는 목록을 따른다. 거리 목록은 그 응답에 없다
- **활성 신청은 1개** — 이미 있으면 `409 MATCH_ALREADY_IN_PROGRESS`. 확정된 신청도 활성이므로 재신청은 막힌다. 혼자 확정된 경우에는 페널티 없이 나갈 수 있고(5-B) 나가면 곧바로 다시 신청할 수 있다. 이 API로 만드는 방은 전부 공개 랜덤 매칭이라 공개 범위를 받지 않는다
- 페이스 조건은 입력받지 않음 — 서버가 보관한 사용자 평균 페이스 자동 사용. 그 값이 없으면(온보딩 미완료) `409 ONBOARDING_NOT_COMPLETED`
- **모집 인원도 입력받지 않음** — 서버가 최대 4명까지 모으고, 마감 때 1명이면 1인으로 확정한다 (`desiredPlayerCount` 필드 없음)
- **Response `201 Created`** — 신청이 접수되면 `running_players` row와 `running_room_sessions` 배정 row가 생긴다(예전에 거쳐 간 방에 다시 배정되면 그 배정 row를 되살린다). 같은 조건에 모집 중인 방이 있으면 거기 배정되고, 없으면 **1인 방**(`running_rooms`, `type='MATCH'`, `status='MATCHING'`, `max_player_count=4`, `current_player_count=1`)이 새로 생긴다

```json
{
  "runningRoomId": 125
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `runningRoomId` | Integer | O | 배정된 방 ID. 이 값으로 SSE 스트림에 연결한다 |

- **방 ID만 돌려준다.** 방 정보·참가자 목록·모집 마감 시각(`closeAt`)은 **연결 직후 스트림이 `RoomInfo` 스냅샷으로 나른다** — 응답과 스트림이 같은 값을 두 벌로 내리지 않는다. 솔로 개시(`POST /running-rooms/solo`)와 응답 모양이 같다
- **응답을 받은 뒤 SSE 스트림에 연결한다**
- **에러 (400 Bad Request)**: 값이 없거나 허용 범위 밖이다. 값이 없으면 요청 검증 단계에서, 선택지 밖이면 유스케이스가 다른 검사보다 먼저 걸러 낸다 — 어느 쪽이든 `code`는 `INVALID_REQUEST`이고, 이미 지난 시각이라도 선택지 밖이면 `409 MATCH_SLOT_CLOSED`가 아니라 400이다

```json
{
  "code": "INVALID_REQUEST",
  "message": "희망 시작 시각은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "목표 거리는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "시작 시각은 18:00부터 22:00까지 30분 간격으로만 선택할 수 있습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "목표 거리는 3000, 5000, 10000 중 하나여야 합니다."
}
```

- 두 조건이 동시에 어긋나면 `message`에 시각 → 거리 순으로 두 문장이 공백으로 이어붙어 한 번에 나간다. 단, 한쪽 값이 비어 있으면 필수 문구만 먼저 나가고 다른 쪽의 선택지 위반은 값을 채워 다시 보냈을 때 나간다

- **에러 (409 Conflict)** — 아래 순서로 검사하며, 먼저 걸린 것 하나만 응답한다
- **`MATCH_SLOT_CLOSED`** — 모집 마감(`start_at - 오프셋`)이 지난 슬롯이다

```json
{
  "code": "MATCH_SLOT_CLOSED",
  "message": "이미 모집이 마감된 시간대입니다."
}
```

- 마감 시각 **정각도 마감으로 본다** — 그 시점에 확정 판정이 돌기 때문이다
- 클라는 `GET /match-rooms/slots`의 `selectable`로 1차 차단한다. 이 에러는 **모달을 열어둔 사이 마감이 지나가는 경합에서만** 나오므로, 받으면 슬롯 목록을 다시 받는다
  - **`slots`로 1차 차단하지 않는 동안에는** 경합이 아니라 마감된 슬롯을 그냥 고른 경우에도 이 에러가 나온다. 슬롯 목록을 다시 받을 곳이 없으므로 해당 슬롯만 목록에서 지우고 다시 고르게 한다

- **`MATCH_COOLDOWN`** — 제재 대상 이탈로 신청이 제한된 상태다. **이 에러만 `cooldownUntil`(신청 제한 해제 시각)을 더 담는다**(api-convention: 오류별 추가 필드 허용). 해제 시각은 Redis 키의 남은 TTL로 계산하며, 근거가 되는 이탈 자체는 `running_players.status`에 남는다

```json
{
  "code": "MATCH_COOLDOWN",
  "message": "매칭 또는 러닝 중 이탈해 일정 시간 신청이 제한됩니다.",
  "cooldownUntil": "2026-07-26T07:30:00"
}
```

- 솔로 러닝(`POST /running-rooms/solo`)은 이 제한을 받지 않는다

- **`MATCH_ALREADY_IN_PROGRESS`** — 이미 활성 신청이나 확정된 방이 있다

```json
{
  "code": "MATCH_ALREADY_IN_PROGRESS",
  "message": "이미 진행 중인 매칭이 있습니다."
}
```

- 대기 중·확정된 방뿐 아니라 **러닝 중에도** 여기 걸린다 — 활성 판정이 `deleted_at`만 보기 때문이다

- **`ONBOARDING_NOT_COMPLETED`** — 매칭 조건에 쓸 온보딩 평균 페이스가 없다

```json
{
  "code": "ONBOARDING_NOT_COMPLETED",
  "message": "온보딩을 먼저 완료해 주세요."
}
```

- 페이스를 입력받지 않고 온보딩 값을 쓰기 때문에, 값이 없으면 매칭 조건을 만들 수 없어 신청을 받지 않는다
- **인증**: 필요

#### `POST /api/v1/match-rooms/leave` — 매칭 취소·방 나가기 (겸용)

- **토큰 주체의 활성 신청 하나를 끝낸다** — 신청은 유저당 하나뿐이라 경로에 식별자를 두지 않는다. `DELETE /match-rooms`가 아니라 동작 경로(`/leave`)인 이유는 **방을 지우는 게 아니라 내가 빠지는 것**이라서다 — 혼자 나가도 방은 남은 사람과 함께 그대로 있다
- 예전 경로 `DELETE /running-matches`는 없앴다 — 남겨 두지 않는다
- **솔로 방의 시작 전 신청도 이 경로로 닫는다** — 솔로를 열고 WS에 붙기 전에 그만둘 때 쓴다. 방은 `CANCELLED`, 신청은 `MATCHED_LEFT_NO_PENALTY`이며 제재는 없다. 이미 뛰는 중(`RUNNING`)이면 아래와 같이 409다
- **서버가 모집 마감 시각으로 분기**
  - 대기 중(마감 전) = 대기 취소(`status=MATCHED_LEFT_NO_PENALTY` + `deleted_at` 기록). **본인이 마지막 참가자였으면 방도 `CANCELLED`**. 제재 없음
  - **분기는 방의 `status`가 아니라 모집 마감 시각으로 한다** — 마감이 지났는데 스케줄러가 아직 `MATCHING`을 안 닫은 틈에 나가면 제재를 피할 수 있기 때문이다
  - **러닝이 시작된 뒤(`status='RUNNING'`)에는 이 API를 쓸 수 없다** — `409 MATCH_ALREADY_STARTED`. 종료는 WS `RUNNING_FINISH`가 맡으며, 여기서 끊으면 GPS 트랙과 기록이 저장되지 않는다
  - 확정 후(`MATCHED`) = 이탈(`status=MATCHED_LEFT_PENALTY` 또는 `MATCHED_LEFT_NO_PENALTY`, `deleted_at` 기록). 제재 대상 여부는 **이 시점에 모집 마감(`start_at - 오프셋`)과 `current_player_count`로 판정해 값에 굳힌다** — 혼자 남은 방(`1`)에서 나가면 마감이 지났어도 면제다. **초대 방(`type='INVITE'`)은 언제나 `MATCHED_LEFT_NO_PENALTY`다.** 쿨다운이 걸리는 경우에만 클라는 나가기 전에 그 사실을 안내한다
  - 남은 인원에게는 `MATCH_ROOM_UPDATED`(초대 방은 `INVITE_ROOM_UPDATED`)를 스트림으로 발신한다. **혼자 남아도 방은 취소하지 않는다**
- **시각으로 취소를 차단하지 않는다.** 시작 직전까지 호출할 수 있고 늦은 이탈은 쿨다운으로 다룬다
- **Response `204 No Content`** — 이후 클라는 SSE 스트림을 닫는다
- **에러 (404 Not Found)**: 활성 신청이 없다

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (409 Conflict)**: 본인이 이미 러닝을 시작했다(참가자 `RUNNING`)

```json
{
  "code": "MATCH_ALREADY_STARTED",
  "message": "이미 시작된 러닝은 취소할 수 없습니다."
}
```

- **인증**: 필요

#### 매칭 상태 복원 — `GET /api/v1/users/me/status`

매칭 상태만 따로 묻는 엔드포인트는 두지 않는다. 앱 진입·포그라운드 복귀 시 **유저 상태 API(2-2)**가 `IDLE`·`WAITING`·`READY`를 돌려주고, 클라이언트는 그 값으로 스트림 연결 여부를 정한다.

- **매칭 스트림은 활성 신청이 있을 때만 연다** — 방 단위 이벤트라 붙을 방이 없으면 보낼 것이 없다. 앱이 켜져 있는 동안의 개인 채널은 앱 스트림(2-3 아래)이 맡는다 — 앱을 켠 사용자마다 연결 하나를 유지하는 비용을 받아들이고 실시간 알림을 이 채널로 모았다. 상태는 지금처럼 2-2로 묻는다
- **방 정보는 스트림이 나른다** — 2-2는 `runningRoomId`까지만 주고, 참가자 목록·마감 시각은 연결 직후의 `RoomInfo` 스냅샷이 채운다. 신청 직후에는 `POST /match-rooms`가 준 `runningRoomId`로 바로 스트림을 연다
- **매칭이 저절로 실패하는 상태는 없다** — 마감은 인원과 무관하게 항상 확정으로 끝나므로 `FAILED`에 해당하는 값이 없다. 취소는 사용자가 직접 한 행동이라 그 즉시 `IDLE`로 돌아간다
- **1인 확정도 `READY`다.** 클라이언트는 `RoomInfo.players`가 1건인 경우를 혼자 확정된 상태로 그리며, 이때 나가기는 페널티가 없다(5-B)

### 5-B. 매칭 방 (매칭완료 대기방)

#### 공통 객체 `RoomInfo` — 매칭방 전체 정보

방 상태 SSE 이벤트가 아래 **같은 구조를 싣는다** — 매칭 방의 `MATCH_STARTED`·`MATCH_ROOM_UPDATED`, 초대 방(4번)의 `INVITE_ROOM_ENTRANCE`·`INVITE_ROOM_UPDATED`. **`INVITE_ROOM_ENTRANCE`만 `startsInMs`를 하나 더 싣는다**(아래 해당 절).

```json
{
  "runningRoomId": 125,
  "type": "MATCH",                   // MATCH | INVITE
  "status": "MATCHED",               // running_rooms.status: MATCHING|MATCHED|STARTED|FINISHED|CANCELLED — CANCELLED는 현재 내려오지 않는다(받으면 홈으로)
  "scheduledStartAt": "2026-07-25T19:00:00",
  "closeAt": "2026-07-25T18:50:00",  // 모집 마감 시각 — 매칭 방은 start_at - 마감 오프셋(현재 10분), 초대 방은 start_at - 초대 마감 오프셋(현재 1분) 계산값
  "targetDistanceMeters": 5000,
  "teamAveragePaceSecondsPerKm": 375,
  "players": [
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440015",
      "nickname": "동완러너",
      "profileImageUrl": "https://...",
      "introduction": "즐겁게 같이 달려요!",   // users.introduction
      "averagePaceSecondsPerKm": 360,
      "isDeleted": false                     // 탈퇴 유저 표시 — §0 공통 규칙
    },
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440013",
      "nickname": "철수",
      "profileImageUrl": "https://...",
      "introduction": "천천히 오래 달려요.",
      "averagePaceSecondsPerKm": 390,
      "isDeleted": false
    }
  ],
  "invitations": []                  // 초대 방의 초대 현황 — 매칭 방은 항상 빈 배열
}
```

- **`invitations`** — 초대 방에서 **아직 응답하지 않은(`PENDING`) 초대만** 담는다

```json
"invitations": [
  {
    "userId": "01a0f2d4-6180-7000-8000-446655440021",
    "nickname": "영희",
    "profileImageUrl": "https://...",
    "status": "PENDING",             // 항상 PENDING — 응답 전인 초대만 담는다
    "isDeleted": false
  }
]
```

  - `PENDING`(대기중)만 나간다. 수락하면 `players`로 옮겨 가고(`ACCEPTED`는 지금 방에 있다는 뜻이다), 거절(`DECLINED`)·나감(`LEFT`)은 현황에서 빠지며, 만료(`EXPIRED`)는 마감 뒤라 현황 자체를 그리지 않는다. 거절·나감한 친구도 다시 초대하면 `PENDING`으로 돌아온다
  - `status` 필드는 응답 모양을 유지하려고 남긴다 — 지금은 늘 `PENDING`이다
  - 상태가 바뀔 때마다(초대·수락·거절) `INVITE_ROOM_UPDATED`가 `RoomInfo` 전체를 다시 보낸다 — 클라는 받은 대로 다시 그린다

- **참가자별 `status`는 내려보내지 않는다** — 이탈자는 배정 행이 `is_connected=false`가 되어 목록에서 빠지므로 남아 있는 참가자는 아직 끝나지 않은 참가자(`JOINED`·`RUNNING`)다. 방의 진행 단계는 위 `status`가 나른다
- **탈퇴한 참가자는 목록에서 빠진다** — 시작 전 탈퇴는 일반 취소처럼 방에서 빠지며 인원도 줄고, 시작 후 탈퇴는 배정이 끊겨 목록에서 빠진다(접속 전이었으면 인원도 준다). `isDeleted`는 §0 공통 형식이라 계약에 남지만 이 목록에서는 현재 항상 `false`다

#### `MATCH_STARTED` (SSE) — 매칭 성사 통지

- `data` = `RoomInfo`. 수신 시 클라는 대기 화면 → 매칭방 화면으로 전환
- **발화 시점은 모집 마감(`start_at - 오프셋`)이다** — 방이 `MATCHING`→`MATCHED`로 넘어가는 순간 한 번. **방 생성 시점이 아니다** — 방은 신청 즉시 생기지만 그건 모집 시작이고, 그 구간의 인원 변동은 `MATCH_ROOM_UPDATED`가 담당한다. 자리가 다 차도 앞당겨 쏘지 않는다(`feature-spec.md` 확정 판정)

#### `MATCH_ROOM_UPDATED` (SSE) — 매칭방 정보 갱신

- `data` = `RoomInfo` 전체 재전송. **인원 변동과 연결 직후 스냅샷이 이 이벤트로 나간다** — 모집 중 참가·이탈뿐 아니라 확정 후 이탈, 시작 후 미접속자(`JOINED`)의 취소·탈퇴로 인원이 줄 때도 나간다
- **`STARTED` 전환은 이 이벤트로 알리지 않는다** — 시작 통지는 `RUNNING_READY`가 맡고(5-C), 방의 `STARTED` 전환은 `start_at` 정각의 시작 스케줄러가 일으킨다. 다만 그 뒤에 붙은 스냅샷에는 `status: "STARTED"`가 실려 온다
- 클라는 **받으면 무조건 `RoomInfo`로 화면을 다시 그린다.** 무슨 일이 있었는지는 `status`·`players`·`invitations`가 말해주므로 이벤트를 더 쪼개지 않는다

| `status` | 클라가 할 일 |
|---|---|
| `MATCHING` | 대기 화면 — 인원·마감 시각 갱신 |
| `MATCHED` | 대기방 |
| `STARTED` | 이미 시작된 방이다(재연결 스냅샷 등) — 러닝 화면으로 이어가고 WS `RUNNING_START`를 보낸다 |
| `CANCELLED` | 홈으로 — 현재 서버는 보내지 않는다(아래) |

- **`MATCH_STARTED`와 나뉘는 이유는 "전이"와 "상태"의 차이다.** `status=MATCHED`는 재연결 스냅샷으로도 오므로, 그것만 보면 확정 연출을 볼 때마다 반복하게 된다. 확정된 그 순간은 `MATCH_STARTED`가, 그 밖의 모든 갱신은 이 이벤트가 맡는다
- 방 취소는 마지막 참가자가 나갈 때만 일어나 받을 사람이 없으므로 이벤트를 보내지 않는다. `CANCELLED`는 방어적으로만 처리한다

#### `INVITE_ROOM_ENTRANCE` (SSE) — 초대 방 입장 스냅샷

- **초대 방에서 스트림을 연 직후 한 번** 보낸다 — 방을 만든 직후(4-1), 초대를 수락한 직후(4-3), 재연결할 때. 매칭 방의 연결 직후 `MATCH_ROOM_UPDATED`에 해당한다
- `data` = `RoomInfo` 전체(참가자 `players`·초대 현황 `invitations` 포함) + `startsInMs`

```json
{
  "runningRoomId": 130,
  "type": "INVITE",
  "status": "MATCHING",
  "scheduledStartAt": "2026-07-25T07:30:00",
  "closeAt": "2026-07-25T07:29:00",
  "targetDistanceMeters": 7000,
  "teamAveragePaceSecondsPerKm": 375,
  "players": [ ... ],
  "invitations": [ ... ],
  "startsInMs": 1185000
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `startsInMs` | Long | O | **이 이벤트를 보낸 시점 기준** 시작까지 남은 밀리초. 이미 지났으면 `0` |

- **`startsInMs`는 남은 시간 표시용이다** — 클라는 기기 시각 대신 이 값으로 대기 화면의 남은 시간을 그린다. 발사 타이머는 여전히 `RUNNING_READY`의 `startsInMs`로 건다(5-C)
- 수신 시 `status`별 처리는 `MATCH_ROOM_UPDATED` 표와 같다

#### `INVITE_ROOM_UPDATED` (SSE) — 초대 방 정보 갱신

- `data` = `RoomInfo` 전체 재전송(`startsInMs` 없음). 초대 보내기(4-2)·수락(4-3)·거절(4-4)·나가기(5-A)·모집 마감(남은 초대 만료로 `invitations`가 빈다) 때 방 참가자에게 보낸다
- 클라는 받으면 무조건 `RoomInfo`로 화면을 다시 그린다 — `MATCH_ROOM_UPDATED`와 같다
- **모집 마감 통지를 따로 두지 않는다** — 마감(시작 1분 전)은 이 갱신(`status: "MATCHED"`, 빈 `invitations`)으로만 알리고, 확정 연출 없이 대기 화면을 유지하다 `RUNNING_READY`(시작 10초 전)로 카운트다운에 들어간다

#### 방 나가기 — 별도 이벤트 없음

- 확정된 방에서 나가기도 **`POST /match-rooms/leave`** 사용 (5-A 참고 — 서버가 모집 마감 시각으로 분기)
- 나간 사람만 `MATCHED_LEFT_*` 처리, 방은 유지되고 그대로 러닝을 진행한다
- **확정 후 이탈에는 페널티가 붙는다** — 모집 마감(`start_at - 오프셋`) 이후에 나가면 일정 시간 매칭 신청이 제한된다(`409 MATCH_COOLDOWN`). 만료는 Redis 키의 TTL이 판정한다(`feature-spec.md` 쿨다운 저장 절)
- **혼자 남은 방은 예외다** — 이탈 시점 `current_player_count`가 `1`이면 면제(`MATCHED_LEFT_NO_PENALTY`)다. 1인으로 확정된 방과 이탈로 혼자 남은 방 모두 해당하며, 나가면 활성 신청이 끝나 곧바로 재신청할 수 있다

#### 대기방 참가자 목록 — 별도 조회 없음

- `RoomInfo`가 참가자 전체를 담고 있고 변동 시마다 재전송되므로, 목록만 따로 받는 요청은 두지 않는다
- 앱 재시작 등으로 스트림이 끊겼다면 다시 연결할 때 오는 스냅샷이 같은 정보를 돌려준다

### 5-C. 러닝 카운트 다운 — SSE에서 WebSocket으로

**출발 순간은 서버가 준 기준으로 맞추고, 3-2-1 표시는 클라가 한다.** 발화 신호를 정각에 보내면 네트워크 지연만큼 참가자마다 출발이 어긋나므로, 서버는 **미리** 알리고 클라가 타이머로 정각을 맞춘다.

절차는 다음과 같다.

1. `scheduledStartAt` 직전(리드타임은 운영값)에 클라가 WS를 연결한다. **`RUNNING_READY`를 기다리지 않는다** — `scheduledStartAt`은 이미 `RoomInfo`로 알고 있고, 연결이 늦으면 정각에 보낼 수 없다
2. 서버가 `scheduledStartAt - 리드타임`(운영값)에 `RUNNING_READY`를 보낸다. 클라는 `startsInMs`로 발사 타이머를 건다
3. 타이머에서 파생해 **시작 3초 전부터 3-2-1 카운트다운**(화면·음성·햅틱)을 표시하고 뒤로가기를 차단한다
4. `scheduledStartAt`에 타이머가 만료되면 러닝 화면으로 전환해 `RUNNING_START`를 보낸다. 방은 같은 시각의 시작 스케줄러가 이미 `STARTED`로 올려놨고, 참가자의 `RUNNING` 전환은 각자의 `RUNNING_START` 몫이다
5. `RUNNING_STARTED` ack를 받으면 SSE 스트림을 닫는다

**`RUNNING_READY`를 못 받아도 러닝은 시작한다** — SSE가 끊겼거나 앱이 백그라운드였으면 이벤트가 오지 않는다. 그때는 기기 시각으로 `scheduledStartAt`을 넘겼는지 보고 그냥 `RUNNING_START`를 보낸다. 서버가 방 상태로 판정하므로 틀린 요청이면 거절될 뿐이다.

#### `RUNNING_READY` (SSE) — 곧 시작 통지

- **발화 시점은 `scheduledStartAt - 리드타임`이다**(리드타임은 운영값). 방을 만들 때 모집 마감 예약과 함께 이 시각으로 예약을 걸고, 발화 시점에 방이 `MATCHED`가 아니면 보내지 않는다. **정각이 아니라 미리 보내는 것이 요점이다** — 정각에 보내면 클라의 발사 시점과 겹쳐 이벤트가 늘 늦게 도착한다
- 이 이벤트는 방 상태를 바꾸지 않는다. `STARTED` 전환은 `scheduledStartAt` 정각에 따로 걸린 시작 스케줄러가 일으킨다

```json
{
  "runningRoomId": 125,
  "scheduledStartAt": "2026-07-25T19:00:00",
  "startsInMs": 10000
}
```

- **`startsInMs`는 이 이벤트를 보낸 시점 기준으로 시작까지 남은 시간이다.** 클라는 이 값으로 타이머를 걸면 되고, 기기 시각을 믿지 않아도 된다
- **남은 시간이 짧으면 연출만 줄이고 발사 시각은 그대로 둔다.** 서버 재시작 복구나 늦은 재연결로 `startsInMs`가 카운트다운 길이보다 짧게 올 수 있다. 이때 `RUNNING_START`를 앞당겨 보내면 `scheduledStartAt` 이전이라 거절된다

| `startsInMs` | 3-2-1 연출 | `RUNNING_START` 발사 |
|---|---|---|
| 3,000 초과 | 전부 표시 | `startsInMs` 후 |
| 0 초과 ~ 3,000 이하 | 남은 만큼 축약하거나 생략 | **`startsInMs` 후 (앞당기지 않는다)** |
| 0 | 없음 | 즉시 |

- **`0`은 "시작 시각이 이미 지났다"는 서버의 신호다** — 서버가 음수를 0으로 눌러 보낸다. 이 값일 때만 즉시 발사가 안전하다
- **기기 시계가 어긋나 있어도 이 둘로 보정할 수 있다** — `scheduledStartAt - startsInMs`가 서버의 현재 시각이므로, 수신 시점의 로컬 시각과 비교하면 오차가 나온다. 보정한 시계로 발사 시각을 잡으면 로컬 타이머를 쓰면서도 기준은 서버에 맞는다
- **시각이 아니라 간격으로 내려보내는 이유**는 시각 포맷이 초 단위까지라(`api-convention.md`) 밀리초를 실을 수 없기 때문이다. 서버 시각을 초 단위로 주면 보정 오차가 최대 1초가 되는데, `RUNNING_START`는 `scheduledStartAt`보다 이르면 거절되므로 그 오차를 감당할 수 없다
- **솔로 러닝에는 오지 않는다** — 솔로는 SSE를 쓰지 않고, `POST /running-rooms/solo` 응답을 받은 즉시 WS로 붙는다

#### WebSocket 연결 — `/api/v1/ws/running`

- **연결**: `wss://.../api/v1/ws/running` + `Authorization: Bearer {accessToken}`
- **인증 실패**: 업그레이드를 거부하고 **HTTP 401**로 응답한다 — 연결이 서기 전이라 `ERROR` 프레임을 쓸 수 없다. 본문은 REST 에러 포맷과 같다. 클라는 `POST /auth/refresh` 후 재연결하고, 다시 실패하면 재로그인으로 보낸다. 같은 이유로 아래 `ERROR`의 code 목록에는 인증 코드가 없다
- **토큰은 핸드셰이크에서 한 번만 검증한다** — 연결 유지 중 `accessToken`이 만료돼도 끊지 않는다. 러닝 구간이 토큰 수명보다 길어 만료마다 끊으면 좌표 전달이 그때마다 멈추고 갱신·재연결·재전송 비용이 든다. 재연결은 같은 러닝으로 이어지므로 기록이 갈리지는 않는다. 단 탈퇴하면 서버가 연결을 닫는다(close code `1000`). 탈퇴를 처리한 서버 인스턴스에 붙은 연결만 닫으며, 여러 대로 운영하면 다른 인스턴스의 연결은 남을 수 있다. 토큰이 폐기돼 재연결 핸드셰이크가 401로 막힌다. 클라는 REST용 토큰을 평소대로 갱신하고, 새 토큰은 재연결할 때만 쓴다
- **중복 연결은 마지막 것만 남긴다** — 같은 사용자의 새 연결이 `RUNNING_START`를 보내면 서버가 기존 연결을 close code `4001`로 닫는다. 기기 전환·앱 재시작 때 이전 소켓이 남아 있을 수 있는데 둘 다 살려두면 같은 `(runningRoomId, userId, sequence)`에 서로 다른 트랙이 섞인다. `4001`을 받은 클라는 재연결하지 않는다 — 다른 기기가 이어받은 것이다
- **보낼 메시지가 밀려 한도(운영값)를 넘으면 서버가 close code `4500`으로 닫는다** — 네트워크가 끊긴 것에 가까운 상태라 연결을 정리하고 재연결로 복구한다. `4001`이 아니므로 클라는 평소대로 재연결한다
- **클라가 보내는 텍스트 메시지 한 건은 운영값(현재 64KB) 이하여야 한다** — 넘으면 서버(WebSocket 컨테이너)가 close code `1009`로 닫는다. 재연결 뒤 로컬 트랙을 다시 보낼 때도 한 번에 몰아 보내지 말고 이 크기 안으로 나눠 보낸다 — 한 번에 보내면 다시 `1009`로 끊겨 재연결·재전송이 되풀이된다
- **keep-alive**: 클라가 주기적으로 `HEALTH_CHECK`(C→S)를 보내고 서버가 `HEALTH_CHECKED`(S→C)로 응답한다. 둘 다 `data`는 비운다. **유휴 상태가 서버 설정 시간(운영값)을 넘으면 서버가 연결을 닫는다** — 좌표를 계속 보내는 러닝 중에는 별도 신호가 필요 없고, 시작 전 대기 구간에서 의미가 있다. 프록시 유휴 타임아웃을 막는 목적은 SSE와 같다
- **연결이 끊겨도 러닝은 끝나지 않는다** — 방·참가자 상태는 그대로 두고 재연결을 기다린다. 서버의 강제 종료(매칭 방에만 건다)는 **`start_at`부터 잰 유예**(운영값) 기준이라 연결 상태와 축이 다르다(`running_room_sessions.is_connected`도 방 배정 여부이지 접속 여부가 아니다)
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
  - **`RUNNING_FINISHED`는 요청 없이도 온다.** 목표가 있는 방에서 좌표 배치로 확정 거리가 목표를 채우면 서버가 그 자리에서 종료를 확정하고 보낸다(5-D). 클라는 `RUNNING_FINISH`의 ack와 똑같이 처리한다
  - ack의 `data`는 비운다. 예외는 `RUNNING_STARTED` 하나로, 진입·재연결 화면 복구용 스냅샷을 싣는다(5-C)
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
  | `PAUSE_SEGMENT_DECREASED` | `RUNNING_LOCATION_UPDATE`의 `pauseSegment`가 `sequence` 순서를 거슬러 줄었다 — 검증 실패 계열(REST의 400에 해당)이다. 클라 로컬 트랙이 깨졌다는 뜻이라 같은 배치를 다시 보내도 계속 거부된다. 클라는 좌표 전송을 멈추고 사용자에게 알린 뒤 `RUNNING_FINISH`(`forced=true`)로 받은 데까지 기록을 확정한다 |
  | `RUNNING_SESSION_UNAVAILABLE` | 외부 저장소 장애로 세션을 등록하지 못함 — 이 연결로는 러닝 메시지를 처리할 수 없다(참가자 상태는 이미 `RUNNING`일 수 있다). 잠시 뒤 `RUNNING_START`를 다시 보낸다 — 멱등이다 |
  | `RUNNING_TRACK_UNAVAILABLE` | 외부 저장소 장애로 좌표를 저장하지 못함 — 러닝은 계속된다. 종료(`RUNNING_FINISH`·목표 도달 자동 종료) 중 트랙을 읽지 못한 경우에도 나가며, 이때는 종료가 확정되지 않았으니 잠시 뒤 `RUNNING_FINISH`를 다시 보낸다. `sourceType`으로 갈린다 — `RUNNING_LOCATION_UPDATE`면 다음 배치 차례에 로컬 트랙 전체를 다시 보내고, `RUNNING_FINISH`면 좌표가 아니라 `RUNNING_FINISH`를 다시 보낸다 |
  | `ROOM_NOT_FOUND` | 방 없음 |
  | `NOT_ROOM_PLAYER` | 이 방 참가자가 아님 |
  | `INVALID_ROOM_STATE` | 현재 상태에서 불가한 요청 |
  | `ONBOARDING_NOT_COMPLETED` | 종료(`RUNNING_FINISH`·목표 도달 자동 종료) 처리 중 온보딩 정보가 없음 — 자동 종료면 `sourceType`이 `RUNNING_LOCATION_UPDATE`다 |
  | `INTERNAL_SERVER_ERROR` | 예기치 못한 서버 오류 — 러닝은 계속된다. 표에 없는 오류는 이 코드로 마스킹된다 |

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
  | 1 | 끝나지 않은 신청(`deleted_at` 없음)이 있고 그 신청이 이 방에 배정돼 있는지 확인 | 아니면 `NOT_ROOM_PLAYER` — **나간 사람은 신청이 닫혀 여기서 걸린다.** 예전에 거쳐 간 방도 지금 신청의 방이 아니라 여기서 걸린다 |
  | 2 | 배정(`is_connected`)이 끊겨 있으면 빈자리가 있을 때 다시 잇는다(재입장) | 끊겨 있지 않으면 통과. 그새 자리가 찼으면 `INVALID_ROOM_STATE` |
  | 3 | 방이 `MATCHED`면 `STARTED`로 올린다 | 통과 |
  | 4 | 참가자가 `JOINED`면 `RUNNING`으로 올린다 | 통과 |
  | 5 | WS 세션을 방에 등록하고 세션이 `runningRoomId`를 기억한다(브로드캐스트 대상·이후 메시지의 방) | 덮어쓴다 |
  | 6 | 참가자 화면 상태(`status`)를 `RUNNING`으로 바꾸고, 바뀌었으면 방에 `RUNNING_PROGRESS_UPDATED`를 보낸다 | 이미 `RUNNING`이면 보내지 않는다 |
  | 7 | ack 전송 | — |

- **3번에 `type` 분기가 없다.** 매칭은 `start_at`에 시작 스케줄러가 이미 `STARTED`로 올려놨으니 통과하고, 솔로는 `start_at`이 개시 시각(과거)이라 여기서 올라간다. 같은 코드가 두 종류를 다 덮는다
- **전 단계가 멱등하다.** 중복 `RUNNING_START`는 아무 상태도 다시 바꾸지 않고 ack만 재전송한다
  - 예외는 6번의 화면 상태다 — `PAUSED`·`DISCONNECTED`였으면 `RUNNING`으로 풀린다. 재연결이 상대 화면의 끊김 표시를 거두는 경로가 이것이다
- **6번은 5번 뒤, ack 앞이다.** 세션 등록 전이면 자기 상태 통지를 본인이 놓치고, ack(스냅샷) 뒤면 본인이 상태 없음(`DISCONNECTED`)으로 실린다
- **거부**: `start_at`이 아직 안 됐으면 `INVALID_ROOM_STATE`(매칭에서 미리 쏘는 것 차단). 방이 `FINISHED`·`CANCELLED`여도 같은 코드
- **ack**: `RUNNING_STARTED` — 러닝 화면을 그리는 데 필요한 스냅샷을 싣는다

```json
{
  "runningRoomId": 125,
  "startedAt": "2026-07-25T19:00:00",       // 방의 start_at — 경과 시간의 기준
  "targetDistanceMeters": 5000,             // 목표 없는 솔로 방은 null
  "nextSequence": 120,                      // 본인 기준 — 서버가 받은 마지막 sequence + 1. 받은 좌표가 없으면 0
  "pauseSegment": 2,                        // 본인 기준 — 서버가 받은 마지막 좌표의 pauseSegment. 받은 좌표가 없으면 0
  "players": [
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440015",
      "nickname": "완두콩",
      "profileImageUrl": "https://...",     // 사진이 없으면 null
      "distanceMeters": 1520,               // 서버가 좌표로 누적한 값
      "currentPaceSecondsPerKm": 345,       // nullable
      "status": "RUNNING"                   // RUNNING | PAUSED | DISCONNECTED | FINISHED — RUNNING_PROGRESS_UPDATED와 같은 값
    }
  ],
  "comboPeers": [                           // RUNNING_COMBO_UPDATED의 peers와 같은 모양, 겹친 상대가 없으면 []
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440016",
      "gapMeters": -18,
      "comboCount": 3,
      "maxComboCount": 12
    }
  ]
}
```

- **`RoomInfo`를 재사용하지 않는다.** 그쪽은 매칭 대기방을 그리려고 만든 구조라 진행 상황이라는 개념이 없고, 러닝 중에는 `closeAt`·`teamAveragePace`가 의미를 잃는다. 대신 `players[]`가 **`RoomInfo`의 프로필 + `RUNNING_PROGRESS_UPDATED`의 진행**을 합친 모양이라, 클라는 **이 스냅샷으로 채우고 갱신분으로 덮는** 한 쌍으로 다룬다
- **본인도 `players`에 담는다.** 본인 진행은 클라가 직접 계산하지만, 앱 재설치로 로컬 트랙이 사라지면 본인 누적 거리를 복구할 경로가 이것뿐이다. 다만 **클라는 화면 표시에 로컬 계산값을 우선**하고 이 값은 복구용으로만 쓴다
- **`nextSequence`·`pauseSegment`는 로컬 트랙을 잃은 클라가 번호를 이어 쓰는 값이다.** 클라는 다음에 붙일 `sequence`와 현재 `pauseSegment`를 각각 로컬 값과 이 값 중 **큰 쪽**으로 맞춘다
  - 평소(재연결·앱 재시작)에는 로컬 값이 항상 같거나 커서 바뀌는 것이 없다. 앱 재설치처럼 로컬 트랙을 잃었을 때만 서버 값으로 올라간다 — 맞추지 않으면 `sequence`는 이미 받은 순번으로 조용히 버려지고, `pauseSegment`는 `PAUSE_SEGMENT_DECREASED`로 계속 거부된다
  - `pauseSegment`는 서버가 `PAUSE_SEGMENT_DECREASED`를 판정할 때 비교하는 값과 같다. 그래서 이 값 이상으로 이어 쓰면 거부되지 않는다
  - 한계: 일시정지 중에 재설치하면 클라는 일시정지 상태를 잃는다. 그 사이 이동은 일시정지가 아니라 관측하지 못한 이동으로 처리된다
- **이미 이탈·완주한 참가자는 담지 않는다.** 러닝 화면에는 그들을 그릴 자리가 없다. 연결만 끊긴 참가자는 그대로 담고 `status`가 `DISCONNECTED`로 실린다
- **`players[].status`는 각 참가자의 현재 화면 상태다.** 상태가 기록된 적 없는 참가자(매칭 방에 배정됐지만 `RUNNING_START`를 보낸 적 없는 사람)는 `DISCONNECTED`로 싣는다. 요청한 본인은 서버 처리 6번에서 이미 `RUNNING`으로 바뀌었으므로 항상 `RUNNING`이다
  - 이탈·완주자를 담지 않으므로 `FINISHED`는 종료 확정과 세션 정리 사이의 짧은 틈에만 나올 수 있다
- **이 ack는 요청한 본인에게만 간다.** 다른 참가자는 서버 처리 6번에서 요청자의 상태가 바뀐 경우(첫 진입, 끊김에서 재연결, 멈춘 채 재연결)에만 `RUNNING_PROGRESS_UPDATED`(`status: RUNNING`)로 알게 된다
- **`comboPeers`는 받는 사람이 낀 관계만, `players`에 있는 상대만 싣는다.** 이미 끝낸 상대와의 콤보는 판정에서 떨어져 나갈 때까지 잠시 남아 있으므로 여기서 거른다. `RUNNING_COMBO_UPDATED`의 `peers`와 같은 모양이라 클라는 한 벌의 코드로 스냅샷과 갱신을 다 그린다
- **`startedAt`은 방의 `start_at`이다** — 참가자가 실제로 `RUNNING`이 된 시각을 쓰면 같은 방에서 사람마다 경과 시간이 달라진다
- **최초 진입과 재연결에 똑같이 나간다.** 클라가 둘을 구분하지 않는 것이 `RUNNING_START`의 전제이므로 ack만 다르게 하지 않는다
- 이 스냅샷이 있어야 `RUNNING_PROGRESS_UPDATED`·`RUNNING_COMBO_UPDATED`가 `userId`만 싣는 설계가 성립한다 — 표시 정보는 여기서 받아 둔다
- **`RUNNING_START`는 일시정지를 푼다.** 일시정지 중에 재연결했다면 클라는 이 ack를 받고 로컬 트랙을 재전송한 **뒤** `RUNNING_PAUSE`를 다시 보낸다. 끊긴 사이 쌓인 처음 보는 좌표가 상태를 `RUNNING`으로 바꾸므로 순서를 지켜야 한다(`RUNNING_PAUSE` 절)

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
      "recordedAt": "2026-07-25T19:10:30",  // 측정 시각
      "pauseSegment": 0                     // 일시정지 구간 번호. 일시정지 버튼을 누를 때마다 1씩 오른다
    }
  ]
}
```

- **`runningRoomId`를 싣지 않는다.** 방은 `RUNNING_START`가 참가자 검증을 마치고 정한 뒤 서버가 WS 세션에 들고 있다. 10초마다 반복되는 메시지에 매번 실으면 클라가 참가하지 않은 방을 지정할 수 있게 된다
  - `RUNNING_START` 없이 이 메시지를 보내면 서버에 정해진 방이 없어 `RUNNING_NOT_STARTED`로 거부한다. 클라는 `RUNNING_START`부터 다시 보낸다
- **필수는 `sequence`·`latitude`·`longitude`·`accuracyMeters`·`recordedAt`·`pauseSegment` 여섯뿐이다.** 나머지는 단말이 못 잴 수 있어 `null`로 보내도 되고, 서버는 그 좌표를 버리지 않고 값이 비었다는 사실만 남긴다 — 배치 하나가 통째로 거절되면 그 10초가 통으로 빈다. 케이던스는 보수 센서가, 속도·방위는 GPS 픽스가 있어야 온다
  - 비어 있으면 해당 지표를 표본에서 제외한다. 유효 표본이 없으면 지표 자체가 null이다(`running_records.avg_cadence`, erd.md)
  - 배치를 거부하는 건 필수 값 쪽뿐이다 — `locations`가 없거나 비었거나, 좌표 중 하나라도 필수 여섯 값이 빠졌거나 범위(위도 -90~90, 경도 -180~180, `sequence`·`pauseSegment` 0~100,000)를 벗어나면 배치 전체를 `INVALID_REQUEST`로 거부한다. `sequence` 상한은 정상 클라가 닿지 않는 안전 한도다(6시간 × 1Hz = 21,600)
- **클라는 1~2초 간격으로 수집해 로컬에 쌓으면서, 10초마다 모아서 보낸다.** 좌표 하나씩 10초마다 보내면 트랙이 성겨져 경로와 거리 정확도가 떨어진다
- **`pauseSegment`는 일시정지 구간 번호다.** 러닝을 시작할 때 0이고, 일시정지 버튼을 누를 때마다 1 올린다. 서버는 이웃한 두 좌표의 값이 다르면 그 사이를 일시정지로 보고 거리·시간에서 뺀다. 이동 거리·속도와 무관하며, 러닝 중 누적과 확정 거리에 모두 적용한다(feature-spec 트랙 필터)
  - **버튼 일시정지에만 올린다.** 자동 정지(버튼 없이 멈춤)·재연결·앱 재시작·위치 권한 재획득에는 올리지 않는다 — 잘못 올리면 그 자리의 실제 이동이 기록에서 빠진다
  - **버튼을 누른 즉시 올리고 로컬에 저장한다.** 남은 좌표의 전송 결과를 기다리지 않는다 — 위치 배치에는 ack가 없고, 남은 좌표는 각자 저장된 옛 번호로 나간다. 증가가 빠지거나(끊긴 채 일시정지, 저장 전 앱 종료) 늦으면 재개 후 좌표가 같은 번호로 나가 일시정지 중 이동이 거리에 들어간다. 서버는 올린 번호를 좌표로만 알 수 있어 `RUNNING_STARTED`로도 되살릴 수 없다
  - `sequence`는 일시정지와 무관하게 이어서 센다
  - 값은 좌표와 함께 로컬 트랙에 저장한다 — 재전송할 때 처음과 같은 값이어야 한다. 서버는 같은 `sequence`의 좌표를 먼저 받은 것으로 남긴다
  - **`sequence` 순으로 줄어들 수 없다.** 서버는 배치 안을 `sequence` 순으로 놓았을 때 값이 줄거나, 마지막으로 반영한 좌표보다 큰 `sequence`의 값이 그 좌표의 값보다 작으면 배치 전체를 `PAUSE_SEGMENT_DECREASED`로 거부한다. 번호가 되돌아가면 일시정지가 아닌 구간이 일시정지로 빠지거나, 일시정지 구간이 거리로 들어갈 수 있다
  - **이 오류를 받으면 클라는 좌표 전송을 멈추고 사용자에게 알린 뒤 `RUNNING_FINISH`(`forced=true`)로 받은 데까지 기록을 확정한다.** 같은 값을 다시 보내도 풀리지 않는다. 재설치로 번호를 잃는 경우는 `RUNNING_STARTED`의 `nextSequence`·`pauseSegment`로 막으므로, 남는 원인은 클라의 번호 관리 오류다. 서버는 거부할 때마다 원인과 순번을 INFO 로그로 남긴다
  - 일시정지 신호(`RUNNING_PAUSE`)가 아니라 이 값으로 계산하는 이유: 좌표와 함께 저장·재전송되므로 유실되지 않고, 좌표와 같은 시계·같은 순서를 따른다
- 페이스·거리·케이던스·진행 시간은 러닝 중 표시용으로 클라이언트가 계산한다. 칼로리는 러닝 중 표시·전송하지 않고 종료 시 서버가 계산한다
  - **클라 로컬 거리도 일시정지 경계 칸을 더하지 않는다.** `pauseSegment`가 바뀐 칸(일시정지 직전 → 재개 직후)은 서버가 거리에서 빼므로, 클라가 더하면 화면으로는 목표에 닿았는데 서버 확정 거리는 모자라 종료가 미뤄진다(`RUNNING_GOAL_PENDING`)
- 서버는 Redis(`runningRoomId+userId` 키)에 버퍼링하고 기록을 생성할 때 S3에 업로드한다(`gpsTrackKey`) — `runningRoomId`는 세션이 들고 있는 값이다
- **ack 없음** — 고빈도 메시지라 건별 ack는 트래픽 낭비. 실패는 `ERROR`로 통지
  - 저장소 장애로 배치를 담지 못하면 `RUNNING_TRACK_UNAVAILABLE`을 보내되 **연결은 끊지 않고 러닝도 계속한다.** 원본은 로컬 트랙에 남아 있다
  - **이 오류(`sourceType: RUNNING_LOCATION_UPDATE`)를 받으면 클라는 재연결을 기다리지 않고 다음 배치 차례에 로컬 트랙 전체를 처음 `sequence`부터 저장된 `pauseSegment` 그대로 한 번 다시 보낸다.** 연결이 끊기지 않으니 재연결만 기다리면 그 배치는 러닝이 끝날 때까지 들어오지 않고, 빈칸은 관측 공백 규칙으로 판정돼 거리가 빠지거나 경로가 끊긴다. 오류에는 어느 배치인지 실리지 않아 전체를 보낸다 — 서버는 이미 받은 `sequence`를 무시한다
    - 장애가 이어지면 다시 보낸 메시지마다 또 오류가 온다. 오류마다 즉시 다시 보내지 말고 배치 주기(약 10초)보다 자주 보내지 않는다. 한 메시지 64KB 한도에 맞춰 나눠 보낸다
    - `RUNNING_FINISH` 전에 재전송이 남아 있으면 먼저 전체를 다시 보낸다
    - 일시정지 중에 다시 보냈다면 처음 보는 좌표가 멈춤을 풀므로, 재전송 뒤 `RUNNING_PAUSE`를 다시 보낸다
  - **누적 상태(마지막 `sequence`·`pauseSegment`)를 읽지 못해도 배치를 저장하지 않고 `RUNNING_TRACK_UNAVAILABLE`을 보낸다.** 줄어든 `pauseSegment`를 걸러낼 기준이 없어 검증하지 않은 좌표를 받지 않는다 — 위 재전송으로 다시 온 배치가 그때 검증된다
  - 장애가 이어지면 배치마다 `ERROR`가 나간다. 클라는 건별 알림 대신 "저장 실패 중" 상태 표시 하나로 다룬다
- 재연결하면 클라이언트는 로컬 트랙 전체를 처음 `sequence`부터 처음과 같은 `pauseSegment`로 다시 보내고, 서버는 `(runningRoomId, userId, sequence)`가 같은 좌표를 무시한다(`runningRoomId`는 재연결 뒤 `RUNNING_START`가 다시 정한다). 앱 재설치로 로컬 트랙을 잃었으면 `RUNNING_STARTED`의 `nextSequence`·`pauseSegment`로 번호를 이어 쓴다. ack가 없으므로 성공 경계를 추정하지 않으며 로컬 트랙은 `RUNNING_FINISHED`를 받은 뒤 삭제한다 — `RUNNING_FINISH`의 ack든 아래 자동 종료든 같다
- **목표 거리를 채우면 서버가 러닝을 끝낸다.** 서버가 누적한 거리(`RUNNING_PROGRESS_UPDATED`의 `distanceMeters`와 같은 값)가 목표 이상이 되면, 그 배치를 저장하고 진행을 알린 뒤 저장된 트랙을 트랙 필터로 분석해 **확정 거리**를 낸다. 확정 거리도 목표 이상이면 `RUNNING_FINISH`(`forced=false`)와 같은 규칙으로 종료를 확정하고 `RUNNING_FINISHED`를 보낸다. 클라는 받으면 좌표 전송을 멈추고 로컬 트랙을 지운 뒤 결과 화면으로 간다
  - **목표가 없는 솔로 방은 해당 없다.** 사용자가 `RUNNING_FINISH`를 보내야 끝난다
  - **확정 거리가 목표 미만이면 끝내지 않고 아무것도 보내지 않는다.** 러닝 중 누적과 확정 거리는 어긋날 수 있다 — 누적만 보고 끝내면 확정 거리가 목표 직전으로 나와 서버가 끊은 러닝이 조기 종료로 남는다. 러닝은 그대로 계속되고 다음 배치가 다시 확인한다
  - **누적이 목표 위에 있는 한 배치마다 다시 판정한다.** 종료가 실패하면 그 배치에 `ERROR`(`sourceType: RUNNING_LOCATION_UPDATE`)가 나가고 다음 배치가 다시 시도한다. 이미 끝난 뒤 늦게 도착한 배치는 **목표 유무와 관계없이** 저장·누적·진행 알림 없이 `RUNNING_FINISHED`만 다시 보내므로, 클라는 이 메시지를 **여러 번 받아도** 같은 처리를 해야 한다

#### `RUNNING_PROGRESS_UPDATED` (S→C) — 참가자 진행 정보

```json
{
  "userId": "01a0f2d4-6180-7000-8000-446655440015",
  "distanceMeters": 1520,               // 현재까지 이동 거리(서버가 좌표로 누적)
  "targetDistanceMeters": 5000,         // 목표 거리(m). 목표 없는 솔로 방은 null
  "currentPaceSecondsPerKm": 345,       // 현재 페이스(초/km), nullable
  "status": "RUNNING"                   // RUNNING | PAUSED | DISCONNECTED | FINISHED
}
```

- **두 경우에 나간다.**
  - `RUNNING_LOCATION_UPDATE` 배치를 처리할 때마다 — 참가자당 약 10초 간격이다. 재전송분만 온 배치라 거리가 그대로여도 나간다
  - 참가자 상태(`status`)가 바뀔 때 — 좌표 배치와 무관하게 즉시 나간다(아래 상태 표). 상태가 그대로면 나가지 않는다
- **누적 거리 조회에 실패한 배치는 저장하지 않고 알림도 거른다.** 보낸 본인에게만 `ERROR`(`RUNNING_TRACK_UNAVAILABLE`)를 보내고, 연결은 끊지 않으며 러닝도 계속된다 — `pauseSegment`를 마지막으로 반영한 좌표와 비교할 기준이 없어서다. 원본은 로컬 트랙에 남아, 클라가 다음 배치 차례에 전체를 다시 보내 복구한다(`RUNNING_LOCATION_UPDATE` 절). 이 배치로 바뀌었어야 할 상태(예: `PAUSED` → `RUNNING`)는 다음 배치에서 반영된다
- **`pauseSegment`가 줄어든 배치는 통째로 거부한다.** 저장·누적·알림이 모두 없고 상태도 바뀌지 않는다. 보낸 본인에게만 `ERROR`(`PAUSE_SEGMENT_DECREASED`)를 보낸다
- **갱신된 참가자 한 명만 싣는다.** 좌표 배치를 받아 진행이 바뀐 사람만 알리면 되고, 전원 스냅샷을 매번 보내면 인원수만큼 payload가 커진다
  - 클라는 참가자별 최신값을 로컬에 들고 이 메시지로 덮는다
  - **최초 진입·재연결 시 전원의 현재 진행은 `RUNNING_STARTED` 스냅샷이 나른다**(5-C) — 이 메시지는 갱신분만 실으므로 그것만으로는 화면을 복구할 수 없다
  - `runningRoomId`를 싣지 않는다 — 클라는 `RUNNING_START`로 정한 방 하나에만 있다
  - 닉네임·`profileImageUrl`을 싣지 않는다 — 10초마다 인원수만큼 나가는 메시지라 presigned URL(수백 자)이 붙으면 payload가 커진다. 표시 정보는 `RUNNING_STARTED` 스냅샷에서 받아 `userId`로 찾는다
- **본인도 받는다.** 방 전체가 같은 서버 기준값을 받게 하려는 것이다. 다만 본인 표시 거리는 클라의 로컬 계산값이 정본이라 이 값으로 덮지 않는다
- `distanceMeters`는 **서버가 수신한 좌표로 누적한 값**이다. 일시정지 경계(`pauseSegment`가 바뀐 칸)는 누적에서도 뺀다. 클라 표시용 거리(5-D)와 미세하게 다를 수 있으나 다른 참가자 화면에 쓰는 값이라 서버 기준으로 통일한다
- `currentPaceSecondsPerKm`는 거리에 반영한 마지막 좌표의 값을 옮긴다. 재전송분만 온 배치면 직전 값을 유지한다 — 단말이 못 재면 `null`이다
- **`status`는 멈춘 것·끊긴 것·끝난 것을 단순히 느려진 것과 구분한다.** 없으면 상대가 화면에서 갑자기 뒤처진 것처럼 보인다. **화면 표시용이며 기록(거리·시간)에는 영향을 주지 않는다** — 저장하지 않고 Redis에만 둔다

  | `status` | 뜻 | 바뀌는 때 |
  |---|---|---|
  | `RUNNING` | 뛰는 중 | `RUNNING_START`, `RUNNING_RESUME`, 처음 보는 좌표가 담긴 `RUNNING_LOCATION_UPDATE` |
  | `PAUSED` | 일시정지 | `RUNNING_PAUSE` |
  | `DISCONNECTED` | 연결 끊김 | WS 연결 종료 감지 — 앱 종료는 즉시, 응답 없이 끊긴 연결은 유휴 타임아웃(운영값) 뒤 |
  | `FINISHED` | 러닝 종료. **이후 바뀌지 않는다** | 종료 확정 — `RUNNING_FINISH`, 목표 도달 자동 종료, 강제 종료 |

  - **`FINISHED`에서는 어디로도 가지 않는다.** 종료 ack를 받은 클라가 연결을 닫아도 `DISCONNECTED`로 퍼지지 않고, 종료 뒤 늦게 도착한 배치는 받지 않고(저장·누적·진행 알림 없음) `RUNNING_FINISHED`만 다시 보낸다
  - **`DISCONNECTED`로 바뀌는 것은 `RUNNING`·`PAUSED`에서뿐이다.** 한 번도 붙지 않은 참가자는 상태가 기록되지 않으며, `RUNNING_STARTED` 스냅샷에서만 `DISCONNECTED`로 실린다(5-C)
  - **좌표·`RUNNING_PAUSE`·`RUNNING_RESUME`이 오면 `DISCONNECTED`도 풀린다.** 메시지가 왔다는 것 자체가 연결돼 있다는 뜻이다 — 재연결한 뒤 옛 연결의 끊김이 늦게 반영돼도 다음 메시지가 바로잡는다
  - **재전송분만 온 배치는 상태를 바꾸지 않는다.** 이미 받은 좌표뿐이면 `PAUSED`가 그대로다. 재연결은 `RUNNING_START`가 먼저 `RUNNING`으로 풀므로, 멈춘 채 재연결했으면 클라가 재전송 뒤 `RUNNING_PAUSE`를 다시 보낸다(5-C). 상태가 기록된 적 없는 참가자의 배치는 `RUNNING`으로 싣는다
  - 상태 변경으로 나간 통지의 `distanceMeters`·`currentPaceSecondsPerKm`은 마지막으로 저장된 누적값이다
- **상태 변경 알림은 재전송하지 않는다.** 전송에 실패하면 다음 좌표 배치나 상태 변경 때 최신 상태가 다시 실리고, 재연결하면 `RUNNING_STARTED` 스냅샷으로 복구한다

#### `RUNNING_COMBO_UPDATED` (S→C) — 콤보 갱신

```json
{
  "peers": [
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440015",
      "gapMeters": -18,                 // 양수면 상대가 앞, 음수면 뒤
      "comboCount": 12,                 // 이 상대와 지금 이어지고 있는 콤보
      "maxComboCount": 30               // 이번 러닝에서 이 상대와 기록한 최고 콤보
    }
  ]
}
```

- **콤보는 참가자 쌍마다 따로 센다.** `peers`는 받는 사람이 낀 관계 전부이며, 다른 두 참가자끼리의 콤보는 담기지 않는다
- **받는 사람의 상태 전체를 담는다.** 콤보가 끊긴 상대는 목록에서 빠지므로 **끊김을 알리는 별도 이벤트가 없다** — 클라는 이 목록으로 화면을 갈아끼운다
  - 통이 유실돼도 다음 통이 현재 상태를 다시 실어 온다. 놓친 이벤트를 되짚을 필요가 없다
  - 아무와도 겹치지 않으면 `peers`는 빈 배열이다
- **`RUNNING_START`로 처음 들어올 때 한 번 나간다.** 판정 기준(누적 거리·상태)을 세우고 이미 이어지던 관계를 바로 그리기 위해서다. 출발선에서는 콤보가 붙지 않는다 — 둘 다 출발해야 붙는다(feature-spec 러닝 콤보의 참가자 상태). 재연결·중복 `RUNNING_START`에는 다시 판정하지 않는다
- **위치 배치를 받을 때마다 나간다.** 보낸 참가자와 **그와 관계가 얽힌 참가자**(지금 겹친 사람·방금 끊긴 사람)에게 보내며, 무관한 참가자에게는 보내지 않는다
  - 방금 끊긴 상대에게도 보내야 한다 — 안 보내면 그쪽 화면에 끊긴 콤보가 남는다
  - 같은 관계를 두고 양쪽의 배치가 각각 통을 만들어 내용이 겹칠 수 있다. `comboCount`는 저장된 시작 시각과 쌓인 시각에서 계산하므로 두 통의 값이 같고, 화면은 같은 값으로 덮여 바뀌지 않는다
- **`gapMeters`는 누적 주행 거리의 차이다** — 좌표상 거리가 아니다. 참가자들이 각자 다른 장소에서 뛰기 때문이다
- **`comboCount`는 서버가 시각에서 계산한 값이다.** 클라가 자체적으로 세지 않는다 — 배치 도착 수로 세면 인원과 전송 빈도에 따라 값이 달라진다
  - 둘 다 움직였다고 확인된 시각까지만 센다 — 함께 달리는 중에도 최대 한 배치 늦게 올라가고, 둘 중 하나가 서 있거나 관측이 없으면 멈춰 있다. 한 번 보인 값이 줄어들지는 않는다
- `runningRoomId`를 싣지 않는다 — 클라는 `RUNNING_START`로 정한 방 하나에만 있다
- `profileImageUrl`·닉네임을 싣지 않는다 — 고빈도 메시지라 `userId`로만 지목하고 표시 정보는 진입 시 받은 참가자 목록에서 찾는다
- **ack 없음** — 실패는 `ERROR`로 통지
- **결과 응답에 싣지 않는다.** 콤보 구간은 방이 닫힐 때 `running_combos`에 남지만([erd.md](erd.md)), 결과·기록 응답에는 아직 콤보 필드가 없다
- 판정 규칙(붙는 거리·봐주는 횟수·신선도·참가자 상태·보정)은 [feature-spec.md](feature-spec.md)의 러닝 콤보 절이 정본이다

#### `RUNNING_GOAL_PENDING` (S→C) — 남은 거리 알림

```json
{
  "remainingMeters": 40              // 목표 − 확정 거리. 항상 1 이상
}
```

- **`RUNNING_FINISH`(`forced=false`)의 확정 거리가 목표에 못 미칠 때 그 응답으로 나간다.** 클라 화면으로는 목표를 다 뛰어 정상 종료를 눌렀는데, 서버가 오염 좌표(멈춘 동안의 흔들림·GPS 튐 등)를 빼고 낸 확정 거리가 모자란 경우다. 서버는 **종료를 확정하지 않고** 남은 거리를 알린다 — 그대로 확정하면 다 뛰었다고 믿는 사용자의 러닝이 조기 종료로 남는다
- **이 경우에만 나간다.** 자동 종료(위치 배치)는 확정 거리가 모자라면 아무것도 보내지 않고 다음 배치가 다시 확인한다 — 사용자가 아무것도 하지 않았으니 알릴 것이 없다. `forced=true`와 강제 종료 시각은 그 시점 거리로 바로 확정한다
- **ACK이 아니며 상태를 바꾸지 않는다.** 러닝은 그대로 진행 중이고 기록도 저장되지 않았다. 클라는 **로컬 트랙을 지우지 않고** 좌표 수집·전송을 계속한다
- **클라는 "N m 더"를 보여주고 계속 뛰게 한다.** 남은 거리를 채우면 자동 종료가 `RUNNING_FINISHED`를 보낸다
- **그래도 끝내려면 `RUNNING_FINISH`(`forced=true`)를 보낸다.** 클라는 "N m 남았어요. 지금 끝내면 완주로 기록되지 않아요"를 한 번 확인받고 보낸다. 서버는 그 시점 확정 거리로 바로 판정한다
- **`remainingMeters`는 10m 경계로 자르기 전의 확정 거리로 낸다** — 자른 값으로 내면 남은 거리가 최대 10m 부풀려진다. 0.3m만 모자라도 1이다
- **본인에게만 보낸다**
- `runningRoomId`를 싣지 않는다 — 클라는 `RUNNING_START`로 정한 방 하나에만 있다

#### `RUNNING_PAUSE` / `RUNNING_RESUME` (C→S) — 일시정지·재개

```json
{}
```

- **상대 화면에 멈춤을 알리는 신호다.** 서버는 참가자 상태를 `PAUSED`/`RUNNING`으로 바꾸고, 바뀌었으면 방에 `RUNNING_PROGRESS_UPDATED`(`status`)를 보낸다. 멈춘 동안에는 좌표 배치가 오지 않아 알림을 따로 내야 한다
- **서버는 이 신호로 기록 계산을 바꾸지 않는다.** 기록은 수신한 좌표로만 계산한다 — 신호가 유실되거나 조작돼도 기록이 흔들리지 않아야 한다. 클라는 일시정지 동안 좌표 수집·전송을 멈추고 본인 화면의 경과 시간도 멈춘다
  - 일시정지 구간은 좌표의 `pauseSegment`로 뺀다(`RUNNING_LOCATION_UPDATE`). 버튼 없이 멈춘 구간(제자리 흔들림·긴 공백)은 서버가 좌표만으로 판정해 뺀다 — [feature-spec.md](feature-spec.md)의 트랙 필터 절
- **일시정지하면 콤보는 항상 끊긴다.** 일시정지 길이와 무관하다. 이 신호를 받으면 일시정지 표시를 남겨 그 참가자가 낀 다음 판정에서 끊고, 신호가 유실되거나 판정 전에 재개해도 재개 후 첫 배치의 `pauseSegment`가 바뀌어 있으면 그 판정에서 끊는다. 다시 함께 달리면 0부터 새로 쌓이고 최고 콤보는 남는다(feature-spec 러닝 콤보)
  - **클라는 콤보 중에 일시정지 버튼을 누르면 콤보가 끊긴다는 안내를 띄운다.** 콤보 중인지는 마지막으로 받은 `RUNNING_COMBO_UPDATED`의 `peers`가 비어 있지 않은지로 판단한다
- **다른 참가자는 계속 진행한다.** 일시정지는 본인에게만 적용된다
- **멈춤은 셋 중 무엇으로든 풀린다** — `RUNNING_RESUME`, `RUNNING_START`, 처음 보는 좌표가 담긴 `RUNNING_LOCATION_UPDATE`. `RUNNING_RESUME`은 다음 배치(최대 10초)를 기다리지 않고 상대 화면을 즉시 바꾸려는 것이고, 좌표는 `RUNNING_RESUME`이 유실됐을 때의 안전망이다
- **멱등이다.** 같은 상태로 바꾸는 요청은 아무것도 보내지 않는다. 이미 끝난 참가자(`FINISHED`)에게는 아무 효과가 없다
- **data는 보지 않는다.** 계약상 `{}`로 보낸다
- **클라가 지킬 순서**
  - **`RUNNING_PAUSE` 전에 남은 좌표를 먼저 보낸다.** 한 연결 안에서는 메시지가 순서대로 처리된다. 멈추기 전 좌표가 `RUNNING_PAUSE` 뒤에 도착하면 처음 보는 좌표라 멈춤이 바로 풀린다
  - **재연결 시 멈춤 중이었으면** `RUNNING_START` → 로컬 트랙 재전송 → `RUNNING_PAUSE` 순으로 보낸다(5-C)
  - **멈춘 동안에도 `HEALTH_CHECK`를 보낸다.** 좌표가 끊겨 유휴 타임아웃(운영값)을 넘기면 서버가 연결을 닫고 `DISCONNECTED`로 알린다
- **ack 없음.** `RUNNING_START` 전에 보내면 `RUNNING_NOT_STARTED`로 거부한다. 그 밖의 실패(상태 저장소 장애)는 화면 표시용이라 `ERROR`로 알리지 않고 서버 로그만 남긴다 — 다음 상태 변경이나 재연결 스냅샷이 바로잡는다

#### `RUNNING_FINISH` (C→S) — 러닝 종료 (정상/강제 통합)

```json
{
  "forced": false
}
```

- `forced`는 필수다 — 비우면 `INVALID_REQUEST`로 거부한다
- **클라는 보내기 전에 남은 좌표를 먼저 보낸다.** 한 연결 안에서는 메시지가 순서대로 처리된다. 종료는 마지막 수신 좌표까지로 계산하므로, 다음 배치를 기다리던 좌표(최대 10초 분량)를 보내지 않으면 그만큼 기록에서 빠진다 — 4,980m에서 배치를 보내고 5,010m에서 종료를 누르면(`forced=false`) 서버는 4,980m까지만 보고 확정하지 않은 채 `RUNNING_GOAL_PENDING`(`remainingMeters: 20`)을 보낸다. `RUNNING_TRACK_UNAVAILABLE`로 다시 보내야 할 트랙이 남아 있으면 그것도 먼저 보낸다
  - 남은 좌표로 확정 거리가 목표를 채우면 그 배치에서 자동 종료가 먼저 확정되고, 뒤이은 `RUNNING_FINISH`는 아래 멱등 규칙대로 `RUNNING_FINISHED`만 다시 받는다
- **이 메시지는 사용자가 종료를 길게 눌렀을 때만 보낸다.** 화면 거리가 목표 이상이면 `forced=false`(다 뛰었다), 미만이면 `forced=true`(조기 종료를 선택했다)다
- **`forced=false`인데 확정 거리가 목표에 못 미치면 확정하지 않는다.** `RUNNING_FINISHED` 대신 `RUNNING_GOAL_PENDING`(남은 거리)을 보내고 러닝은 계속된다 — 화면으로는 다 뛰었는데 서버가 오염 좌표를 빼 인정하지 않은 경우라, 그대로 확정하면 억울한 조기 종료가 된다. 사용자가 그래도 끝내려면 `forced=true`로 다시 보낸다
  - 확정하지 않으므로 상태·기록·트랙 버퍼 모두 그대로이고, 이 요청의 ack(`RUNNING_FINISHED`)도 나가지 않는다
- `forced`는 사용자가 조기 종료를 선택했는지 나타낼 뿐 최종 상태를 결정하지 않는다. 서버가 확정한 거리가 목표 이상이면 `COMPLETED`, 미달이면 `totalDistanceMeters / targetDistanceMeters`를 운영 설정 비율과 비교해 이상은 `RUNNING_LEFT_NO_PENALTY`, 미만은 `RUNNING_LEFT_PENALTY`로 전환한다. 다만 미만이어도 방 인원(확정 인원에서 취소·탈퇴한 미출석자를 뺀 수)이 1이거나 초대 방이면 `_NO_PENALTY`다. 목표가 없는 솔로 방은 끝낸 것이 곧 완주라 `COMPLETED`다
- 종료 시각을 `deleted_at`에 기록한다 — `COMPLETED`·`RUNNING_LEFT_*` 공통이다. 비우면 활성 신청으로 남아 다음 매칭을 신청할 수 없다
- 종료 신호나 강제 종료에 마지막 수신 데이터로 거리·페이스·구간·칼로리·고도 지표를 계산한다. 계산 전에 트랙 필터가 일시정지 경계(`pauseSegment`가 바뀐 칸)·정지·GPS 튐·관측 안 된 칸을 거리·시간에서 뺀다([feature-spec.md](feature-spec.md) 트랙 필터) — 그래서 `totalDurationSeconds`는 움직인 시간이다. 칼로리는 확정 거리·시간과 사용자 체중으로, 고도는 노이즈를 필터링한 기기 GPS 고도로 계산한다
- 유효 러닝 판정(거리·시간·경로 산출 가능 + 평균 페이스 허용 범위 + 최소 거리·최소 시간, 운영값)을 통과한 트랙이면 `running_records`·경로 조각(`running_segments`)·splits를 저장하고 GPS 트랙을 S3에 올린다. 통과하지 못하면 실제 거리를 0으로 판정하고 기록 없이 상태만 확정한다
- **목표 거리를 넘겨 뛰면 목표 지점에서 끊어 기록한다.** 목표를 사이에 둔 두 좌표에서 비율로 위치·시각을 보간해 그 지점을 기록의 끝으로 삼고, `totalDistanceMeters`·`endAt`·`totalDurationSeconds`를 모두 그 기준으로 확정한다 — 거리만 자르면 페이스가 실제보다 빨라진다. 목표 이후 좌표는 기록 계산에서만 빠지고 **S3 원본 트랙에는 그대로 남는다**. 목표 미달로 끝났으면 마지막 10m 경계까지로 확정한다(10m 미만 꼬리는 버린다)
- **구간은 목표 거리를 10m로 나눈 고정 경계다**(0-10, 10-20…). 참가자별 실제 거리로 나누지 않으므로 같은 방 참가자의 `splitNumber` N은 언제나 같은 거리 구간을 가리킨다. 경계가 정확히 10m가 되도록 그 지점도 보간해 만든다
- **ack**: `RUNNING_FINISHED` — 수신 후 클라는 REST `GET /running-rooms/{id}/results`로 대시보드 진입. 확정하지 못한 `forced=false`는 ack 대신 `RUNNING_GOAL_PENDING`을 받는다
  - 목표가 있는 방은 이 요청 없이도 받을 수 있다 — 좌표 배치로 목표를 채우면 서버가 먼저 끝낸다(`RUNNING_LOCATION_UPDATE` 절)
- `RUNNING_FINISH`는 멱등이다. 강제 종료·목표 도달 자동 종료나 이전 요청으로 이미 확정됐으면 기록을 덮어쓰지 않고 `RUNNING_FINISHED`를 다시 보내 로컬 트랙을 정리하게 한다
- `RUNNING`이 된 참가자가 모두 종료 상태가 되고 기록 확정이 끝나면 방을 닫는다 — 유효 기록이 하나라도 있으면 `FINISHED`, 없으면 `CANCELLED`다. 강제 종료 시각(`start_at + 유예`)에는 한 번도 붙지 않아 `JOINED`로 남은 참가자를 확정 후 이탈로 먼저 닫고, 뛰던 참가자를 같은 규칙으로 종료 처리한다. 뛴 참가자가 모두 끝나 방이 먼저 닫혔는데 `JOINED`인 참가자가 남았으면 예약 시각을 기다리지 않고 방을 닫은 직후 같은 기준으로 닫는다
- **부수효과 — 기록을 확정할 때 시작 좌표가 외부로 나간다.** 서버가 날씨를 조회하려고 `api.open-meteo.com`(`OpenMeteo GmbH`, 스위스)에 위도·경도와 시각을 보낸다. 개인위치정보의 국외 이전이라 개인정보처리방침 고지 대상이고 Google Play 데이터 보안에는 `공유됨`으로, App Store 개인정보 라벨에는 `정확한 위치`로 신고한다. 상세는 `feature-spec.md`의 날씨 절에 있다

## 6. 러닝 중 / 러닝 후 대시보드 (REST)

> **러닝 사진**: 앱에서 촬영해 디바이스 갤러리에만 저장 — 서버 업로드/조회 API 없음. results 등 응답에 사진 필드 없음.

### 6-1. `GET /api/v1/running-rooms/{runningRoomId}/results` — 러닝 결과 (참가자 전원 요약)

- **화면**: 러닝 후 - 대시보드 (참가자 공통 정보). `RUNNING_FINISHED` 수신 후 진입
- **Request**: 본문 없음

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `runningRoomId` | Integer | O | 조회할 러닝 방 ID (path) |

- **조회자는 토큰에서만 온다** — 클라가 사용자 식별자를 보내지 않는다
- 방이 아직 `STARTED`면 종료하지 않은 참가자는 `status='RUNNING'`, 기록 지표는 null인 현재 스냅샷을 반환한다. 다시 조회하면 최신 상태를 받고 방이 `FINISHED`면 최종 결과가 된다
- **Response `200 OK`**

```json
{
  "runningRoomId": 125,
  "startedAt": "2026-07-25T19:00:30",   // 현재 사용자 기록 기준, 기록 없으면 null
  "finishedAt": "2026-07-25T19:30:30",  // 현재 사용자 기록 기준, 기록 없으면 null
  "routes": [                             // 현재 사용자 경로 — 끊긴 곳마다 조각 하나. 본인 기록이 없으면 null
    [                                     // 조각 1 [위도, 경도]
      [35.1795543, 129.0756416],
      [35.1796012, 129.0757104]
    ],
    [                                     // 조각 2 — 일시정지 뒤 재개
      [35.1801230, 129.0762011],
      [35.1801874, 129.0762650]
    ]
  ],
  "players": [
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440015",
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
      "userId": "01a0f2d4-6180-7000-8000-446655440031",
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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `runningRoomId` | Integer | O | 러닝 방 ID |
| `startedAt` | String | X | 현재 사용자 기록의 시작 시각. 기록이 없으면 null |
| `finishedAt` | String | X | 현재 사용자 기록의 종료 시각. 기록이 없으면 null |
| `routes` | Number[][][] | X | 현재 사용자 경로. 조각 배열이고, 조각마다 `[[위도, 경도], …]`를 달린 순서대로 담는다. 조각도 달린 순서다. 가장 안쪽 배열은 길이 2, 위도가 먼저다(GeoJSON과 반대). 좌표는 소수점 5자리(약 1m)까지. 첫 조각의 첫 원소가 시작 지점, 끝 조각의 끝 원소가 종료 지점이라 마커용 좌표를 따로 싣지 않는다. 본인 기록이 없으면 null(빈 배열이 아니다) |
| `players` | Array | O | 러닝 단계에 들어간 참가자 전원의 결과 |

**`players[]`**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `userId` | String | O | 참가자 ID |
| `nickname` | String | O | 닉네임. 탈퇴한 사용자는 `탈퇴한 사용자` |
| `profileImageUrl` | String | X | 프로필 이미지 URL. 사진이 없거나 탈퇴한 사용자면 null |
| `status` | String | O | 참가자 상태 — `RUNNING`(진행 중)·`COMPLETED`(종료) 두 값뿐. 중도 이탈도 종료 뒤에는 `COMPLETED`다 |
| `isDeleted` | Boolean | O | 탈퇴한 사용자 여부 |
| `isMe` | Boolean | O | 현재 사용자 여부 |
| `totalDistanceMeters` | Integer | X | 총 이동 거리(m). 기록이 없으면 null |
| `totalDurationSeconds` | Integer | X | 총 러닝 시간(초) — **움직인 시간**이다. 트랙 필터가 일시정지·정지·관측하지 못한 구간을 빼므로 그 기록의 시작~종료 시각 차보다 짧을 수 있다. 기록이 없으면 null |
| `totalCaloriesKcal` | Integer | X | 총 소모 칼로리(kcal). 기록이 없으면 null |
| `averagePaceSecondsPerKm` | Integer | X | 평균 페이스(초/km). 기록이 없으면 null |
| `averageCadenceSpm` | Integer | X | 평균 케이던스(spm). 기록이 없거나 유효 표본이 부족하면 null |
| `totalElevationGainMeters` | Integer | X | 누적 상승 고도(m). 기록이 없거나 유효 표본이 부족하면 null. 일시정지 중에 오른 높이는 들어가지 않는다 |

- **`status`는 `COMPLETED`·`RUNNING` 두 값뿐이다** — 러닝을 끝낸 사람은 완주든 중도이탈이든 `COMPLETED`, 아직 뛰는 중이면 `RUNNING`이다. DB의 `running_players.status`(`RUNNING_LEFT_PENALTY` 등, `erd.md` §6)를 그대로 노출하지 않는다: **페널티 여부는 본인 매칭 쿨다운 판정에 쓰는 내부 값이라 남의 화면에 실을 이유가 없다.** 얼마나 뛰었는지는 `totalDistanceMeters`로 드러난다
- `players`에는 방에서 러닝 단계에 들어간 참가자 전원을 유지하고 **시작 전 이탈자는 제외한다**. 기록이 없는 참가자는 사용자 정보와 `status`만 채워 내려가므로 화면에 "기록 없음"으로 표시한다
- 탈퇴한 참가자는 공통 탈퇴 유저 형식으로 표시하고 `isDeleted=true`로 반환한다

- **`startedAt`·`finishedAt`·`routes`는 본인 기록 기준이다**(`running_records.start_at`/`end_at`, `running_segments.route_polyline`). 본인 기록이 없으면 null이며 6-2의 최상위 필드도 같은 기준이다
- **`routes`는 서버가 조각별 폴리라인을 풀어서 내린다** — 저장은 `running_segments.route_polyline`(조각마다 encoded polyline)이지만 응답은 조각 번호 순의 좌표 배열이다. 클라가 디코더를 붙일 필요도, 6-2를 기다릴 필요도 없이 진입 즉시 지도를 그린다. 좌표 정밀도는 폴리라인을 따라 소수점 5자리(약 1m)이며 그보다 정밀한 값은 존재하지 않는다. **러닝 결과(6-1·6-2) 중 전체 경로를 한 번에 주는 곳은 여기뿐이다** — 6-2의 `routes`는 같은 경로를 구간별로 자른 것이다
- **조각은 서버가 기록에서 뺀 곳에서만 나뉜다** — 일시정지 경계, 관측하지 못한 이동, GPS 튐. 클라는 조각 사이를 선으로 잇지 않고, 30m 같은 간격 규칙으로 따로 판단하지 않는다(feature-spec 트랙 필터)
- **지도 마커용 시작·끝 좌표는 따로 싣지 않는다** — 첫 조각의 첫 원소와 끝 조각의 끝 원소가 그대로 시작·끝 지점이다
- **피드 카드(8-1)는 `routePolyline`을 유지한다** — 한 응답에 기록이 여러 건이라 좌표 배열로 바꾸면 응답 크기가 건수만큼 곱해진다. 서버가 조각을 이어 한 줄로 다시 인코딩해 내린다. 좌표 배열은 기록 하나를 크게 그리는 화면(6-1·6-2)에만 쓴다. 기록 목록(7-1)은 경로를 싣지 않는다

- **에러 (404 Not Found)**: `runningRoomId`에 해당하는 방이 없다

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (403 Forbidden)**: 방은 있지만 러닝 단계에 들어간 참가자가 아니다

```json
{
  "code": "NOT_ROOM_PLAYER",
  "message": "이 방의 참가자가 아닙니다."
}
```

- **방 존재 여부를 먼저 본다** — 없으면 404, 있는데 참가자가 아니면 403이다. 순서를 뒤집으면 없는 방에 403이 나가면서 방의 존재 여부가 새어 나간다
- **인증**: 필요 (같은 방 참가자)

### 6-2. `GET /api/v1/running-rooms/{runningRoomId}/split-results` — 구간별 상세 + 경로

- **화면**: 러닝 후 - 대시보드 (본인 경로 확인 + 참가자 상세·구간별 비교)
- **Request**: 본문 없음

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `runningRoomId` | Integer | O | 조회할 러닝 방 ID (path) |

- **조회자는 토큰에서만 온다** — 클라가 사용자 식별자를 보내지 않는다
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
      "userId": "01a0f2d4-6180-7000-8000-446655440015",
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
          "userId": "01a0f2d4-6180-7000-8000-446655440015",  // 최상위 players와 조인
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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `runningRoomId` | Integer | O | 러닝 방 ID |
| `splitDistanceMeters` | Integer | O | 고정 구간 거리(m). 항상 10 |
| `totalDistanceMeters` | Integer | X | 현재 사용자 총 거리(m). 목표를 넘겨 뛰었으면 목표 지점에서, 목표 미달이면 마지막 10m 경계에서 끊은 값이다. 기록이 없으면 null |
| `totalElevationGainMeters` | Integer | X | 현재 사용자 누적 상승 고도(m). 기록이 없거나 유효 표본이 부족하면 null. 일시정지 중에 오른 높이는 들어가지 않는다 |
| `startedAt` | String | X | 현재 사용자 기록 시작 시각. 기록이 없으면 null |
| `finishedAt` | String | X | 현재 사용자 기록 종료 시각. 기록이 없으면 null |
| `players` | Array | O | **기록이 있는** 참가자 목록. 참가자 메타데이터는 여기에만 담는다 |
| `splits` | Array | O | 구간별 참가자 기록. 구간 번호 오름차순. 본인 기록이 없어도 다른 참가자의 행은 유지된다 |

**`players[]`**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `userId` | String | O | 참가자 ID |
| `nickname` | String | O | 닉네임. 탈퇴한 사용자는 `탈퇴한 사용자` |
| `profileImageUrl` | String | X | 프로필 이미지 URL. 사진이 없거나 탈퇴한 사용자면 null |
| `status` | String | O | 참가자 상태 — `RUNNING`·`COMPLETED` 두 값뿐 (6-1과 같은 규칙) |
| `isDeleted` | Boolean | O | 탈퇴한 사용자 여부 |
| `isMe` | Boolean | O | 현재 사용자 여부 |

**`splits[]`**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `splitNumber` | Integer | O | 1부터 시작하는 구간 번호 |
| `startDistanceMeters` | Integer | O | 구간 시작 누적 거리(m) |
| `endDistanceMeters` | Integer | O | 구간 종료 누적 거리(m) |
| `distanceMeters` | Integer | O | 구간 거리(m). 고정 10m — 마지막 자투리 구간은 생기지 않는다 |
| `routes` | Number[][] | X | 이 구간의 **본인** 경로. `[[위도, 경도], …]` 형식이며 구간이 속한 조각 하나에서 자른 것이다. 같은 조각 안에서는 구간 N의 끝 원소가 구간 N+1의 첫 원소와 같다. 끊긴 곳에 걸친 구간은 원소가 하나다. 본인 기록이 없거나 본인이 이 구간에 도달하지 못했으면 null |
| `players` | Array | O | 이 구간 기록이 있는 참가자 목록 |

**`splits[].players[]`**

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `userId` | String | O | 참가자 ID. 최상위 `players`와 조인한다 |
| `durationSeconds` | Integer | O | 구간 소요 시간(초) — 움직인 시간이다. 트랙 필터가 뺀 일시정지·정지·관측하지 못한 시간은 들어가지 않는다 |
| `averagePaceSecondsPerKm` | Integer | O | 구간 평균 페이스(초/km) |
| `averageCadenceSpm` | Integer | X | 구간 평균 케이던스(spm). 유효 표본이 부족하면 null |
| `caloriesKcal` | Integer | O | 구간 소모 칼로리(kcal) |
| `elevationChangeMeters` | Integer | X | 구간 순고도차(m). 끝 − 시작이며, 일시정지로 끊긴 구간은 조각마다의 순고도차를 더한다(일시정지 중 오르내린 높이는 빠진다). 내리막은 음수이며 유효 표본이 부족하면 null — 10m 구간에서는 대체로 null이다 |

- **참가자 메타데이터는 최상위 `players`에 한 번만 싣고, `splits[].players`에는 `userId`와 수치만 둔다.** 구간이 목표 5,000m 기준 500개라 `nickname`·`profileImageUrl`을 구간마다 반복하면 응답이 MB 단위가 된다(presigned URL만 500×인원×수백 바이트). 클라는 `userId`로 조인한다
- **구간 경계는 방 전체가 공유한다** — 0m부터 10m씩 자르는 고정 경계라, `splitNumber` N은 모든 참가자에게 같은 거리 구간이다. 목표에 못 미치고 끝난 참가자는 도달하지 못한 구간의 `players`에서 빠진다
- **최상위 `players`는 6-1과 달리 기록이 있는 참가자만이다** — 구간은 기록(`running_records`)에 딸린 행이라, 기록이 없으면 보여줄 구간 자체가 없다. 그런 참가자는 `splits[].players`와 최상위 `players` 양쪽에서 빠진다. 6-1은 기록이 없어도 목록에 남기고 지표만 null로 내리므로 **두 API의 참가자 수가 다를 수 있다**. 탈퇴한 참가자는 공통 탈퇴 유저 형식과 `isDeleted=true`로 표시한다
- **10m 구간의 `elevationChangeMeters`는 대체로 null이다** — GPS 수직 오차가 구간 길이에 맞먹어 노이즈 임계값을 넘는 표본이 거의 없다
- 조회하는 본인의 기록이 없어도 **다른 참가자의 구간 기록은 그대로 내려간다** — 본인 기록이 없어도 다른 참가자의 결과는 보이는 6-1과 같은 규칙이다
- **경로는 최상위가 아니라 구간마다 실린다.** 이 화면은 구간별로 색을 달리해 그리므로 자른 조각이 곧 그리는 단위다. 전체 경로 하나가 필요하면 6-1의 `routes`를 쓴다 — 같은 값을 두 응답에 중복해 싣지 않는다
- **`splits[].routes`는 조회하는 본인의 경로다.** 같은 객체의 `players`가 참가자 전원인 것과 다르다 — `running_splits.route_start_index`·`route_end_index`가 각자 자기 조각(`running_segments.route_polyline`)의 위치를 가리키므로 남의 구간 좌표는 이 배열에 섞이지 않는다
- **이어붙일 때 경계점이 겹친다.** 같은 조각 안에서 N번 구간의 끝 원소와 N+1번의 첫 원소는 같은 점이다 — 전체 경로를 만들려면 두 번째 구간부터 첫 원소를 건너뛴다. 전체 경로가 필요하면 6-1을 쓰는 편이 낫다
- **끊긴 곳에 걸친 구간은 점 하나만 실린다.** 일시정지 직전 경계점은 앞 조각에, 재개 뒤 첫 경계점은 뒤 조각에 있다. 구간은 끝점이 있는 뒤 조각에 속하고 그 조각의 첫 점 하나만 가리킨다 — 선을 긋지 않는다. 거리·시간·페이스는 다른 구간과 같다
- 경로는 구간이 속한 조각의 `running_segments.route_polyline`에서 구간 범위로 잘라 내린다 — 서버가 풀어 내리므로 S3를 조회하지 않는다. 저장된 좌표 컬럼은 없다
- 점별 고도·정확도·순간 페이스·케이던스·시각은 반환하지 않고 구간 단위 값만 제공한다.
- **고도는 두 층위가 서로 다른 값이다** — 최상위 `totalElevationGainMeters`는 **누적 상승**(올라간 것만 합산, `running_records.total_elevation_gain`), 구간의 `elevationChangeMeters`는 **순고도차**(끝 − 시작, 일시정지로 끊긴 구간은 조각별 순고도차의 합, `running_splits.elevation_change`)다. **구간값을 더해도 최상위 값이 되지 않는다** — 계산 기준이 다르다(`erd.md` 러닝 기록 절)

- **에러 (404 Not Found)**: `runningRoomId`에 해당하는 방이 없다

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **에러 (403 Forbidden)**: 방은 있지만 러닝 단계에 들어간 참가자가 아니다

```json
{
  "code": "NOT_ROOM_PLAYER",
  "message": "이 방의 참가자가 아닙니다."
}
```

- **방 존재 여부를 먼저 본다** — 6-1과 같은 순서다
- **인증**: 필요 (같은 방 참가자)

## 7. 기록 화면

### 7-1. `GET /api/v1/users/me/running-records` — 내 러닝 기록 목록

- **화면**: 기록(주간 요약·주간 차트·기록 캘린더·날짜별 카드)
- **Query**: `from`·`to`(`YYYY-MM-DD`) 둘 다 필수다. 양 끝을 포함한 기간의 기록을 한 번에 받으며 기간은 최대 31일이다 — 예: 이번 주는 `?from=2026-09-27&to=2026-10-03`, 한 달은 `?from=2026-09-01&to=2026-09-30`, 하루는 `from`과 `to`를 같은 날로 보낸다
- 날짜는 `running_records.start_at`의 KST 달력 날짜 기준이다. **자정을 넘긴 러닝은 시작한 날에 속한다.** 미래 날짜도 받는다 — 이번 주를 조회하면 아직 오지 않은 요일이 들어간다
- 정렬은 `startedAt` 오름차순이다
- **Response `200 OK`**

```json
{
  "runningRecords": [
    {
      "runningRecordId": 501,
      "runningRoomId": 125,           // 항상 값이 있다
      "type": "MATCH",                // MATCH | SOLO | INVITE
      "playerCount": 3,               // 이 러닝을 시작한 인원(본인 포함)
      "startedAt": "2026-07-25T19:00:30",
      "totalDistanceMeters": 5020,
      "totalDurationSeconds": 1800,
      "averagePaceSecondsPerKm": 359,
      "totalElevationGainMeters": 42  // 유효 표본이 부족하면 null
    }
  ]
}
```

- 기간에 기록이 없으면 `runningRecords`는 빈 배열이다
- **숨긴 방(`running_rooms.deleted_at` 있음)의 기록은 빠진다** — 6-1·6-2가 그 방을 404로 보는 것과 맞춘다. 숨김은 [MVP 제외]라 지금은 해당하는 방이 없다
- **페이지를 나누지 않는다** — 한 번에 받는 범위가 31일로 묶여 있어 커서를 두지 않는다
- **`type`은 방 종류다** — `SOLO`는 솔로 러닝, `MATCH`는 매칭 러닝, `INVITE`는 초대 러닝이다
- **`playerCount`는 6-1 결과의 `players` 수와 같다** — 러닝 단계에 들어간 참가자(`RUNNING`·`RUNNING_LEFT_*`·`COMPLETED`)를 본인과 탈퇴자까지 포함해 센다. 확정됐지만 나타나지 않은 참가자는 세지 않으므로 `running_rooms.current_player_count`와 다를 수 있다. `MATCH`인데 `1`이면 혼자 뛴 매칭 러닝이다
- **`totalElevationGainMeters`는 6-1의 같은 필드와 같은 값이다** — 누적 상승 고도(m)이며 유효 표본이 부족하면 null이다. 주간 요약의 누적 경사는 클라이언트가 합산한다
- **동행자 닉네임·프로필은 싣지 않는다** — 필요하면 `runningRoomId`로 6-1을 조회한다
- **경로는 싣지 않는다** — 카드에 경로 미리보기를 띄우지 않는다. 경로는 기록 상세(6-1·6-2)에서만 본다
- **기록 상세는 별도 API 없이 `runningRoomId`로 6-1·6-2를 조회한다** — 러닝 직후 결과 화면과 같은 화면이다

- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "조회 시작일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "조회 종료일은 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "조회 시작일은 종료일보다 늦을 수 없습니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "조회 기간은 31일 이하여야 합니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "입력값이 올바르지 않습니다."
}
```

- 날짜가 `YYYY-MM-DD` 형식이 아니거나 없는 날짜(`2026-02-30`)면 마지막 기본 문구다. 형식 오류가 난 날짜는 순서·기간 검사를 하지 않는다
- **인증**: 필요 (본인 기록만)

## 8. 피드 목록 페이지 (+댓글 모달) [MVP 제외]

**피드 카드 공통 객체** (8-1/8-2/9-2 응답):

```json
{
  "feedId": 77,
  "author": {
    "userId": "01a0f2d4-6180-7000-8000-446655440015",
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
    "routePolyline": "u{~vFvyys@fS]pT_@..."   // 10m 경계점 경로(encoded polyline) — 카드 지도 미리보기. running_segments.route_polyline을 조각 순으로 이어 다시 인코딩
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
        "userId": "01a0f2d4-6180-7000-8000-446655440013",
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
- **Response `200 OK`**

```json
{
  "userId": "01a0f2d4-6180-7000-8000-446655440015",
  "email": "run@example.com",
  "loginType": "GOOGLE",               // LOCAL | GOOGLE | KAKAO | APPLE
  "nickname": "동완러너",                // 온보딩 전이면 null
  "isOnboarded": true
}
```

- **계정 정보를 함께 싣는다** — 이메일과 로그인 수단을 쓰는 곳은 설정 화면뿐이지만, 설정에서 따로 호출하지 않고 앱 진입 때 받아 둔 값을 그대로 쓴다. 어차피 매번 타는 경로라 여기 얹으면 설정 진입에 추가 왕복이 없다
- **`loginType` 판정**: `oauth_users`에 row가 있으면 그 `provider`, 없으면 `LOCAL`. `users.password_hash`의 null 여부로 판정하지 않는다 — 결과는 같지만 "무슨 계정인가"에 직접 답하는 데이터는 `oauth_users`다
- **계정은 로컬·소셜 중 하나로 배타적이다** — 소셜 최초 가입 시 이메일이 기존 계정과 겹치면 `409`로 거부하므로(1-5~1-7) 단일 값으로 표현된다
- **클라 표시 규칙**: `LOCAL`이면 로그인 수단 문구 없이 "비밀번호 변경" 메뉴를 노출하고, 소셜이면 "구글/카카오/애플 계정으로 로그인 중"을 표시하고 메뉴를 감춘다(근거는 `feature-spec.md` 설정 페이지 절)
- **소개글은 싣지 않는다** — 프로필 화면에서만 쓰고 10-2가 담당한다
- **`profileImageUrl`을 내리지 않는 이유** — 11-3이 전용 조회를 제공하고, presigned URL은 TTL이 있어 표시 시점에 다시 받아야 한다. 앱 진입마다 발급하면 쓰이지 않을 URL에 S3 호출만 늘어난다
- **`userId`는 남긴다** — `{userId}` 경로 API(10-2·10-4 등)에서 본인 여부를 가리는 데 쓴다. 액세스 토큰에도 들어 있지만 클라이언트가 토큰을 파싱하게 만들지 않는다
- **인증**: 필요

### 10-2. `GET /api/v1/users/{userId}` — 프로필 요약

- **화면**: 프로필 (본인/타인 공통 — 본인이면 편집·설정 버튼, 타인이면 친구 요청 버튼 노출은 `isMe`로 분기)
- **Response `200 OK`**

```json
{
  "userId": "01a0f2d4-6180-7000-8000-446655440015",
  "isMe": false,
  "nickname": "동완러너",              // 온보딩 전이면 null
  "profileImageUrl": "https://...",
  "introduction": "즐겁게 달려요",
  "friendCount": 42,                   // user_relationships에서 COUNT (type=FRIEND)
  "relationshipType": "FRIEND"         // NONE | FRIEND_REQUEST_SENT | FRIEND_REQUEST_RECEIVED | FRIEND
}
```

- **러닝 통계(마일리지·최고 페이스·러닝 횟수)는 싣지 않는다** — FE와 합의해 프로필 화면에서 제외했다. 다시 필요해지면 유효 러닝 판정(`feature-spec.md`)을 거친 집계로 추가한다
- `relationshipType`으로 버튼을 가른다(10-6 표). 본인 프로필(`isMe=true`)이면 `null`이다
- **배경 사진은 싣지 않는다** — 11-7이 따로 내린다(프로필 사진과 같은 이유로 presigned URL을 표시 시점에 받는다)
- 친구 목록(10-7)·업적(10-10)은 각자의 API로 받는다

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
  "relationshipType": "FRIEND_REQUEST_SENT"
}
```

- `user_relationships`에 `(요청자→대상, FRIEND_REQUEST_SENT)`·`(대상→요청자, FRIEND_REQUEST_RECEIVED)` 두 행을 쓰고(이전 관계로 `NONE` 행이 남아 있으면 그 행을 갱신한다) 대상에게 "친구 요청 도착" 인앱 알림(`FRIEND_REQUESTED`, 2-4)을 쌓고 푸시를 보낸다
- **이미 상대가 나에게 요청해 둔 상태면(내 행이 `FRIEND_REQUEST_RECEIVED`) 새 요청을 만들지 않고 수락으로 처리한다.** 서로 요청을 주고받은 상황은 이미 합의된 것이라 한 번 더 수락을 요구할 이유가 없다. 이때 응답은 10-5 수락과 같은 `200 OK` `{ "relationshipType": "FRIEND" }`이고(요청을 새로 만들지 않으므로 `201`이 아니다), 역방향 요청자에게 `FRIEND_ACCEPTED` 알림을 쌓는다
  - **동시에 서로 요청해도 같다** — 두 요청 중 뒤에 처리되는 쪽이 앞쪽이 만든 요청을 받은 상태로 보고 수락한다. 둘 다 `FRIEND_REQUEST_SENT`로 끝나는 일은 없다
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

### 10-5. `PUT /api/v1/users/{userId}/friend-request` — 요청 수락

- **경로의 `{userId}`는 요청을 보낸 사람이다.** 그 사람이 나에게 보낸 요청의 두 행(`FRIEND_REQUEST_SENT`·`FRIEND_REQUEST_RECEIVED`)을 `FRIEND`로 바꾼다 — 요청 리소스를 친구로 확정된 상태로 갈아끼우는 것이라 `PUT`이다
- **Request 본문이 없다** — 수락 말고 바꿀 수 있는 값이 없다. `PATCH`로 상태 값을 받으면 `FRIEND_REQUEST_SENT` 같은 의미 없는 값까지 검증해야 한다
- 요청자에게 "친구 요청 수락됨" 인앱 알림(`FRIEND_ACCEPTED`)을 쌓고 푸시를 보낸다
- **멱등이다** — 이미 그 사람과 친구(`FRIEND`)면 아무것도 바꾸지 않고 같은 응답을 준다. 알림도 다시 쌓지 않는다. 응답을 못 받은 앱이 다시 보내도 에러가 나지 않는다
- **Response `200 OK`**: `{ "relationshipType": "FRIEND" }`
- **에러 (404 Not Found)**: 그 사람이 나에게 보낸 요청이 없다 — 내가 보낸 요청을 내가 수락하려는 경우도 여기다
- **인증**: 필요

### 10-6. `DELETE /api/v1/users/{userId}/friend-request` — 요청 취소 · 거절 / `DELETE /api/v1/users/{userId}/friend` — 친구 삭제

- **요청 취소·거절** (`DELETE .../friend-request`)
  - **호출자가 보낸 쪽이면 취소, 받은 쪽이면 거절이다.** 이름만 다를 뿐 하는 일은 같아서(양쪽 `FRIEND_REQUEST_*` 행을 `NONE`으로) 하나로 둔다
  - 행은 지우지 않는다. 취소인지 거절인지는 애플리케이션 로그로 남는다
  - **이미 친구(`FRIEND`)여도 친구 삭제로 처리한다.** 요청을 보낸 뒤 상대가 수락하는 찰나에 취소를 누르면, 화면은 아직 "요청 취소"인데 관계는 이미 친구가 되어 있다. 취소하려던 뜻대로 관계를 `NONE`으로 되돌리고 `204`를 준다. 성립한 관계를 지우는 정식 경로는 여전히 `DELETE .../friend`다
  - **Response `204 No Content`**
  - **에러 (404 Not Found)**: 둘 사이에 대기 중인 요청(`FRIEND_REQUEST_*`)도 친구 관계도 없다
- **친구 삭제** (`DELETE .../friend`): **양쪽 누구나 호출할 수 있다** — 성립한 뒤로는 방향에 의미가 없다. 두 행을 `NONE`으로 바꾼다
  - 성립 전 요청과 리소스를 나눈다 — 요청은 `friend-request`, 성립한 관계는 `friend`다
  - **Response `204 No Content`**
  - **에러 (404 Not Found)**: 친구가 아니다
- **인증**: 필요

> | `relationshipType` | 화면 버튼 | 호출 |
> |---|---|---|
> | `NONE` | 친구 요청 | `POST .../friend-request` |
> | `FRIEND_REQUEST_SENT` | 요청 취소 | `DELETE .../friend-request` |
> | `FRIEND_REQUEST_RECEIVED` | 수락 / 거절 | `PUT .../friend-request` / `DELETE .../friend-request` |
> | `FRIEND` | 친구 삭제 | `DELETE .../friend` |

### 10-7. `GET /api/v1/users/{userId}/friends` — 친구 목록

- **화면**: 친구 목록 페이지(본인 — 이 목록 + 추천 친구 10-11) / 타인 프로필의 친구 목록
- **본인과 타인이 보는 범위가 다른 하나의 리소스라 `{userId}` 경로에 인가 규칙을 둔다**(`api-convention.md` 사용자 리소스 경로). 10-3 피드 그리드와 같은 구조다

| 조회자 | 담기는 항목 |
|---|---|
| 본인(`userId`가 토큰 주체) | 확정(`FRIEND`) · 보낸 요청(`FRIEND_REQUEST_SENT`) · 받은 요청(`FRIEND_REQUEST_RECEIVED`) |
| 타인 | 확정(`FRIEND`)만 — 누구에게 요청했고 누가 요청했는지는 본인만 안다 |
| 차단 관계 | 차단 구현 때 정한다 — 목록을 열지 않는 방향이다(10-8의 `403 PROFILE_NOT_VISIBLE` 자리) |

- `profile_visibility`로 가리지 않는다
- **Query**: `q`(이름 필터), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl", "relationshipType" } ], "nextCursor": "..." }`
- **`relationshipType`은 조회하는 사람 기준이다**(§0)
  - 본인 목록: 항목의 상태 그대로 — `FRIEND_REQUEST_RECEIVED`면 수락(10-5)·거절(10-6), `FRIEND_REQUEST_SENT`면 취소(10-6), `FRIEND`면 삭제(10-6)
  - 타인 목록: 나와 그 사람의 관계 — 타인의 목록에서 바로 친구 요청 버튼을 그린다. 목록에 조회자 본인이 있으면 그 항목은 `null`이다
- **정렬**: 받은 요청 → 보낸 요청 → 확정 순으로 묶고, 묶음 안에서는 관계가 바뀐 시각(`updated_at`)의 최근순이다 — 친구는 최근에 맺은 순, 요청은 최근에 오간 순이다. 시각이 같으면 `userId` 역순이다. 닉네임은 바뀔 수 있어 정렬 키로 쓰지 않는다. 커서는 묶음과 그 안의 위치를 함께 담는다 — 묶음 경계에서 페이지가 끊겨도 이어진다
  - 페이지 사이에 요청이 친구로 바뀐 항목은 친구 묶음에 한 번 더 내려올 수 있다 — 클라는 `userId`로 중복을 덮어쓴다(뒤에 온 항목이 최신 상태다)
- `user_relationships`에서 `user_id`가 대상인 행이다(`NONE`·`BLOCKED`는 담지 않는다) — 본인이면 `type`이 `FRIEND`·`FRIEND_REQUEST_SENT`·`FRIEND_REQUEST_RECEIVED`, 타인이면 `FRIEND`만. 행의 `type`이 곧 본인 목록의 `relationshipType`이다
- **10-2 `friendCount`는 확정만 센다** — 본인 목록의 항목 수와 다를 수 있다
- **에러 (404 Not Found)**: 대상이 없다(탈퇴 포함)
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
- **Query**: `q`(필수, 닉네임 — 앞뒤 공백은 걷는다, 최대 16자), `cursor`/`limit`
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl", "relationshipType" } ], "nextCursor": "..." }`
- **대상은 온보딩을 마친 전체 사용자다** — 친구 여부와 상관없이 찾는다. 10-7의 이름 필터는 내 관계 안에서만 거른다는 점이 다르다
  - **조회자 본인은 담지 않는다** — 자기 자신에게는 친구 요청을 보낼 수 없어 그릴 버튼이 없다
  - 온보딩 전인 사용자는 닉네임이 없어 걸리지 않는다. 탈퇴한 사용자는 행이 지워져 걸리지 않는다
  - 차단 관계에서 어떻게 가릴지는 차단 구현 때 정한다
- **닉네임 부분 일치다** — 검색어가 닉네임 어디에 있어도 걸린다. `%`·`_`는 와일드카드가 아니라 글자 그대로 찾는다
- **`relationshipType`은 조회하는 사람 기준이며 10-2와 같은 값이다** — 버튼을 무엇으로 그릴지가 이 값에 달렸다. 본인이 빠지므로 `null`은 없다
  - `NONE`이면 친구 요청(10-4), `FRIEND_REQUEST_SENT`면 취소(10-6), `FRIEND_REQUEST_RECEIVED`면 수락(10-5)·거절(10-6), `FRIEND`면 친구 표시
  - 한 번도 관계를 맺지 않은 사람과, 거절·취소·삭제로 되돌아간 사람은 둘 다 `NONE`이다
- **정렬**: 닉네임 오름차순이다. 닉네임이 UNIQUE라 커서는 마지막 항목의 닉네임 하나로 다음 위치가 정해진다. 정확히 일치하는 사람을 위로 올리지 않는다
  - 페이지 사이에 닉네임을 바꾼 사람은 빠지거나 한 번 더 내려올 수 있다 — 클라는 `userId`로 중복을 덮어쓴다
- **에러 (400 Bad Request)**: `INVALID_REQUEST` — `q`가 없거나 공백뿐이거나 16자를 넘는다, `limit`이 1보다 작다, 커서를 풀 수 없다(서버가 준 값을 그대로 돌려보내지 않았다)
- **인증**: 필요

### 10-10. `GET /api/v1/users/{userId}/achievements` — 업적

- **화면**: 프로필 (본인/타인 공통) — 10칸 중 활성 업적 4개를 그리고, 나머지 6칸은 클라가 비활성(준비중)으로 채운다
- **Response `200 OK`**

```json
{
  "achievements": [
    { "type": "TOTAL_DISTANCE",      "level": 2, "maxLevel": 5, "thresholds": [10000, 50000, 100000, 300000, 1000000] },
    { "type": "BEST_PACE",           "level": 1, "maxLevel": 4, "thresholds": [420, 360, 300, 240] },
    { "type": "BEST_COMBO",          "level": 0, "maxLevel": 4, "thresholds": [10, 30, 60, 120] },
    { "type": "MATCH_RUNNING_COUNT", "level": 3, "maxLevel": 5, "thresholds": [1, 5, 10, 30, 100] }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `type` | String | O | 업적 종류 — 아래 표 |
| `level` | Integer | O | 달성한 단계. 하나도 못 넘었으면 `0` |
| `maxLevel` | Integer | O | 최대 단계 = `thresholds` 길이 |
| `thresholds` | Integer[] | O | 단계 기준값(1단계부터). 단위는 업적마다 다르다 — 아래 표 |

| `type` | 판정 값 | `thresholds` 단위 | 넘은 것으로 보는 조건 |
|---|---|---|---|
| `TOTAL_DISTANCE` | 누적 거리 | 미터 | 값 ≥ 기준값 |
| `BEST_PACE` | 최고 페이스 | 초/km | 값 ≤ 기준값 (빠를수록 작다) |
| `BEST_COMBO` | 최고 콤보 | 회 | 값 ≥ 기준값 |
| `MATCH_RUNNING_COUNT` | 매칭 러닝 횟수 | 회 | 값 ≥ 기준값 |

- **판정 값 자체는 싣지 않는다** — 프로필은 수치 통계를 보여주지 않기로 해서(`feature-spec.md` 프로필 페이지 절) 단계만 내린다. 기준값은 배지 이름("10km")과 다음 목표를 그리는 데 쓴다
- 판정 규칙(어떤 기록을 세는지)은 `feature-spec.md` 업적 절이 정본이다. 저장하지 않고 조회할 때 계산한다
- **기준값·단계 수는 운영 설정이다** — 위 숫자는 형식 예시다. 클라는 단계 수를 하드코딩하지 않고 `maxLevel`·`thresholds`를 그대로 쓴다
- **항목 순서는 위 표 순서로 고정한다.** 업적이 늘면 뒤에 붙고 클라는 빈칸을 줄인다 — 모르는 `type`은 건너뛴다
- 기록이 없거나 온보딩 전인 사용자는 전부 `level: 0`이다
- **에러 (404 Not Found)**: 대상이 없다(탈퇴 포함)
- **인증**: 필요

### 10-11. `GET /api/v1/users/me/friend-recommendations` — 추천 친구

- **화면**: 친구 목록 페이지 (본인)
- **Response `200 OK`**

```json
{
  "recommendations": [
    {
      "userId": "01a0f2d4-6180-7000-8000-446655440013",
      "nickname": "철수",
      "profileImageUrl": "https://...",
      "reason": "RAN_TOGETHER"        // RAN_TOGETHER | SIMILAR_PACE
    }
  ]
}
```

- **함께 뛴 사람(`RAN_TOGETHER`)을 먼저 채우고 남은 자리를 페이스가 비슷한 사람(`SIMILAR_PACE`)으로 채운다.** 판정·제외 규칙은 `feature-spec.md` 추천 친구 절
- **결과는 모두 친구 요청을 보낼 수 있는 사람이다**(`relationshipType`이 늘 `NONE`이라 싣지 않는다) — 버튼은 친구 요청(10-4) 하나다
- 상대의 페이스 값은 싣지 않는다
- **페이지를 나누지 않는다** — 총 인원이 운영값으로 묶여 있다. 후보가 없으면 빈 배열이다
- 온보딩 전이면 페이스 갈래는 비고 함께 뛴 사람만 나온다
- **인증**: 필요

### 10-12. `POST /api/v1/users/{userId}/block` — 차단 / `DELETE` — 차단 해제

- **화면**: 차단은 타인 프로필, 해제는 차단 목록(10-13)
- **차단은 유저가 아니라 나와 그 사람 사이의 관계다** — 유저 리소스(`/users/{userId}`)를 지우는 것이 아니라 하위 리소스 `block`을 만들고 지운다. `DELETE /users/me`(회원탈퇴)와 모양이 겹치지 않게 하려는 것이기도 하다
- **토글형 액션이다**(`api-convention.md` 응답 형태) — 이미 차단한 사람을 다시 차단해도, 차단하지 않은 사람을 해제해도 성공한다
- **Request 본문이 없다**
- **Response `204 No Content`** — 차단·해제 모두
- **차단했을 때 일어나는 일(친구 관계·요청·초대 정리, 상대에게 보이는 방식, 양방향 여부)은 차단 구현 때 정한다** — 정해진 방향은 `feature-spec.md` 차단 절(프로필 안 보임·초대 불가·친구 목록 보기 불가)
- **에러 (400 Bad Request)**: `CANNOT_BLOCK_SELF` — 자기 자신은 차단할 수 없다(차단만 해당)
- **에러 (404 Not Found)**: 대상이 없다(탈퇴 포함)

```json
{
  "code": "CANNOT_BLOCK_SELF",
  "message": "자기 자신은 차단할 수 없습니다."
}
```

- **인증**: 필요

### 10-13. `GET /api/v1/users/me/blocks` — 차단 목록

- **화면**: 설정 또는 프로필의 차단 목록 — 해제(10-12 `DELETE`)의 진입점이다
- **내가 차단한 사람만 담는다** — 나만 보는 목록이라 `/me`다. 나를 차단한 사람은 담지 않는다
- **Query**: `cursor`/`limit` — 최근에 차단한 순
- **Response `200 OK`**: `{ "items": [ { "userId", "nickname", "profileImageUrl" } ], "nextCursor": "..." }`
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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `mimeType` | String | O | `image/jpeg`·`image/png`·`image/webp`만 허용(대소문자 무시) |
| `fileSizeBytes` | Integer | O | 1 이상 10,485,760(10MB) 이하 — 서명에 포함되므로 실제 업로드 크기와 같아야 한다 |

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/01a0f2d4-.../9f1c2b7e-....jpg",
  "uploadUrl": "https://..."
}
```

`profileImageKey` 포맷은 `profiles/{userId}/{imageId}.{확장자}` — 소유자 검증에 쓰이므로 클라가 임의로 만들지 않는다. 확장자는 `mimeType`이 정한다(`image/jpeg`→`jpg`, `image/png`→`png`, `image/webp`→`webp`). 클라는 `uploadUrl`로 S3에 직접 업로드하며, 업로드 헤더의 `Content-Type`은 소문자 정규값(`image/jpeg`·`image/png`·`image/webp`)이어야 한다(서명에 포함) — 요청 `mimeType`을 대문자로 보냈어도 서명에는 소문자가 들어간다. `uploadUrl`은 운영값(현재 10분) 동안만 유효하다.

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
  "profileImageKey": "profiles/01a0f2d4-.../9f1c2b7e-....jpg"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `profileImageKey` | String | O | 255자 이하. 11-1이 발급한 키 그대로 |

- **Response `200 OK`**

```json
{
  "profileImageKey": "profiles/01a0f2d4-.../9f1c2b7e-....jpg"
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

- **에러 (400 Bad Request — 본인 키가 아니거나, 올라간 파일이 10MB를 넘거나 `Content-Type`이 허용값이 아님)**

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

사진이 등록돼 있지 않으면 `profileImageUrl`은 `null`이다. URL은 운영값(현재 1시간) 동안만 유효하다 — 10-2의 `profileImageUrl`도 같다.

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

### 11-5. `POST /api/v1/users/me/background-image/presigned-url` — 배경 사진 업로드 URL

11-1과 같은 흐름이다 — 키 prefix와 응답 필드 이름만 다르다.

- **Request**

```json
{
  "mimeType": "image/jpeg",
  "fileSizeBytes": 204800
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `mimeType` | String | O | `image/jpeg`·`image/png`·`image/webp`만 허용(대소문자 무시) |
| `fileSizeBytes` | Integer | O | 1 이상 10,485,760(10MB) 이하 — 서명에 포함되므로 실제 업로드 크기와 같아야 한다 |

- **Response `200 OK`**

```json
{
  "backgroundImageKey": "backgrounds/01a0f2d4-.../9f1c2b7e-....jpg",
  "uploadUrl": "https://..."
}
```

`backgroundImageKey` 포맷은 `backgrounds/{userId}/{imageId}.{확장자}` — 프로필 사진(`profiles/`)과 prefix가 달라 서로의 키로 반영할 수 없다. 확장자·업로드 헤더·`uploadUrl` 유효 시간 규칙은 11-1과 같다.

- **에러 (400 Bad Request)** — 11-1과 같다

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

### 11-6. `PATCH /api/v1/users/me/background-image` — 배경 사진 반영

11-5로 받은 `uploadUrl`에 업로드를 마친 뒤 호출한다. 서버가 S3에 실제로 올라왔는지 확인하고 `users.background_image_key`를 갱신한다.

- **Request**

```json
{
  "backgroundImageKey": "backgrounds/01a0f2d4-.../9f1c2b7e-....jpg"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `backgroundImageKey` | String | O | 255자 이하. 11-5가 발급한 키 그대로 |

- **Response `200 OK`**

```json
{
  "backgroundImageKey": "backgrounds/01a0f2d4-.../9f1c2b7e-....jpg"
}
```

- **에러 (400 Bad Request — 요청 검증)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "배경 이미지 키는 필수입니다."
}

{
  "code": "INVALID_REQUEST",
  "message": "배경 이미지 키는 255자 이하여야 합니다."
}
```

- **에러 (400 Bad Request — 본인 키가 아니거나(프로필 사진 키 포함), 올라간 파일이 10MB를 넘거나 `Content-Type`이 허용값이 아님)**

```json
{
  "code": "INVALID_BACKGROUND_IMAGE",
  "message": "배경 이미지가 올바르지 않습니다."
}
```

- **에러 (400 Bad Request — 키에 해당하는 객체가 S3에 없음)**

```json
{
  "code": "BACKGROUND_IMAGE_NOT_UPLOADED",
  "message": "업로드되지 않은 이미지입니다."
}
```

- **인증**: 필요

### 11-7. `GET /api/v1/users/{userId}/background-image` — 배경 사진 URL 조회

- **Response `200 OK`**

```json
{
  "backgroundImageUrl": "https://..."
}
```

사진이 등록돼 있지 않으면 `backgroundImageUrl`은 `null`이다 — 기본 배경은 클라가 그린다. URL은 운영값(현재 1시간) 동안만 유효하다.

- **10-2에 싣지 않고 따로 둔다** — 10-2는 프로필 화면 밖(매칭·러닝 중)에서도 불리는데 거기선 배경이 필요 없다. 실으면 쓰지 않는 URL을 매번 서명한다

- **에러 (404 Not Found — 대상 사용자 없음)**

```json
{
  "code": "NOT_FOUND",
  "message": "요청한 리소스를 찾을 수 없습니다."
}
```

- **인증**: 불필요

### 11-8. `DELETE /api/v1/users/me/background-image` — 배경 사진 삭제

`users.background_image_key`를 비운다. **S3 객체는 지우지 않고 DB의 키 연결만 끊는다.** 사진이 없는 상태에서 호출해도 에러 없이 성공한다(idempotent).

- **Request**: 본문 없음
- **Response `204 No Content`**

- **인증**: 필요

### 11-9. `GET /api/v1/users/me/profile` — 프로필 편집용 조회

- **화면**: 프로필 편집 (진입 시 입력 칸을 현재 값으로 채운다)
- **11-10 `PATCH`와 같은 필드 집합이다** — 고치기 전에 읽는 자리라 둘이 짝이다
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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `introduction` | String | X | 소개글. 없으면 `null` (`users`에서 읽는다) |
| `gender` | String | X | `MALE` \| `FEMALE`. 온보딩 전이면 `null` (`user_onboardings`에서 읽는다) |
| `birthday` | String | X | `YYYY-MM-DD`. 온보딩 전이면 `null` (`user_onboardings`에서 읽는다) |
| `weightKg` | Number | X | 저장된 값(소수점 첫째 자리). 온보딩 전이면 `null` (`user_onboardings`에서 읽는다) |
| `heightCm` | Number | X | 저장된 값(소수점 첫째 자리). 온보딩 전이면 `null` (`user_onboardings`에서 읽는다) |

- **닉네임·사진은 여기 없다** — 편집 화면에 함께 있지만 저장 흐름이 달라 엔드포인트가 갈린다(11-3·11-11)
- **온보딩 전에도 `200`이다** — `user_onboardings`에 행이 없으면 그 출처의 네 필드가 `null`로 나가고 소개글만 채워진다. 11-10이 소개글만 보내는 요청을 온보딩 전에도 받아주므로 여기서도 막지 않는다
- **키·몸무게는 이 API로만 나간다** — 프로필 화면(10-2)에는 싣지 않는다
- **인증**: 필요

### 11-10. `PATCH /api/v1/users/me/profile` — 프로필 수정

저장 버튼 하나로 끝나는 값들을 한 요청에 담는다. 사진(11-1~11-4)은 저장 버튼과 무관한 3단계 업로드라, 닉네임(11-11)은 중복 검사와 409가 붙어 각각 전용 엔드포인트를 쓴다. 평균 페이스는 수정 불가(서버가 러닝 기록으로 갱신).

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

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `introduction` | String | X | 100자 이하. 빈 문자열이면 소개글을 지운다 (`users`에 저장) |
| `gender` | String | X | `MALE` \| `FEMALE`(대소문자 무시, 저장·응답은 대문자) (`user_onboardings`에 저장) |
| `birthday` | String | X | `YYYY-MM-DD`, 1900-01-01 이후이며 미래일 수 없다. 만 14세 이상 (`user_onboardings`에 저장) |
| `weightKg` | Number | X | 20 이상 300 이하. 소수점 둘째 자리 이하는 반올림해 저장한다 (`user_onboardings`에 저장) |
| `heightCm` | Number | X | 20 이상 300 이하. 소수점 둘째 자리 이하는 반올림해 저장한다 (`user_onboardings`에 저장) |

- **필드를 생략하면 현재 값을 그대로 둔다.** 소개글만 빈 문자열(`""`)로 지울 수 있고, 나머지 넷은 온보딩에서 필수라 지우는 개념이 없다
- **키·몸무게는 소수점 첫째 자리로 정규화한다.** `70.55`를 보내면 거부하지 않고 `70.6`으로 저장하며, 응답에도 정규화된 값이 나가 클라이언트가 저장된 값을 알 수 있다. 정상 범위의 값을 자릿수만으로 400으로 막지 않기 위해서다
- **화면 구성이 확정되기 전이라 편집 가능한 값을 한 엔드포인트에 모았다** — 저장 버튼이 하나로 묶이든 여러 화면으로 갈리든 클라이언트가 자기 필드만 보내면 된다. 화면이 정해지면 이 구성을 다시 본다
- **`profileVisibility`는 여기 없다** — 프로필 편집이 아니라 설정 페이지 값이라 12-3이 담당한다

- **Response `200 OK`** — 갱신본. 보낸 필드만 담아 돌려준다

```json
{
  "introduction": "즐겁게 달려요",
  "weightKg": 70.5
}
```

- **바꾼 값만 돌려준다** — 사진 반영(11-2)·닉네임(11-11)과 같은 형태다
- **11-9 `GET`의 `null`과 뜻이 다르다** — 여기서 빠진 필드는 "안 보냈다", 11-9의 `null`은 "아직 값이 없다"다
- **소개글을 `""`로 지우면 응답도 `""`다** — 조회는 `null`이지만 여기서는 `null`이 "안 보냈다"라 겹쳐 쓸 수 없다

- **에러 (400 Bad Request)** — 문구는 온보딩(1-10)과 같다

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

### 11-11. `PATCH /api/v1/users/me/nickname` — 닉네임 변경

닉네임은 `user_onboardings.nickname`에 있어 온보딩을 마쳐야 바꿀 수 있다. 서비스 전반의 표시명이 이 값이다.

- **Request**

```json
{
  "nickname": "완두콩"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nickname` | String | O | 2~16자, 한글·영문·숫자·`_`만 |

- **Response `200 OK`**

```json
{
  "nickname": "완두콩"
}
```

- **바꾼 값만 돌려준다** — 본인 대상 수정이라 `userId`는 클라이언트가 이미 알고 있다(10-1·로그인 응답). 온보딩(1-10)은 `201 Created`로 리소스를 만들며 식별자를 돌려주지만, 수정은 그 자리가 아니다
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

### 11-12. `POST /api/v1/users/nickname/availability` — 닉네임 중복 확인

저장하기 전에 쓸 수 있는 닉네임인지 미리 확인한다. 확인과 저장 사이에 남이 선점할 수 있으므로 최종 방어는 11-11·1-10의 409다. 사용 화면은 프로필 편집·온보딩이다.

- **Request**

```json
{
  "nickname": "완두콩"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `nickname` | String | O | 2~16자, 한글·영문·숫자·`_`만 |

- **Response `200 OK`**

```json
{
  "nickname": "완두콩",
  "available": true
}
```

- **본인이 지금 쓰는 닉네임도 `false`다** — 공개 API라 호출자를 모르고 사용 중인지만 본다. 편집 화면에서는 값이 바뀌지 않았으면 확인하지 않는다(11-11은 같은 값이면 그대로 200이다)

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

### 12-1. `PATCH /api/v1/users/me/password` — 비밀번호 변경

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

- 클라는 10-1의 `loginType`으로 메뉴를 감추지만 서버도 막는다 — 구버전 앱과 직접 호출이 있다

- **인증**: 필요

> 비밀번호 찾기(로그인 전 재설정)는 명세에 없다. 이 API는 로그인 상태 전용이다.

### 12-2. `GET /api/v1/users/me/settings` — 설정 조회

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

### 12-3. `PATCH /api/v1/users/me/settings` — 설정 변경

- **화면**: 설정
- **Request**

```json
{
  "alertConsent": false,                 // 선택
  "profileVisibility": "FRIENDS"         // 선택
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `alertConsent` | Boolean | X | 전체 알림 on/off |
| `profileVisibility` | String | X | `FRIENDS` \| `PUBLIC`(대소문자 무시, 저장·응답은 대문자) |

- **Response `200 OK`** — 12-2와 같은 형식으로 저장 후 설정 전체를 담는다

```json
{
  "alertConsent": false,
  "profileVisibility": "FRIENDS"
}
```

- **한 필드만 보내도 응답에는 두 필드가 모두 담긴다** — 클라이언트가 이 응답으로 설정 화면 상태를 그대로 갱신하며, 토글을 연달아 눌러도 마지막 응답이 화면을 정리한다
- **필드를 생략하면 현재 값을 그대로 둔다.** 둘 다 생략한 `{}`는 아무것도 바꾸지 않고 현재 설정을 반환한다
- **에러 (400 Bad Request)**

```json
{
  "code": "INVALID_REQUEST",
  "message": "프로필 공개 범위는 FRIENDS 또는 PUBLIC이어야 합니다."
}
```

- **인증**: 필요

### 12-4. `DELETE /api/v1/users/me` — 회원탈퇴

- **화면**: 설정 (확인 팝업 후)
- **동작 (테이블별 정책)**:
  - 탈퇴는 활성 상태 때문에 막지 않는다. `MATCHING`이면 5-A의 대기 취소, `MATCHED`이면 방 나가기를 적용하고 인원을 갱신해 남은 참가자에게 이벤트를 보낸다. `STARTED`이면 본인이 `RUNNING`일 때 마지막 수신 데이터로 5-D의 종료 처리를, `JOINED`(미접속)일 때 강제 종료와 같은 기준의 이탈 처리를 먼저 적용한다. 미접속이면 시작 후 취소처럼 인원을 줄여 남은 참가자에게 이벤트를 보내고, 0이 되면 방을 `CANCELLED`로 닫는다.
  - `delete_users` 스냅샷 후 `users`를 하드 삭제한다. `delete_users.created_at`은 스냅샷 시각이다.
  - **유지**: `feeds`/`comments`/`running_records`(+segments·splits)/좋아요. `running_players`와 `running_room_sessions`는 상태와 무관하게 전부 유지한다 — 시작 전 신청도 지우지 않고 일반 취소처럼 닫는다(쿨다운은 걸지 않는다). 시작한 방은 기록 없는 참가자도 결과에 남고, 신청 이력은 통계에 쓴다. 사용자는 공통 탈퇴 유저 형식으로 표시한다.
  - **삭제**: `user_onboardings`(값은 `delete_users`로 스냅샷 후)/`user_devices`/`oauth_users`(`login_type` 판정 후 — 먼저 지우면 `LOCAL`로 보인다)/`user_relationships`(양쪽)/`user_colors`.
  - **DB 밖**: 리프레시 토큰 삭제 + 액세스 토큰 블랙리스트 등록, S3 프로필·배경 사진 90일 뒤 삭제, GPS 원본 유지, 카카오 unlink(`provider_id` 선확보).
  - `delete_users`의 `email`·`nickname`은 90일 뒤 `NULL`로 갱신한다(행은 유지).
- **Response**: `204 No Content` (토큰 즉시 무효화)
- **에러 (503 Service Unavailable — 진행 중인 러닝을 정리하다 저장소 장애)** — 탈퇴는 통째로 되돌려진다. 클라는 잠시 후 다시 시도하게 한다

```json
{
  "code": "ACCOUNT_DELETION_UNAVAILABLE",
  "message": "일시적인 오류로 탈퇴하지 못했습니다. 잠시 후 다시 시도해 주세요."
}
```

- **인증**: 필요
