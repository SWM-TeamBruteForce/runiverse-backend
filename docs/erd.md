# Runiverse ERD (러너버스 데이터 모델)

> 테이블·컬럼명은 PostgreSQL 표준 소문자 `snake_case`. API 표면은 `camelCase`로 매핑(백엔드 담당). 테이블명은 복수형(`users`·`feeds`·`comments` …), FK 컬럼은 참조 테이블의 단수 PK명 그대로 유지(`user_id`·`feed_id`). 자바 엔티티 클래스는 한 행을 표현하므로 단수(`UserJpaEntity`).

---

## 0. 공통 규칙

- **PK 타입**: `users.user_id`만 **UUID**, 그 외 자체 PK는 **bigint**(auto-increment, `running_splits`·`running_combos`만 시퀀스 — 표 참고). 연결·좋아요류(`user_colors`·`feed_likes`·`comment_likes`·`running_room_sessions`)는 **복합 PK**, 유저당 1 row(`user_onboardings`·`oauth_users`·`delete_users`)는 **참조 키가 곧 PK**. → API: `userId`만 UUID 문자열, 나머지 Long.
- **FK/참조 네이밍**: 참조 테이블 PK명 그대로(예: `running_records.running_room_id`). 같은 테이블 이중 참조는 역할명(`user_relationships.user_id`/`target_id`). (`running_room_invitations.inviter_id`/`invitee_id`도 같다). `feeds.running_record_id`는 논리 참조(아래 정책).
- **UNIQUE 표기**: 단일 컬럼 = 제약칸, 복합 UNIQUE = 표 아래 블록쿼트(`oauth_users`·`running_records`·`running_segments`·`running_splits`·`running_combos`·`running_room_invitations`(복합 PK)·`user_relationships`·`scheduled_jobs`·`colors`).
- **CHECK 표기**: 표 아래 블록쿼트에 `CHECK 이름: 조건식`으로 적는다 — 이름·식은 엔티티 `@Check`와 같게 둔다. 다른 제약(NOT NULL·UNIQUE·FK)처럼 엔티티에 있는 것은 빠짐없이 옮긴다. 값 목록의 뜻은 [§6](#6-enum-사전), 조건의 이유는 비고가 설명한다.
- **타임스탬프**: **접미사가 타입을 말한다** — 시점은 전부 `*_at`(`timestamp`, 시간대 없음, **KST 벽시계로 저장**). 앱이 JVM 기본 타임존을 `APP_TIME_ZONE`으로 고정한다(`DefaultTimeZoneInitializer` — 컨텍스트 전에 고정해 감사 컬럼도 같은 시간대로 찍힌다). DB에 저장하는 달력 날짜 컬럼은 `user_onboardings.birthday`뿐이며 API에서는 `YYYY-MM-DD`로 표현한다.
- **감사 컬럼**: `created_at`·`updated_at`은 `NOT NULL`, 앱이 자동 세팅(Hibernate `@CreationTimestamp`/`@UpdateTimestamp`). **write-once 테이블은 `created_at`만 둔다**(`running_records`·`running_segments`·`running_splits`·`running_combos`·`feed_images`·좋아요류·`user_colors`) — 고치지 않으므로 `updated_at`이 늘 같은 값이다. 엔티티는 `BaseCreatedAtEntity`를 상속한다. `scheduled_jobs`·`delete_users`도 `created_at`만 두지만 갱신이 한 번 있다 — 실행 표시(`is_sent`·`sent_at`)와 90일 뒤 신원 비우기(`email`·`nickname`)다. 그 시각은 `sent_at`과 배치 기준(`created_at + 90일`)이 대신 말한다.
- **지표 컬럼 접두어**: 실적 합계는 `total_*`(`total_distance`·`total_duration`·`total_calories`·`total_elevation_gain`), 평균은 `avg_*`(`avg_pace`·`avg_cadence`), 목표는 `target_*`(`target_distance`). **구간(`running_splits`)은 부분값이라 접두어 없이 적는다**(`distance`·`duration`·`calories`) — 접두어의 유무가 전체와 구간을 가른다. 개수 컬럼은 `*_count`(`max_player_count`·`current_player_count`·`desired_player_count`·`leave_count`·`like_count`·`comment_count`)로 예외가 없다.
- **컬럼 순서**: `PK → FK → 분류·상태 → 조건·속성 → 결과·이력 → 감사 컬럼` 순으로 적는다. **PK와 FK는 붙여 쓰고**, FK가 여럿이면 상위 엔티티부터(`running_room_id` → `user_id`). `created_at`·`updated_at`·`deleted_at`은 **항상 맨 아래**다.
- **단위(컬럼에 단위 미표기 — 아래로 통일)**: 거리 = **미터**, 페이스(`avg_pace`) = **초/km**, 시간(`total_duration`·`duration`) = **초**, 칼로리 = **kcal**, 케이던스(`avg_cadence`) = **spm**, 누적 상승 고도(`total_elevation_gain`)·구간 순고도차(`elevation_change`) = **미터**, 기온(`temperature`) = **섭씨**. **좌표는 컬럼으로 두지 않는다** — 경로·지점은 전부 `running_segments.route_polyline`(encoded polyline, precision 5)에서 뽑는다. PostGIS 미사용(위치 기반 기능 도입 시 검토).
- **enum**: DB도 API와 **동일한 영문 코드를 그대로 저장**(Java enum `@Enumerated(STRING)`) — 한글 값·변환 매핑 없음. 컬럼별 값 목록은 [§6 enum 사전](#6-enum-사전).
- **`deleted_at`**: `feeds`는 소프트 삭제, `comments`는 답글이 있을 때의 톰스톤, `running_rooms`는 **[MVP 제외]** 관리자 숨김에 쓴다. `delete_*` 테이블은 별도 용도다([§5](#5-delete_-스냅샷이력-테이블)).
  - **`running_players.deleted_at`은 "신청이 끝난 시각"이다** — 활성 신청은 값의 유무로 판정하고, 값은 신청이 끝난 시각으로 남아 쿨다운 DB 폴백을 얹을 때의 기준이 된다.
- **`user_id` FK 정책 (회원탈퇴 연동)**: 탈퇴 시 **CASCADE 삭제**되는 테이블(`user_onboardings`·`oauth_users`·`user_devices`·`user_relationships`·`user_notifications`·`running_room_invitations`·`user_colors`)은 `user_id` **FK + ON DELETE CASCADE**. **유지**되는 테이블(`feeds`·`comments`·`running_records`·`feed_likes`·`comment_likes`)은 `user_id`를 **논리 참조**(FK 제약 없음 — `users` 하드delete 후 값 유지, 무결성은 앱 레벨). 표기 `→ users`
  - **CASCADE에 기대지 않고 앱이 먼저 지운다** — 온보딩 값과 로그인 수단은 `delete_users`로 옮겨야 하는데 DB 연쇄 삭제는 애플리케이션을 거치지 않는다. 기존 개발 DB처럼 FK가 붙지 않은 환경도 있어 CASCADE 유무와 무관하게 동작해야 한다.
- **`running_players`는 상태와 무관하게 유지한다.** 탈퇴 전 일반 취소·이탈·러닝 종료 처리를 적용한다(시작 전 신청도 지우지 않고 취소처럼 닫는다). 시작한 방의 참가 row와 세션은 기록 없는 참가자도 과거 결과에 남기기 위해, 그 밖의 신청 이력은 통계에 쓰기 위해 남긴다.
  - 논리 참조 `user_id` 컬럼은 전부 **NOT NULL**이다(nullable은 스냅샷 테이블 `delete_feeds`·`delete_comments`뿐).
- **`feeds.running_record_id` 참조 정책**: 별개 애그리거트라 하드 FK 없이 **ID로만 논리 참조**(DDD *Reference by Identity*). 표기 `→ running_records`. **무결성은 앱 레벨**: 저장 시 존재 검증, 조회 시 유령 참조 방어(기록 카드 미표시).

---

## 1. 도메인 A — 유저 · 인증

### users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK | |
| email | varchar(255) | UNIQUE, NOT NULL | 로컬·소셜 공통 |
| password_hash | varchar(255) | nullable | 소셜 전용 유저는 null. 원문 미보관 |
| alert_consent | boolean | NOT NULL | 전체 알림 on/off 단일 토글 — 모든 푸시 관장 (설정 12-2/12-3) |
| profile_visibility | enum | NOT NULL | 지인 마스킹 on/off |
| profile_image_key | varchar(255) | nullable | S3 key(Presigned 업로드). 미등록이면 null |
| background_image_key | varchar(255) | nullable | 프로필 배경 S3 key(Presigned 업로드). 미등록이면 null — 기본 배경은 클라가 그린다 |
| introduction | varchar(100) | nullable | 소개글. 비우면 null |
| created_at / updated_at | timestamp | NOT NULL | |

> CHECK `ck_users_profile_visibility`: `profile_visibility in ('PUBLIC', 'FRIENDS')`

### user_onboardings

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users, ON DELETE CASCADE | 참조 키가 곧 PK — 1:1 강제 (온보딩 1 row) |
| nickname | varchar(16) | UNIQUE, NOT NULL | 중복 시 409. 프로필 표시명(닉네임 변경도 이 컬럼 갱신) |
| gender | enum | NOT NULL |  |
| birthday | date | NOT NULL | |
| avg_pace | int | NOT NULL | 초/km. 온보딩 입력이 초기값 → 러닝 종료마다 서버가 최근 기록으로 다시 낸다(feature-spec 평균 페이스 갱신). 표본이 덜 차면 갱신하지 않고 온보딩 입력값이 그대로 남는다. **페이스를 읽는 쪽은 전부 이 컬럼 하나를 본다** — `running_players.avg_pace`·`running_rooms.avg_pace`는 신청 시점에 여기서 복사해 가므로 소급 갱신 경로가 없다 |
| weight | numeric(4,1) | NOT NULL | kg |
| height | numeric(4,1) | NOT NULL | cm |
| created_at / updated_at | timestamp | NOT NULL | created_at = 온보딩 완료 시각 |

> CHECK `ck_user_onboarding_nickname`: `char_length(nickname) between 2 and 16`
> CHECK `ck_user_onboarding_gender`: `gender in ('MALE', 'FEMALE')`
> CHECK `ck_user_onboarding_avg_pace`: `avg_pace between 120 and 1800`
> CHECK `ck_user_onboarding_weight`: `weight between 20.0 and 300.0`
> CHECK `ck_user_onboarding_height`: `height between 20.0 and 300.0`

> `users`=계정/인증, `user_onboardings`=온보딩 프로필(온보딩 완료 = row 존재).

### oauth_users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users, ON DELETE CASCADE | 참조 키가 곧 PK — 유저당 소셜 1개(1:1 확정) |
| provider | enum | NOT NULL |  |
| provider_id | varchar(255) | NOT NULL | provider 내 유저 식별자 |
| created_at / updated_at | timestamp | NOT NULL | |

> UNIQUE (provider, provider_id) — 같은 소셜 계정 중복 연결 방지.
> CHECK `ck_oauth_user_provider`: `provider in ('GOOGLE', 'KAKAO', 'APPLE')`

### user_devices

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_device_id | bigint | PK | |
| user_id | UUID | FK → users, NOT NULL, ON DELETE CASCADE | 지금 이 기기로 로그인한 유저 — 다른 계정이 등록하면 바뀐다 |
| push_token | text | NOT NULL | FCM 등록 토큰. 공식 길이 상한이 없어 `text`다 |
| platform | enum | NOT NULL | `X-App-Platform` 헤더 값 |
| device_id | varchar(100) | UNIQUE, NOT NULL | 앱이 설치 때 만드는 기기 식별자 (`POST /users/me/devices` upsert 키). 형식은 보지 않는다 |
| app_version | varchar(30) | NOT NULL | `X-App-Version` 헤더 값 — 등록할 때의 앱 버전 |
| is_active | boolean | NOT NULL | 푸시를 받는 기기인가. 등록하면 이 기기만 true(유저당 하나), 로그인·로그아웃하면 그 유저의 기기 전부 false, FCM이 등록 해제된 토큰이라고 답하면 그 기기 false(그사이 토큰이 바뀌었으면 그대로) |
| created_at / updated_at | timestamp | NOT NULL | |

> CHECK `ck_user_device_platform`: `platform in ('IOS', 'ANDROID')`
> CHECK `ck_user_device_app_version`: `app_version ~ '^[0-9]{1,9}\.[0-9]{1,9}\.[0-9]{1,9}$'`
> 받는 기기를 유저당 하나로 두는 것은 로그인 세션이 계정당 하나라서다. 기기별 세션이 생기면 기기 단위로 바꾼다.

### app_versions (앱 버전 정책)

> **도메인 테이블이 아니라 운영 설정 테이블이다** — 플랫폼마다 "몇 버전부터 쓸 수 있고 최신은 몇 버전인가"만 담는다. 유저·기기에 속하지 않아 FK가 없다. 도메인 A 아래 두는 것은 같은 플랫폼 값을 쓰는 `user_devices` 옆이기 때문이다.

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| app_version_id | bigint | PK | |
| platform | enum | UNIQUE, NOT NULL | 플랫폼당 한 행 |
| minimum_version | varchar(30) | NOT NULL | 이보다 낮은 앱은 쓸 수 없다. API `minimumVersion` |
| latest_version | varchar(30) | NOT NULL | 스토어에 공개된 최신 버전. API `latestVersion` |
| created_at / updated_at | timestamp | NOT NULL | |

> CHECK `ck_app_version_platform`: `platform in ('IOS', 'ANDROID')`
> CHECK `ck_app_version_format`: `minimum_version ~ '^[0-9]{1,9}\.[0-9]{1,9}\.[0-9]{1,9}$' and latest_version ~ '^[0-9]{1,9}\.[0-9]{1,9}\.[0-9]{1,9}$'`
> CHECK `ck_app_version_order`: `string_to_array(minimum_version, '.')::int[] <= string_to_array(latest_version, '.')::int[]`
> 대소 비교는 부분마다 정수로 한다(문자열 비교면 `1.10.0` < `1.9.0`).
> **값은 운영이 SQL로 직접 바꾼다** — 관리 API가 없어 애플리케이션 검증을 거치지 않으므로 형식·대소 관계를 DB가 막는다. 서버가 캐시 없이 요청마다 읽으므로 바꾸면 바로 반영된다 — 버전 확인 API와 구버전 요청 거절이 같은 값을 본다.
> **행은 배포 SQL이 미리 넣는다** — 두 플랫폼 행이 항상 있다. 행을 지우거나 더하지 않고 UPDATE만 한다.
> `minimum_version`은 스토어에서 실제로 설치할 수 있는 버전으로만 올린다 — 아직 공개되지 않은 버전을 요구하면 그 플랫폼 사용자가 전부 막힌다.

---

## 2. 도메인 B — 매칭 · 러닝

> **방은 항상 존재한다**: 솔로 러닝도 1인 방(`type='SOLO'`)을 만든다. 덕분에 `running_records.running_room_id`가 NOT NULL이고, 참가자 조회·기록 저장 경로가 매칭과 솔로에서 동일하다.

### running_rooms

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK | API `runningRoomId`(Long)가 이 값을 가리킴. **신규 방은 신청·개시 시 1인으로 생성** — 매칭·초대는 `MATCHING`, 모집 단계가 없는 솔로는 `MATCHED`로 시작한다 |
| type | enum | NOT NULL | `SOLO` / `MATCH` / `INVITE` — [§6](#6-enum-사전). 생성 시 확정·불변 |
| status | enum | NOT NULL | 진행 단계 — [§6 enum 사전](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 예약 시작 시각 |
| close_at | timestamp | nullable | **방이 닫힌 시각.** `FINISHED`·`CANCELLED`로 갈 때 찍고, 그 전까지는 null이다 — 종류와 무관하게 열려 있는 방은 전부 null. 모집 마감 시각이 아니다(그건 `start_at - 오프셋`으로 계산한다) |
| target_distance | int | nullable | 방의 목표 거리(미터). **솔로 방은 null** — 목표가 없어야 조기 종료 페널티 판정을 건너뛴다. 매칭 조건이라 **정해진 뒤에는 바뀌지 않는다**. 참가자에게서 유추하지 않고 방이 직접 가져 후보 방 조회가 단일 테이블에서 끝난다 |
| avg_pace | int | nullable | 참가자 평균 페이스(초/km). 시작 전까지 참가·이탈마다 갱신하고, 시작 후에는 뛴 방의 평균으로 고정한다. 배정 후보의 순위 재료로 쓰고(자격 조건이 아니다 — 멀어도 매칭한다), `RoomInfo.teamAveragePaceSecondsPerKm`로도 나간다. **nullable인 이유는 시작 전에 참가자가 0이면 평균 낼 대상이 없기 때문이다** — 마지막 값을 남기지 않고 지운다(그 방은 같은 순간 `CANCELLED`로 닫힌다 — 아래 `current_player_count`) |
| max_player_count | int | NOT NULL | 자리 수 — 매칭 `4`, 솔로 `1`, 초대 `4`. 생성 시 확정·불변 |
| current_player_count | int | NOT NULL | 현재 인원. 생성 시 `1`, 참가·이탈마다 갱신한다. `current_player_count < max_player_count`면 들어갈 수 있다. **`1`은 정상 상태다** — 마감 전이면 계속 모집하고 마감 후면 혼자 뛴다. **인원은 이탈로만 줄어든다** — 시작 전 이탈, 시작 후에는 접속 전(`JOINED`) 참가자의 매칭 취소·탈퇴. `0`이 되면 방을 `CANCELLED`로 닫는다(빈 방이 후보로 남지 않게). 완주·조기 종료·강제 종료로는 줄지 않고, 뛰던 참가자가 모두 종료됐을 때 유효 기록 유무로 `FINISHED`/`CANCELLED`가 갈린다(아래 생명주기). 확정 후 이탈·미출석·조기 종료 제재의 면제 판정에도 쓴다 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **[MVP 제외]** 관리자 부정 방 숨김용 |

> CHECK `ck_running_room_type`: `type in ('SOLO', 'MATCH', 'INVITE')`
> CHECK `ck_running_room_status`: `status in ('MATCHING', 'MATCHED', 'STARTED', 'FINISHED', 'CANCELLED')`
> CHECK `ck_running_room_player_count`: `max_player_count >= 1 and current_player_count >= 0 and current_player_count <= max_player_count`
> CHECK `ck_running_room_avg_pace`: `avg_pace between 120 and 3600`
> CHECK `ck_running_room_target_distance`: `target_distance between 1 and 500000`
> CHECK `ck_running_room_close_at`: `(status in ('FINISHED', 'CANCELLED') and close_at is not null) or (status not in ('FINISHED', 'CANCELLED') and close_at is null)`

> **후보 방 배정**: 매칭 신청 시 `type='MATCH' AND status='MATCHING' AND current_player_count < max_player_count`인 방 중 `target_distance`·`start_at`이 맞는 방을 후보로 삼고, `avg_pace`가 가까운 순으로 고른다 — 페이스는 순위 재료라 멀어도 후보에서 빠지지 않는다(`feature-spec.md` 방 배정 기준). 후보가 하나도 없을 때만 새 방을 만든다(1인 방).
> **초대 방**: 사용자가 시작 시각·목표 거리를 직접 정해 연다(`type='INVITE'`). 사람은 `running_room_invitations` 수락으로만 들어오고 후보 방 배정에는 잡히지 않는다. 모집 마감은 매칭 오프셋이 아니라 초대 전용 오프셋(`start_at - 초대 마감 오프셋`, 현재 1분)이고, 이탈·조기 종료·미출석은 언제나 `_NO_PENALTY`다. 그 밖의 마감 이후 경로는 매칭 방과 같다(feature-spec 초대 러닝 생명주기).
> **마감 판정**: 모집 마감(`start_at - 운영 설정 오프셋`)에 도달하면 스케줄러가 **인원과 무관하게** `MATCHED`로 확정한다(1인이면 1인으로 확정돼 혼자 뛴다). `max_player_count` 도달 여부와도 무관하다. 마감 시각은 방 컬럼이 아니다 — 방을 만들 때 `scheduled_jobs`에 `MATCH_CLOSE`(`execute_at = start_at - 오프셋`)를 걸어 그 시각에 확정한다.

### running_players

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_player_id | bigint | PK | 매칭 신청·솔로 참가 단위 |
| user_id | UUID | → users, NOT NULL | 논리 참조(FK 제약 없음). 탈퇴해도 유지 — [§0](#0-공통-규칙) |
| status | enum | NOT NULL | 참가·진행 상태 — [§6 enum 사전](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 희망 시작 시각 |
| target_distance | int | NOT NULL | 목표 거리(미터, API `targetDistanceMeters`). 솔로는 목표가 없어 도달 불가능한 상한(500000)으로 "끝은 유저가 정한다"를 표현한다 — 판정 기준은 이 값이 아니라 방(`running_rooms.target_distance`)이다. `running_records.total_distance`(실제 이동 거리)와 이름으로 갈린다 |
| avg_pace | int | NOT NULL | 신청 시점의 사용자 평균 페이스(초/km). **입력받지 않는다** — 매칭 조건에 페이스 항목이 없어(5-A) 서버가 `user_onboardings.avg_pace`에서 복사한다. 배정 시 방 평균과의 차이로 **후보 순위를 매기는 데 쓴다 — 자격을 가르지는 않는다**(feature-spec 방 배정 기준). 신청 시점 스냅샷이라 이후 원본이 갱신돼도 소급하지 않는다 |
| desired_player_count | int | nullable | **[MVP 제외]** 향후 사용자가 선택할 희망 매칭 인원. 입력 UI가 없는 동안 항상 `4`로 저장한다(솔로 포함) — 읽는 로직은 없다 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **신청이 끝난 시각** — 대기 취소·이탈·완주 공통. 완주도 그 신청이 끝난 것이라 찍는다. 비우면 활성 신청으로 남아 다음 매칭을 신청할 수 없다. 한 번 찍히면 바뀌지 않는다 |

> CHECK `ck_running_player_status`: `status in ('JOINED', 'RUNNING', 'COMPLETED', 'MATCHED_LEFT_PENALTY', 'MATCHED_LEFT_NO_PENALTY', 'RUNNING_LEFT_PENALTY', 'RUNNING_LEFT_NO_PENALTY')`
> CHECK `ck_running_player_avg_pace`: `avg_pace between 120 and 3600`
> CHECK `ck_running_player_target_distance`: `target_distance between 1 and 500000`
> CHECK `ck_running_player_desired_player_count`: `desired_player_count is null or desired_player_count between 2 and 4`

> **방과의 연결은 `running_room_sessions`가 갖는다** — 참가자가 여러 방을 거칠 수 있는 설계라(방 이동은 향후 매칭 알고리즘 몫) 단일 `running_room_id` 컬럼으로는 이력을 담을 수 없고, 현재 속한 방은 `is_connected`로 가린다.
> **`status`는 참가 의사와 진행 상태를 함께 표현한다** — 신청(`JOINED`)에서 러닝(`RUNNING`)·완주(`COMPLETED`)까지 한 축으로 간다. 이탈은 시점과 제재 여부로 네 값이 갈린다. **초대 대기는 신청이 아니다** — `running_room_invitations`가 갖고, 수락할 때 `JOINED`로 처음 생긴다(초대만으로 신청 행이 생기면 활성 신청 판정에 걸린다). 그래서 초대 상태 값(`INVITED`)을 두지 않는다.
> **`status`와 `deleted_at`은 축이 다르다** — `status`가 "어떻게 끝났나"(사유·제재 여부), `deleted_at`이 "언제 끝났나"다. `updated_at`을 이탈 시각으로 쓰지 않는다 — 그 row가 한 번만 더 갱신돼도 값이 밀려 이탈 시각을 잃는다.
> **row 생명주기**: 생성 = 매칭 신청·솔로 개시 / 대기 취소 = `status=MATCHED_LEFT_NO_PENALTY` + `deleted_at` 기록(마감 전이라 언제나 미제재다) / 러닝 시작 = 각자의 WS `RUNNING_START`가 본인을 `RUNNING`으로 전환(일괄 전환 없음) / 이탈 = `status=*_LEFT_*` + `deleted_at` 기록 / 완주 = `status=COMPLETED` + `deleted_at` 기록. **시작 전** 대기 취소·이탈 시 배정 행은 `is_connected=false`로 바꾸고 방 인원을 하나 줄이며, 그 결과 인원이 `0`이면 방을 `CANCELLED`로 닫는다. **러닝이 시작된 뒤에는 인원이 줄지 않는다** — 완주·조기 종료든, 강제 종료로 `JOINED`인 채 닫히든 배정 행만 `is_connected=false`로 내리고 `leave_count`도 올리지 않는다. **예외는 시작 후 `JOINED`인 참가자의 매칭 취소·탈퇴**다 — 시작 전 이탈과 같게 인원을 하나 줄이고 `leave_count`를 올린다. 그래서 상대가 취소·탈퇴해 혼자 남은 사람이 이어서 나가거나 강제 종료되면 미출석 제재가 면제된다. `current_player_count`는 시작 이후 "확정 인원에서 취소·탈퇴한 미출석자를 뺀 수"이며, 미출석 제재(취소·탈퇴·강제 종료)와 조기 종료 제재는 그 값이 1이면 면제된다. 완주·조기 종료로 줄이면 마지막 완주자가 방을 `CANCELLED`로 만들어 버리기도 한다 — 시작 후 방을 닫는 건 전원 종료를 확인하는 별도 경로이고, 유효 기록이 있으면 `FINISHED`, 하나도 없으면 `CANCELLED`다. **초대 방에서는 생성 = 방 개설·초대 수락**이고, 그 뒤 생명주기는 위와 같다.
> **활성 신청 판정**: `deleted_at IS NULL`. `status`는 보지 않는다 — 러닝 중(`RUNNING`)인 참가자도 활성이어야 다음 매칭이 막힌다.
> **러닝 종료 판정**: 목표 거리 도달은 `COMPLETED`, 미달은 실제 거리 비율에 따라 `RUNNING_LEFT_*`다(목표가 없는 솔로는 끝내면 `COMPLETED`). 종료 신호·목표 도달 자동 종료·강제 종료·러닝 중 탈퇴에 같은 규칙을 적용하고, 유효 러닝 판정(거리·시간·경로 산출 가능 + 평균 페이스 허용 범위 + 최소 거리·최소 시간 통과)을 지난 트랙만 기록으로 만든다. 산출할 수 없으면 실제 거리를 0으로 판정한다. **미달이어도 `status`는 그대로 남는다** — 기록 유무와 개인 종료 상태는 별개다.

### running_room_sessions (참가자 ↔ 방 배정)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK1, FK → running_rooms, ON DELETE CASCADE | 배정된 방 |
| user_id | UUID | PK2, → users | 논리 참조(FK 제약 없음). **키를 신청이 아니라 유저로 잡는다** — 취소 후 같은 방에 다시 신청해도 행이 늘지 않고 기존 행을 되살린다 |
| running_player_id | bigint | NOT NULL, → running_players | 논리 참조(FK 제약 없음) — 무결성은 앱이 관리한다. 탈퇴해도 세션은 지우지 않는다. **현재 이 방에 들어와 있는 신청.** PK가 아니라 재배정 시 새 신청으로 갱신된다. 참가자의 상태·페이스·기록을 읽는 조인 경로이며, 지우고 `user_id`로 우회하면 유저의 과거 신청까지 딸려 오거나 완주(`deleted_at` 기록) 후 조인이 끊긴다 |
| leave_count | int | NOT NULL | **이 유저가** 이 방에서 이탈한 **누적** 횟수 — 방 이동(향후 매칭 알고리즘)이 생기면 같은 방을 다시 거쳐 2 이상이 될 수 있다. 배정 시 **페이스 구간이 같은 방들의 순위를 가르는 데 쓴다** — 신청자 본인이 등지고 나왔던 방으로 되돌리지 않기 위한 것이다. **방 전체의 이탈 합이 아니다**: 후보를 훑을 때 `user_id`가 신청자인 행만 읽고, 거쳐 간 적 없는 방은 행이 없어 0으로 본다 |
| is_connected | boolean | NOT NULL | 현재 방 배정 여부이며 WebSocket 연결 상태와 무관하다. 현재 배정 중인 참가자는 행 하나만 true이고, 취소·이탈·**완주** 후에는 모두 false다. **"이 유저가 이 방에서 뛰었나"를 판정하지 않는다** — 그건 `running_players.status`가 답하며, 결과 조회는 이 컬럼을 보지 않는다 |
| created_at / updated_at | timestamp | NOT NULL | `updated_at` = 마지막 배정 변동 시각(`is_connected` 전환·`leave_count` 증가). **write-once가 아니라 두 컬럼 다 둔다** — 이탈, 그리고 향후 재배정·복귀로 갱신되는 테이블이다 |

> CHECK `ck_room_session_leave_count`: `leave_count >= 0`

> **지금은 배정이 신청당 한 번이다** — 신청하면 방 하나에 배정되고, 현재 구현·명세에는 배정을 바꾸는 흐름이 없다. 나가면 그 행이 `is_connected=false`로 남는다.
> **스키마는 방 이동을 담을 수 있게 미리 설계돼 있다** — 매칭 알고리즘이 고도화되면 서버가 활성 신청을 더 맞는 방으로 옮겨 다니게 한다. 그때 신청이 방을 옮기면 row가 쌓여 참여 이력이 되고, 거쳐 간 방으로 돌아오면 복합 PK가 같으므로 기존 행의 `is_connected`를 되살리고 `leave_count`만 누적한다(그래서 2 이상이 될 수 있다). 이동 시 두 방의 `current_player_count`는 한 트랜잭션에서 같이 갱신한다.
> **취소 후 재신청은 같은 행을 쓴다** — 키가 유저라 이전에 나갔던 방에 다시 배정되면 행을 새로 만들지 않고 `is_connected`를 되살리며 `running_player_id`를 새 신청으로 갱신한다. 후보에서 막지는 않으며 `leave_count`가 그 방의 순위를 낮춰 되도록 피할 뿐이다 — **그 방이 유일한 후보면 몇 번을 나왔든 다시 들어간다.** 러닝 구간의 "의도적 이탈은 복귀 불가"는 러닝 중인 같은 신청 얘기라 둘 다와 별개다.

### running_room_invitations (러닝 초대)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK, FK → running_rooms, ON DELETE CASCADE | 초대 방(`type='INVITE'`) |
| inviter_id | UUID | PK, → users | 보낸 사람 — 방의 현재 참가자 누구나. **논리 참조** — 보낸 사람이 탈퇴해도 초대는 남는다(방장이 없어 방이 그대로 남는다) |
| invitee_id | UUID | PK, FK → users, ON DELETE CASCADE | 받는 사람 |
| status | enum | NOT NULL | `PENDING` / `ACCEPTED` / `DECLINED` / `LEFT` / `EXPIRED` — [§6](#6-enum-사전) |
| invite_count | int | NOT NULL | 이 보낸 사람이 이 받는 사람에게 보낸 횟수. 운영 설정 상한(`invite.max-invite-count`, 현재 5)에 닿으면 그 사람에게는 보내지 않는다 |
| created_at / updated_at | timestamp | NOT NULL | `created_at` = 처음 보낸 시각, `updated_at` = 마지막 변경(보냄·응답·만료) 시각 |

> PK (running_room_id, inviter_id, invitee_id) — "이 방에서 누가 누구에게" 한 행. 같은 사람이 다시 보내면 행을 늘리지 않고 `invite_count`만 올린다. 초대 ID(대리키)를 두지 않는다 — 받는 사람의 초대는 (방, 받는 사람)으로 정해지고 API도 방 ID로 가리킨다.
> CHECK `ck_room_invitation_status`: `status in ('PENDING', 'ACCEPTED', 'DECLINED', 'LEFT', 'EXPIRED')`
> CHECK `ck_room_invitation_invite_count`: `invite_count >= 1`
> CHECK `ck_room_invitation_users`: `inviter_id <> invitee_id`

> **같은 받는 사람의 행들은 늘 같은 상태다** — 상태는 받는 사람 기준의 사실이다(D가 수락하면 누구의 초대든 D는 방에 들어간 것이다). 수락·거절·나감·다시 초대는 방을 잠그고 그 방의 그 받는 사람 행을 **모두 함께** 바꾼다. DB가 강제하지 못하므로 애플리케이션이 받는 사람 단위 애그리거트로 지킨다. 새 보낸 사람의 행은 지금 상태를 따라 만든다.
> **초대는 신청이 아니다** — 수락하기 전에는 `running_players` 행을 만들지 않는다. 수락하면 그때 `running_players(JOINED)`·`running_room_sessions`를 매칭 참가와 같게 만든다. 활성 신청 판정(`running_players.deleted_at IS NULL`)이 초대에 걸리지 않게 하려는 것이다 — 초대받아도 매칭·솔로를 할 수 있다.
> **전이**: `PENDING` → `ACCEPTED`(수락) / `DECLINED`(거절) / `EXPIRED`(모집 마감 확정·방 취소와 같은 트랜잭션). `ACCEPTED` → `LEFT`(수락해 들어온 방을 나감 — 나가기와 같은 트랜잭션). `DECLINED`·`LEFT`는 다시 초대로 `PENDING`이 된다. `EXPIRED`는 끝이다 — 마감 뒤에는 초대할 수 없다.
> **`ACCEPTED`는 "지금 방에 있음"만 뜻한다** — 나가면 `LEFT`로 바뀐다. 거절(`DECLINED`)과 나감(`LEFT`)은 지금은 같게 다룬다 — 둘 다 현황에서 빠지고 다시 초대로 되살아난다. 이후 정책이 갈릴 수 있어 값을 나눈다.
> **대기 중인 초대에도 다시 보낼 수 있다** — 상태는 그대로 `PENDING`이고 보낸 사람의 `invite_count`만 오른다(독촉). **횟수는 거절·나감 뒤에도 이어서 센다** — 거절당한 뒤 계속 보내는 것을 막는 게 상한의 목적이다.
> **자리가 차서 수락하지 못한 초대는 `PENDING`으로 남는다** — 자리가 나면 다시 수락할 수 있다.
> **거절을 상태로 남긴다** — 방 화면이 거절을 보여준다. `user_relationships`가 거절을 `NONE`(관계 없음)으로 처리하는 것과 다르다.
> **받은 사람이 탈퇴하면 그 사람의 초대를 지우고, 보낸 사람이 탈퇴해도 초대는 남긴다** — 방장이 없어 보낸 사람이 빠져도 방과 초대는 그대로 유효하다.

### running_records

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_record_id | bigint | PK | |
| running_room_id | bigint | FK → running_rooms, NOT NULL | 솔로 러닝도 방을 만드므로 항상 값이 있다 |
| user_id | UUID | → users, NOT NULL | |
| avg_pace | int | NOT NULL | 초/km |
| total_distance | int | NOT NULL | 미터. **목표 거리에서 끊는다** — 목표를 넘겨 뛰어도 `running_rooms.target_distance` 지점을 보간해 그 값으로 확정하고 `end_at`·`total_duration`도 같은 지점 기준으로 맞춘다(거리만 자르면 페이스가 틀어진다). 참가자 전원이 같은 구간 경계를 갖게 하려는 것이다 — 6-2 응답은 구간 경계를 참가자별이 아니라 구간 레벨에 둔다. 목표에 못 미치면 마지막 10m 경계까지로 확정한다 — 10m 미만 꼬리는 버려 구간 합과 어긋나지 않게 한다. **S3 원본 트랙에는 목표 이후 좌표도 전부 남긴다**(재계산용) |
| total_duration | int | NOT NULL | 초. 구간(`running_splits.duration`)의 합. **움직인 시간**이다 — 트랙 필터가 정지·일시정지·관측하지 못한 구간을 빼므로 `end_at - start_at`보다 짧을 수 있다. 멈춘 시간까지 포함한 전체 경과는 `end_at - start_at`으로 구한다(feature-spec 트랙 필터) |
| avg_cadence | int | nullable | spm (선택). 러닝 전체 평균 — 점별 순간 케이던스(`cadenceSpm`)는 DB에 저장하지 않는다(S3 원본 트랙에는 남는다) |
| total_elevation_gain | int | nullable | 누적 상승 고도(미터). 기기 GPS 고도를 운영 임계값으로 필터링해 계산하며 유효 표본이 부족하면 null. 일시정지 경계에서 기준 고도를 새로 잡아 일시정지 중 이동한 고도차는 들어가지 않는다. 구간(`running_splits.elevation_change`)의 합과는 다르다 |
| total_calories | int | NOT NULL | 종료 시 서버가 확정 거리·시간과 사용자 체중으로 계산한 kcal |
| gps_track_key | varchar(255) | NOT NULL | S3 key — 받은 좌표를 그대로(위치·시각·고도·정확도·속도·방위·케이던스·순간 페이스·일시정지 구간 번호) 담은 **원본 트랙**. 재계산·분석용이라 **API 응답에는 쓰지 않는다** |
| weather_code | int | NOT NULL | WMO 4677 코드(0~99) — 날씨 API 원본값 그대로. 조회에 실패하거나 값이 없으면 폴백(`0` 맑음, 기온 `15.0`)이 들어간다. 악조건 여부는 저장하지 않고 판정 시 계산한다 |
| temperature | numeric(3,1) | NOT NULL | 섭씨. 영하 포함 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | 종료 메시지·목표 도달 자동 종료·강제 종료·탈퇴로 기록을 확정할 때 일괄 INSERT. 진행 중 PATCH 없음(write-once) |

> UNIQUE (running_room_id, user_id) — 유저당 방별 1기록. 솔로도 방을 가지므로 부분 인덱스 조건이 필요 없다.
> CHECK `ck_running_record_avg_pace`: `avg_pace between 120 and 3600`
> CHECK `ck_running_record_total_distance`: `total_distance between 1 and 500000`
> CHECK `ck_running_record_total_duration`: `total_duration between 1 and 86400`
> CHECK `ck_running_record_total_calories`: `total_calories between 0 and 20000`
> CHECK `ck_running_record_avg_cadence`: `avg_cadence is null or avg_cadence between 1 and 300`
> CHECK `ck_running_record_elevation_gain`: `total_elevation_gain is null or total_elevation_gain between 0 and 20000`
> CHECK `ck_running_record_weather_code`: `weather_code between 0 and 99`
> CHECK `ck_running_record_period`: `end_at > start_at`
> **러닝 중에는 이 테이블에 쓰지 않는다** — 좌표·누적 거리는 Redis가 원본이고, 확정할 때 러닝 하나치를 Redis에서 통째로 읽어 한 번에 넣는다(write-behind). 배치마다 행을 갱신하지 않는 근거와 관측 방법은 feature-spec GPS 트랙 절.
> **개인 단위 진행 상태는 `running_players.status`가 갖는다.** 기록은 `COMPLETED`뿐 아니라 `RUNNING_LEFT_*`와도 함께 생성될 수 있으며, 기록 생성 가능한 트랙이 없으면 종료 상태만 남는다.
> 결과 조회는 해당 방에서 러닝 단계에 들어간 참가자(`RUNNING`·`COMPLETED`·`RUNNING_LEFT_*`)의 세션에 `running_records`를 LEFT JOIN한다. 시작 전 이탈자는 제외하고 기록 없는 참가자는 유지한다.
> **경로는 이 테이블에 없다** — 끊긴 곳마다 나뉜 조각으로 `running_segments`에 있다. 일시정지가 있어도 기록은 러닝 한 번에 한 행이고, 위 합계 값은 러닝 전체 기준이다.

### running_segments (경로 조각)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_segment_id | bigint | PK | 기록당 몇 개뿐이라 IDENTITY로 둔다 — 구간이 이 ID를 참조하므로 구간보다 먼저 INSERT한다 |
| running_record_id | bigint | FK → running_records, NOT NULL, ON DELETE CASCADE | |
| segment_number | int | NOT NULL | 이 기록에서 몇 번째 조각(1부터, 달린 순서). 끊긴 곳이 없으면 1 하나뿐이다 |
| route_polyline | text | NOT NULL | 이 조각의 10m 경계점(encoded polyline, precision 5). 원본 트랙을 0m부터 인정 거리 10m마다 보간한 경계점을 끊긴 곳에서 나눈 것이다. 조각 안의 점 번호는 조각마다 0부터 센다 — `running_splits.route_start_index`·`route_end_index`가 이 번호다. **API가 내려주는 유일한 경로 데이터**다 |
| created_at | timestamp | NOT NULL | 기록과 함께 일괄 INSERT(write-once) |

> UNIQUE (running_record_id, segment_number) — 기록당 조각 번호 중복 방지. 기록의 조각을 순서대로 읽는 조회도 커버한다.
> CHECK `ck_running_segment_number`: `segment_number >= 1`
> **조각은 트랙 필터가 경로를 끊은 곳에서 나뉜다** — 이웃한 두 경계점 사이에 0으로 한 칸이 있고 그것이 정지(④)가 아닐 때다(일시정지 경계·GPS 튐·거부한 관측 안 된 칸·시계 역행). 정지는 제자리라 끊지 않는다(feature-spec 트랙 필터).
> **조각을 번호순으로 이으면 기록의 경계점 전체다** — 조각끼리 점을 공유하지 않고, 점을 더하거나 빼지 않는다. 그래서 전 조각의 점 수 합은 구간 수 + 1이다.
> **점이 하나뿐인 조각도 있다** — 출발 직후 첫 칸에서 끊기면 1번 조각은 0m 경계점 하나다. 그 조각에는 구간이 붙지 않는다(구간은 끝 경계점이 있는 조각에 속한다 — `running_splits`).
> **응답에서**: 기록 상세(6-1)는 조각마다 좌표 배열 하나로 내려 클라가 조각 사이를 잇지 않는다. 피드 카드(8-1)는 조각을 풀어 한 줄로 다시 인코딩해 `routePolyline`으로 내린다 — encoded polyline은 앞 점과의 차로 인코딩돼 문자열을 그대로 이어 붙이면 깨진다. 기록 목록(7-1)은 경로를 싣지 않는다.

### running_splits (구간별)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_split_id | bigint | PK | 시퀀스 `running_split_seq`로 채번한다(`allocationSize=100`) — IDENTITY면 Hibernate가 배치 INSERT를 끈다 |
| running_segment_id | bigint | FK → running_segments, NOT NULL, ON DELETE CASCADE | 이 구간이 속한 경로 조각 — **끝 경계점이 있는 조각**이다. 기록은 조각을 거쳐 찾는다(`running_segments.running_record_id`) — 구간에 기록 FK를 따로 두지 않는다 |
| split_number | int | NOT NULL | 구간 번호(1부터). 조각이 바뀌어도 기록 전체에서 이어서 센다. API `splitNumber` |
| avg_pace | int | NOT NULL | 초/km |
| distance | int | NOT NULL | 구간 거리(미터) — **10m 고정**. 경계는 0-10, 10-20…으로 끊고, 정확히 10m가 되도록 경계 지점을 보간해 만든다. 목표 5,000m면 구간이 500개다. 목표 미달로 끝난 참가자는 도달한 구간까지만 행이 생긴다 |
| duration | int | NOT NULL | 구간 소요 시간(초) — **움직인 시간**이다. 트랙 필터가 뺀 정지·관측하지 못한 시간은 들어가지 않아, 실제 시각인 `start_at`·`end_at`의 차와 다를 수 있다 |
| avg_cadence | int | nullable | spm (선택). 구간 평균 |
| elevation_change | int | nullable | 필터링한 기기 GPS 고도의 **순고도차**(미터) — 끝 고도 − 시작 고도라 음수가 될 수 있다. 일시정지를 걸친 구간은 경계 앞과 뒤의 순고도차를 더한다. 유효 표본이 부족하면 null이며 `total_elevation_gain`과는 다른 값이다. **10m 구간에서는 대체로 null이다** — 구간에 실측점이 3~4개뿐인데 GPS 수직 오차가 수 m라 노이즈 임계값을 넘는 표본이 거의 없다 |
| calories | int | NOT NULL | 종료 시 서버가 계산한 구간 kcal |
| route_start_index | int | NOT NULL | 소속 조각(`running_segments.route_polyline`)에서 이 구간이 시작하는 점 번호(0부터). 구간 경로를 텍스트로 중복 저장하지 않고 위치만 가리킨다 |
| route_end_index | int | NOT NULL | 끝나는 점 번호(포함). 같은 조각 안에서 구간 N의 끝점은 구간 N+1의 시작점과 같아 값이 하나 겹친다 — 한 행만 읽어도 구간을 자를 수 있게 둘 다 저장한다. 끊긴 곳에 걸친 구간은 시작과 같다(아래) |
| start_at / end_at | timestamp | NOT NULL | 실제 시각(경계점 보간값). `duration`과 달리 멈춘 시간을 빼지 않는다 |
| created_at | timestamp | NOT NULL | |

> UNIQUE (running_segment_id, split_number) — 조각 안에서 구간 번호 중복 방지. 조각의 구간을 번호순으로 읽는 조회도 커버한다.
> **기록 안에서 구간 번호가 겹치지 않는 것은 앱이 보장한다** — 기록 FK가 없어 DB 제약으로는 조각 단위까지만 막는다. 구간은 종료 시 한 번에 만들어져 1부터 이어 번호를 매기므로(도메인 검증) 다른 조각과 겹칠 경로가 없다.
> CHECK `ck_running_split_number`: `split_number >= 1`
> CHECK `ck_running_split_avg_pace`: `avg_pace between 1 and 86400000`
> CHECK `ck_running_split_distance`: `distance between 1 and 500000`
> CHECK `ck_running_split_duration`: `duration between 1 and 86400`
> CHECK `ck_running_split_calories`: `calories between 0 and 20000`
> CHECK `ck_running_split_avg_cadence`: `avg_cadence is null or avg_cadence between 1 and 300`
> CHECK `ck_running_split_elevation_change`: `elevation_change is null or elevation_change between -10000 and 10000`
> CHECK `ck_running_split_route_range`: `route_start_index >= 0 and route_end_index >= route_start_index`
> CHECK `ck_running_split_period`: `end_at > start_at`
> **구간 경계는 방 전체가 공유한다.** 참가자별 실제 거리가 아니라 0m부터 10m씩 자르는 고정 경계라, 같은 방 참가자의 `split_number` N은 언제나 같은 거리 구간을 가리킨다. 6-2가 구간 하나에 참가자 여럿을 묶어 내려줄 수 있는 근거다.
> **행 수가 방마다 수천 개다** — 목표 5,000m·4인 방이면 2,000행이다. 건별 INSERT가 아니라 배치로 넣는다.
> **경계점 목록이 곧 경로라 인덱스가 구조적으로 맞물린다.** 경로가 0m부터 10m마다의 경계점이라, 조각을 이은 전체에서 `N`번 구간은 언제나 `N-1`~`N`번 점이다. 조각 안의 번호로는 같은 조각의 구간끼리 `[i-1, i]`로 이어진다.
> **끊긴 곳에 걸친 구간은 뒤 조각의 첫 점 하나만 가리킨다(`[0, 0]`).** 일시정지 직전 경계점은 앞 조각의 끝, 재개 뒤 첫 경계점은 뒤 조각의 처음이라 그 사이 10m 구간은 두 조각에 걸친다. 구간 N은 경계점 N에 닿는 순간 끝나므로 끝점이 있는 뒤 조각에 넣고, 범위를 그 조각 안에 둔다 — 다른 조각을 가리키는 예외를 만들지 않는다. 그래서 2번 이후 조각은 항상 `[0, 0]` 구간으로 시작하고, 6-2는 이 구간에 점 하나만 실어 선을 긋지 않는다. 거리·시간·페이스는 다른 구간과 같다 — 10m 고정이고 `duration`은 일시정지를 뺀 움직인 시간이다.
> **구간 번호는 조각과 무관하게 이어 센다** — 조각마다 1부터 세면 같은 번호가 참가자마다 다른 거리를 가리켜 위의 "방 전체가 공유하는 경계"가 깨진다.
> **대가는 `running_segments`와의 결합이다** — 조각의 `route_polyline`을 재생성하면 점 개수가 달라져 그 조각의 구간 인덱스가 무효가 되므로 항상 함께 갱신한다. 둘 다 write-once라 재생성 자체가 예외적 상황이다.

### running_combos (러닝 콤보 구간)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_combo_id | bigint | PK | 시퀀스 `running_combo_seq`로 채번한다(`allocationSize=100`) |
| running_room_id | bigint | FK → running_rooms, NOT NULL, ON DELETE CASCADE | |
| first_user_id | UUID | → users, NOT NULL | 논리 참조. 쌍을 정렬한 앞쪽 — Redis 콤보 관계(`RunningComboPair`)와 같은 순서다 |
| second_user_id | UUID | → users, NOT NULL | 논리 참조. 정렬한 뒤쪽 |
| segment_number | int | NOT NULL | 이 방에서 이 쌍의 몇 번째 콤보(1부터, `start_at` 순) |
| combo_count | int | NOT NULL | 이 구간의 유지 횟수 — (`end_at` − `start_at`) ÷ 1회 길이. 1회 길이가 운영 설정이라 계산하지 않고 저장한다 |
| first_route_polyline | text | nullable | `first_user_id`의 콤보 달성 위치 — 그 참가자의 경로에서 고른 점들을 같은 형식(encoded polyline, precision 5)으로 담는다. i번째 점이 i콤보를 달성한 자리다. 점 개수가 `combo_count`와 같다. 각 점은 그 참가자 기록의 10m 경계점 중 달성 시각(`start_at` + i × 1회 길이)에 가장 가까운 점 그대로다. 그 참가자의 기록이 없으면 null |
| second_route_polyline | text | nullable | `second_user_id` 쪽 |
| start_at | timestamp | NOT NULL | 콤보가 붙은 시각 |
| end_at | timestamp | NOT NULL | **쌓인 시각** — 겹쳤다고 판정된 때 두 참가자가 모두 움직였다고 확인된 마지막 순간(feature-spec 러닝 콤보). 끊긴 시각이 아니다. 봐주는 구간이나 서 있던 시간을 넣으면 함께 달리지 않은 구간이 섞인다 |
| created_at | timestamp | NOT NULL | 방이 `FINISHED`로 닫힌 커밋 뒤, 별도 트랜잭션에서 일괄 INSERT(write-once) |

> UNIQUE (running_room_id, first_user_id, second_user_id, segment_number) — 쌍마다 구간 번호 중복 방지. `second_user_id` 쪽 조회 인덱스는 [§7](#7-인덱스-조회-성능).
> CHECK `ck_running_combo_users`: `first_user_id < second_user_id`
> CHECK `ck_running_combo_segment_number`: `segment_number >= 1`
> CHECK `ck_running_combo_count`: `combo_count >= 1`
> CHECK `ck_running_combo_period`: `end_at > start_at`
> **쌍마다 한 행이다.** A-B와 B-A를 따로 두지 않는다. 시각·횟수는 쌍의 값이고 달성 위치만 사람마다 달라 polyline을 두 칸 둔다 — 원격 러닝이라 같은 시각에 각자 다른 길 위에 있다.
> **정렬 순서를 DB 제약으로 건다** — `<`가 정렬과 함께 같은 사용자끼리의 쌍(`<>`)도 막는다. 앱(Java `UUID.compareTo`)은 64비트씩 부호 있는 비교, Postgres는 바이트 순(부호 없는 비교)이라 상위 64비트의 첫 비트가 서로 다른 쌍에서만 결과가 갈린다. `user_id`는 UUIDv7이라 첫 비트가 타임스탬프 최상위 비트로 늘 0이므로 두 순서가 같다. **UUIDv7이 아닌 `user_id`가 들어오면 이 제약이 쌍을 거부할 수 있다.** 조회는 크기 비교를 쓰지 않는다.
> **유지 횟수가 0인 구간은 저장하지 않는다** — 붙자마자 끊겨 함께 달린 구간이 없다.
> **러닝 중에는 이 테이블에 쓰지 않는다** — 판정 상태와 끊긴 구간은 Redis가 원본이다. 방이 닫힐 때 Redis 구간 해시(끊긴 구간)와 관계 해시(이어지던 관계)를 읽어 한 번에 넣는다. `segment_number`는 이때 `start_at` 순으로 매기고, `end_at`은 관계가 들고 있던 쌓인 시각이다(feature-spec 러닝 콤보).
> **콤보는 선이 아니라 점이다.** 클라는 기록의 경로를 그리고, 그 위에 콤보 1회마다 점 하나를 찍는다. 그래서 점은 새로 보간하지 않고 경계점을 그대로 고른다 — 그려진 경로에서 벗어나지 않는다. 경계점의 시각은 구간의 `start_at`·`end_at`에서 얻는다. 어느 조각의 점인지는 담지 않는다 — 콤보는 그 자리에서 이뤄졌다는 점일 뿐이다.
> **위치는 거리가 아니라 시각으로 찾는다.** 콤보 판정은 필터 전 누적 거리로, 경계점은 필터 후 거리로 만들어져 거리로 찾으면 어긋난다. 시각은 하나뿐이다.
> **좌표를 담고 경계점 번호는 담지 않는다.** 번호로 담으면 `running_splits`처럼 경로 재생성에 묶인다.

---

### scheduled_jobs (예약 작업)

> **도메인 테이블이 아니라 기전 테이블이다** — "정해진 시각에 무엇을 실행할지"만 담고 결과는 각 도메인 테이블이 갖는다. 지금은 모집 마감·시작 통지·정각 시작·강제 종료 넷이 이 표를 쓴다. 매칭 방과 초대 방에 같게 걸고, 솔로 방에는 걸지 않는다. 도메인 B 아래 두는 것은 현재 값이 전부 매칭·러닝이기 때문이다.

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| scheduled_job_id | bigint | PK | |
| job_type | varchar(50) | NOT NULL | 무엇을 할 것인가 — [§6 enum 사전](#6-enum-사전) |
| target_id | varchar(100) | NOT NULL | 대상 식별자. 타입마다 가리키는 테이블이 달라(`MATCH_CLOSE`면 `running_room_id`) FK 없이 문자열로 둔다 |
| execute_at | timestamp | NOT NULL | 실행할 시각. `MATCH_CLOSE`는 `running_rooms.start_at - 모집 마감 오프셋`, `RUNNING_READY`는 `start_at - 리드타임`, `RUNNING_START`는 `start_at` 정각, `RUNNING_FORCE_FINISH`는 `start_at + 강제 종료 유예` |
| is_sent | boolean | NOT NULL | 실행 완료 여부. 여러 인스턴스가 같은 예약을 들고 있어도 여기서 하나만 이긴다 |
| sent_at | timestamp | nullable | 실제 실행 시각. `execute_at`과의 차이가 곧 지연이라 운영 지표로 쓴다 |
| created_at | timestamp | NOT NULL | |

> UNIQUE (job_type, target_id) — 한 대상에 같은 종류의 예약은 하나다. 인스턴스 여럿이 같은 예약을 넣으려 해도 DB가 막는다.
> CHECK `ck_scheduled_job_type`: `job_type in ('MATCH_CLOSE', 'RUNNING_READY', 'RUNNING_START', 'RUNNING_FORCE_FINISH')`
> **정본은 이 표고 메모리 타이머는 사본이다.** 각 인스턴스가 `execute_at`에 깨도록 타이머를 걸지만 재시작하면 사라지므로, 부팅 때 `is_sent=false`를 전부 읽어 **지난 것은 즉시 실행하고 남은 것은 다시 건다**. 그래서 타이머가 여러 인스턴스에 중복으로 걸리는 것은 낭비가 아니라 이중화다 — 한 대가 죽어도 남은 대가 쏜다.
> **실행 직전에 예약 행을 잠가 이긴 하나만 일한다** — `SELECT … FOR UPDATE`로 잠그고 `is_sent`를 다시 읽어, 이미 true면 남이 실행한 것이라 그냥 빠진다. 선점(`is_sent=true`)과 실행이 한 트랜잭션이라 실행이 실패하면 선점도 롤백되고, 잠금을 기다리던 인스턴스가 다시 시도한다. 대신 진 쪽은 이긴 쪽의 실행이 끝날 때까지 기다린다. 대상 쪽 상태 재확인은 이것과 별개로 남는다 — 예약이 아닌 경로(사용자 취소 등)로 이미 상태가 넘어가 있을 수 있다.
> **`execute_at`을 저장하는 대가는 오프셋 변경이다** — 모집 마감 오프셋을 바꾸면 아직 실행되지 않은 `MATCH_CLOSE`의 `execute_at`을 함께 UPDATE 해야 한다. 방에서 매번 계산하지 않고 시각을 굳히는 대신 치르는 값이다.
> **행은 지우지 않는다** — 실행 이력이 곧 "그 시각에 확정이 실제로 돌았는가"의 근거다. 보존 기간은 운영에서 정한다.

---

## 3. 도메인 C — 소셜 (친구 · 알림 · 피드 · 댓글)

> **`[MVP 제외]` 표기**: 지금 만들지 않는 테이블. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이다.

### user_relationships (친구 · 요청 · 차단)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_relationship_id | bigint | PK | API에 내보내지 않는다 — 경로는 상대 `userId`로 찾는다 |
| user_id | UUID | FK → users, NOT NULL, ON DELETE CASCADE | **행의 주인** — "내 관점"의 나 |
| target_id | UUID | FK → users, NOT NULL, ON DELETE CASCADE | 상대 |
| type | enum | NOT NULL | 주인 관점의 관계 — `NONE` / `FRIEND` / `FRIEND_REQUEST_SENT` / `FRIEND_REQUEST_RECEIVED` / `BLOCKED` — [§6](#6-enum-사전) |
| created_at / updated_at | timestamp | NOT NULL | `created_at` = 이 쌍의 관계가 처음 생긴 시각, `updated_at` = 마지막 `type` 변경 시각 |

> UNIQUE (user_id, target_id) — 쌍마다 행은 하나다. 관계가 끝나도 지우지 않고 같은 행을 계속 쓴다.
> CHECK `ck_user_relationship_type`: `type in ('NONE', 'FRIEND', 'FRIEND_REQUEST_SENT', 'FRIEND_REQUEST_RECEIVED', 'BLOCKED')`
> CHECK `ck_user_relationship_users`: `user_id <> target_id`

> **사람마다 자기 관점의 행을 갖는다.** `type`이 곧 조회자 기준 API `relationshipType`이라 값 이름을 그대로 맞췄다(변환 없음). 친구 수·친구 목록·관계 확인이 전부 `user_id` 하나로 끝난다 — 요청자/수신자 두 컬럼을 OR로 볼 일이 없다.
> **관계가 끝나면 행을 지우지 않고 `NONE`으로 바꾼다.** 행이 지워지는 것은 탈퇴(두 FK CASCADE)뿐이다. 그래서 API `NONE`은 "행이 없다"와 "`NONE` 행이 있다" 두 경우다 — 둘을 같게 다룬다.
> **친구·요청은 양쪽에 짝으로 쓴다** — A가 요청하면 `(A,B,FRIEND_REQUEST_SENT)`·`(B,A,FRIEND_REQUEST_RECEIVED)`, 수락하면 둘 다 `FRIEND`. **짝은 DB로 막을 수 없어 앱이 한 트랜잭션에서 함께 바꾼다.** 차단은 건 쪽 한 행만 `BLOCKED`다.

| 사건 | A의 행 (A → B) | B의 행 (B → A) |
|---|---|---|
| A가 요청 | `FRIEND_REQUEST_SENT` (없으면 생성, `NONE`이면 갱신) | `FRIEND_REQUEST_RECEIVED` (없으면 생성, `NONE`이면 갱신) |
| B가 수락 (서로 요청해 바로 성립한 경우 포함) | `FRIEND` | `FRIEND` |
| 요청 취소·거절·친구 삭제 | `NONE` | `NONE` |
| A가 B를 차단 | `BLOCKED` (없으면 생성) | 있으면 `NONE` |
| A가 차단 해제 | `NONE` | — |

> **차단은 친구 관계를 끝낸다** — 쌍마다 행이 하나라 `BLOCKED`와 `FRIEND`가 함께 있을 수 없다. 해제하면 `NONE`이라 친구로 돌아가지 않는다.
> **조회는 `NONE`을 관계 없음으로 본다** — 친구 수는 `type='FRIEND'`, 친구 목록은 `type in (FRIEND, FRIEND_REQUEST_SENT, FRIEND_REQUEST_RECEIVED)`처럼 필요한 값을 골라 읽는다. `NONE`을 빼먹지 않도록 "아닌 것"(`<> 'BLOCKED'`)이 아니라 "맞는 것"을 나열한다.
> **이력은 테이블에 남지 않는다** — 행은 지금 상태만 갖는다(마지막 변경 시각은 `updated_at`). 무슨 일이 있었는지(요청·수락·취소·거절·삭제·차단·해제)는 **애플리케이션 로그**로 남긴다(feature-spec 관계 이력).
> **친구 수는 `COUNT`로 구한다** — `user_id`가 본인이고 `type='FRIEND'`인 행.

### user_notifications (인앱 알림)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_notification_id | bigint | PK | API `notificationId`(Long). 생성 순서대로 늘어나 최신순 정렬·커서 기준으로 쓴다 |
| user_id | UUID | FK → users, NOT NULL, ON DELETE CASCADE | 받는 사람 |
| type | enum | NOT NULL | `FRIEND_REQUESTED` / `FRIEND_ACCEPTED` / `RUNNING_INVITED` — [§6](#6-enum-사전). 문구·동작은 클라가 이 값으로 정한다 |
| payload | jsonb | NOT NULL | 종류별 데이터 — 모양은 아래 표. 키 검증은 도메인이 한다 |
| is_read | boolean | NOT NULL, default false | 알림함을 열면 받는 사람의 안 읽은 알림을 모두 true로 바꾼다 |
| created_at / updated_at | timestamp | NOT NULL | `created_at` = 쌓인 시각(정렬·보관 기간 기준), `updated_at` = 마지막 변경 시각(지금은 읽음 처리뿐) |

| type | payload |
|---|---|
| `FRIEND_REQUESTED` | `{"request_user_id": UUID}` — 나에게 친구 요청을 보낸 사람 |
| `FRIEND_ACCEPTED` | `{"accept_user_id": UUID}` — 내 요청을 수락한 사람 |
| `RUNNING_INVITED` | `{"request_user_id": UUID, "running_room_id": bigint}` — 나를 초대한 사람과 그 초대 방 |

> CHECK `ck_user_notification_type`: `type in ('FRIEND_REQUESTED', 'FRIEND_ACCEPTED', 'RUNNING_INVITED')`
> CHECK `ck_user_notification_payload`: `jsonb_typeof(payload) = 'object'`

> **알림은 사건의 기록이다** — 원 상태(친구 요청·초대)가 바뀌어도 고치거나 지우지 않는다. 화면의 현재 상태는 조회할 때 `user_relationships`·`running_room_invitations`에서 읽어 붙인다. 그래서 상태 컬럼이 없다.
> **종류별 값은 컬럼이 아니라 `payload`에 둔다** — 종류가 늘어도 컬럼을 더하지 않는다. 대신 DB는 객체인지만 보고, type과 키의 짝은 도메인(`UserNotificationPayload` 구현체)이 생성할 때와 읽을 때 모두 검증한다. **키 이름은 저장된 약속이다** — 보관 기간 동안 옛 행이 남으므로 바꾸지 않는다.
> **payload의 ID는 논리 참조다** — 알림을 일으킨 사람이 탈퇴해도 알림은 남고 탈퇴 유저 형식으로 보인다. 초대 알림의 지금 상태는 (`running_room_id`, `user_id`)로 `running_room_invitations`를 찾는다.
> **읽을 때 깨진 행은 목록에서 뺀다** — payload 검증에 실패하거나 가리키는 초대가 없는 행은 그 행만 빼고 로그를 남긴다. 알림 하나 때문에 알림함 전체가 열리지 않게 하지 않는다.
> **모두 읽음은 벌크 UPDATE라 `@UpdateTimestamp`를 거치지 않는다** — 쿼리가 `updated_at`을 직접 세팅한다.
> **`deleted_at`을 두지 않는다** — 지우는 경로가 없다. 탈퇴가 소프트 삭제로 바뀌면 그때 뜻을 정해 더한다.
> **만드는 동작과 같은 트랜잭션에서 넣는다**(feature-spec 인앱 알림 저장). 보관 기간(운영값)이 지난 행은 조회에서 빠지고, 지우는 주기는 운영에서 정한다.

### feeds [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_id | bigint | PK | |
| running_record_id | bigint | → running_records, nullable | 러닝 기록 템플릿 카드용. **논리 참조**(FK 제약 없음) — [§0](#0-공통-규칙). UNIQUE 없음(1기록:N피드 허용) |
| user_id | UUID | → users, NOT NULL | 작성자 |
| content | text | nullable | 캡션 (이미지만 있는 피드 허용) |
| visibility | enum | NOT NULL |  |
| like_count | int | NOT NULL, default 0 | |
| comment_count | int | NOT NULL, default 0 | |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable |  |

### feed_images [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_image_id | bigint | PK | |
| feed_id | bigint | FK → feeds, NOT NULL | |
| feed_image_key | varchar | NOT NULL | S3 key |
| mime_type | varchar | nullable | |
| sort_order | int | NOT NULL, default 0 | 표시 순서 |
| created_at | timestamp | NOT NULL | |

### feed_likes [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| feed_id | bigint | PK1, FK → feeds | |
| user_id | UUID | PK2, → users | |
| created_at | timestamp | NOT NULL | |

### comments [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| comment_id | bigint | PK | |
| feed_id | bigint | FK → feeds, NOT NULL | |
| parent_comment_id | bigint | FK → comments, nullable | 답글이면 부모 댓글. depth 1단계 제한(답글엔 답글 불가) — 앱 로직 강제, 스키마 미강제 |
| user_id | UUID | → users, NOT NULL | |
| content | text | nullable | 톰스톤(삭제) 시 null |
| like_count | int | NOT NULL, default 0 | |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable |  |

### comment_likes [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| comment_id | bigint | PK1, FK → comments, ON DELETE CASCADE | 답글 없는 댓글 하드 삭제 시 좋아요도 삭제. 톰스톤은 댓글 row를 유지하므로 영향 없음 |
| user_id | UUID | PK2, → users | |
| created_at | timestamp | NOT NULL | |

---

## 4. 도메인 D — 컬러 [MVP 제외]

> 설계 근거는 `feature-spec.md`의 컬러 시스템 절을 따른다.
> 색 획득·날짜별 표시·컬렉션은 MVP에서 제외하지만 미래 구조와 판정용 관측값 저장은 유지한다.

### colors (색 마스터) [MVP 제외]

고정 데이터. 운영이 채워 넣으며 사용자 행동으로 늘어나지 않는다.

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| color_id | bigint | PK | |
| category | enum | NOT NULL | 12범주 ([§6 enum 사전](#6-enum-사전)) |
| shade_number | int | NOT NULL | 범주 내 순번(1부터). 개수는 범주마다 다르다(3~4). API `shadeNumber` |
| name | varchar | NOT NULL | 색 이름("딥 블루") |
| hex_code | varchar(7) | NOT NULL | `#3c62e2` |
| unlock_description | varchar | NOT NULL | 획득 조건 안내 문구 |
| created_at / updated_at | timestamp | NOT NULL | |

> UNIQUE (category, shade_number) — 범주 내 셰이드 번호 중복 방지.

### user_colors (획득 이력) [MVP 제외]

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK1, FK → users | |
| color_id | bigint | PK2, FK → colors | |
| running_record_id | bigint | → running_records, nullable | 획득 계기가 된 러닝. 누적 조건으로 열린 색은 특정 기록에 귀속되지 않아 null |
| created_at | timestamp | NOT NULL | 획득 시각. 회원탈퇴 시 CASCADE 삭제 |

> **복합 PK가 중복 획득을 막는다** — 이미 보유한 색은 다시 지급되지 않는다.
> 컬렉션 진행률은 `user_colors` 보유 수 / `colors` 전체 행 수로 계산한다.

---

## 5. delete_* (스냅샷/이력 테이블)

FK 강제 없는 독립 테이블(원본 삭제/수정된 row를 참조하므로 FK 미설정). 컬럼은 스냅샷 당시 값 그대로, `created_at`(NOT NULL) = 스냅샷 시각. 단 `delete_users`의 `birth_year`·`bmi`는 식별성을 낮춘 가공값이다.

> **스냅샷은 앱이 남긴다.** `ON DELETE CASCADE`는 DB가 처리하므로 애플리케이션을 거치지 않는다 — 탈퇴로 지워지는 row를 남기려면 탈퇴 유스케이스에서 **명시적으로 INSERT**해야 한다.

### delete_users

회원탈퇴 스냅샷. **한 테이블에 두 목적이 있고 보관 기간이 다르다** — `email`·`nickname`은 신고 대응용이라 90일 뒤 `NULL`이 되고, 나머지는 통계용으로 남는다.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, → users | 탈퇴 유저. 논리 참조(`users` 하드delete 후 값 유지) |
| email | varchar(255) | nullable | 탈퇴 시점 이메일 — 신고 대상의 신원을 특정한다. **90일 뒤 `NULL`로 갱신**하고 행은 남긴다 |
| nickname | varchar(16) | nullable | 탈퇴 시점 닉네임 — 신고가 닉네임으로 들어오므로 탈퇴자를 찾는 고리다. 변경 이력이 없어 마지막 값만 남는다. `email`과 함께 **90일 뒤 `NULL`** |
| gender | enum | nullable | 온보딩 스냅샷. 온보딩 전에 탈퇴하면 null |
| birth_year | int | nullable | **생년월일이 아니라 연도만** — 성별·체형과 겹치면 소수 표본에서 특정된다 |
| avg_pace | int | nullable | 초/km |
| bmi | numeric(5,1) | nullable | **체중·신장이 아니라 파생값** — 원값을 복원할 수 없다 |
| login_type | enum | NOT NULL | 가입 수단. `oauth_users`에 row가 있으면 그 `provider`, 없으면 `LOCAL`. 가입 시점에 정해지므로 온보딩 전에 탈퇴해도 값이 있다 |
| joined_at | timestamp | NOT NULL | **`users.created_at`**(가입 시각) — `user_onboardings.created_at`(온보딩 완료 시각)이 아니다 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 = 탈퇴 시각. `created_at - joined_at`이 체류 기간이다 |

> CHECK `ck_delete_user_gender`: `gender is null or gender in ('MALE', 'FEMALE')`
> CHECK `ck_delete_user_login_type`: `login_type in ('LOCAL', 'GOOGLE', 'KAKAO', 'APPLE')`
> CHECK `ck_delete_user_birth_year`: `birth_year is null or birth_year >= 1900`
> CHECK `ck_delete_user_avg_pace`: `avg_pace is null or avg_pace between 120 and 1800`
> CHECK `ck_delete_user_bmi`: `bmi is null or bmi > 0`
> CHECK `ck_delete_user_onboarding_snapshot`: `(gender is null and birth_year is null and avg_pace is null and bmi is null) or (gender is not null and birth_year is not null and avg_pace is not null and bmi is not null)`

### delete_feeds [MVP 제외]

피드 변경 이력(변경 전 내용 스냅샷 — 신고 시 원본 확인용). **수정 시에만 쌓인다** — 피드는 소프트 삭제라 삭제해도 `feeds.content`가 남는다.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_feed_id | bigint | PK | |
| feed_id | bigint | → feeds | 원본 피드 |
| user_id | UUID | → users | 작성자 |
| content | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

### delete_comments [MVP 제외]

댓글 변경 이력(변경 전 내용 스냅샷). `delete_feeds`와 **용도가 같고 쌓이는 시점만 다르다** — 댓글은 삭제 시 톰스톤으로 `comments.content`가 null이 되므로 수정뿐 아니라 삭제 시에도 원문을 남긴다.
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| delete_comment_id | bigint | PK | |
| comment_id | bigint | → comments | 원본 댓글 |
| feed_id | bigint | → feeds | |
| parent_comment_id | bigint | → comments | |
| user_id | UUID | → users | |
| content | text | | 스냅샷된 내용 |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

---

## 6. enum 사전

컬럼별 값 목록이다.

| 컬럼 | 값 | 비고 |
|---|---|---|
| feeds.visibility | FRIENDS / PUBLIC / PRIVATE | 피드별 개별 저장 |
| users.profile_visibility | FRIENDS / PUBLIC | 지인 마스킹 — FRIENDS는 `user_relationships`로 직접 판정 |
| user_relationships.type | NONE / FRIEND / FRIEND_REQUEST_SENT / FRIEND_REQUEST_RECEIVED / BLOCKED | 행 주인 관점의 관계 — 관계 없음(끝난 관계) / 친구 / 보낸 요청 / 받은 요청 / 내가 차단함. API `relationshipType`과 같은 값(`BLOCKED`는 차단 구현 때 API에 추가). 행이 없어도 `NONE`이다 |
| running_room_invitations.status | PENDING / ACCEPTED / DECLINED / LEFT / EXPIRED | 응답 대기 / 수락(지금 방에 있음) / 거절 / 수락 뒤 방을 나감 / 모집 마감·방 취소로 만료. `user_relationships`와 달리 거절을 값으로 남긴다 — 화면(`RoomInfo.invitations`)은 `PENDING`만 보여주지만, 다시 초대할 때 보낸 횟수를 이어서 세려면 행이 남아야 한다 |
| user_notifications.type | FRIEND_REQUESTED / FRIEND_ACCEPTED / RUNNING_INVITED | 친구 요청 도착 / 내 요청이 수락됨 / 러닝 초대 도착 |
| colors.category | DISTANCE / SPEED / ENDURANCE / CONSISTENCY / CADENCE / INTERVAL / EVEN_PACE / HILLS / RECOVERY / COMPANY / ADVERSITY / MILESTONE | 12범주 — 거리 / 속도 / 지구력 / 꾸준함 / 케이던스 / 인터벌 / 균등페이스 / 언덕 / 회복 / 동행 / 악조건극복 / 이정표 |
| user_onboardings.gender | MALE / FEMALE | |
| user_devices.platform | IOS / ANDROID | |
| app_versions.platform | IOS / ANDROID | `user_devices.platform`과 같은 값 |
| running_players.status | JOINED / MATCHED_LEFT_PENALTY / MATCHED_LEFT_NO_PENALTY / RUNNING / RUNNING_LEFT_PENALTY / RUNNING_LEFT_NO_PENALTY / COMPLETED | 초대 대기는 이 값이 아니라 `running_room_invitations.status`가 갖는다. 참가 / **시작 전 이탈**(제재·미제재) / 러닝 중 / 러닝 중 이탈(제재·미제재) / 완주. **`MATCHED_LEFT_*`는 대기 취소·확정 후 이탈·미출석을 함께 담는다** — 신청이 어떻게 끝났는지를 `status` 한 축으로 읽기 위해서다. 제재 여부는 `_PENALTY`/`_NO_PENALTY`가 가르며, 대기 취소는 언제나 `_NO_PENALTY`다 |
| running_rooms.type | SOLO / MATCH / INVITE | 솔로 러닝 / 랜덤 매칭 / 친구 초대 |
| running_rooms.status | MATCHING / MATCHED / STARTED / FINISHED / CANCELLED | 모집 중(마감 전) / 마감 시점 확정(인원 무관, 1인도 확정) / 시작 / **유효 기록을 남기고** 종료 / 남길 기록 없이 닫힘 — 시작 전이면 항상(인원이 0이 됐거나, 한 번도 시작되지 않은 방을 강제 종료가 닫을 때 — 이때는 인원을 줄이지 않는다), 시작 후면 유효 기록이 하나도 없을 때 |
| oauth_users.provider | GOOGLE / KAKAO / APPLE | |
| scheduled_jobs.job_type | MATCH_CLOSE / RUNNING_READY / RUNNING_START / RUNNING_FORCE_FINISH | 모집 마감 확정(`MATCHING`→`MATCHED`. 매칭 방은 `start_at - 마감 오프셋`, 초대 방은 `start_at - 초대 마감 오프셋`) / 곧 시작 통지(`start_at - 리드타임`에 SSE `RUNNING_READY` 발행 — 방 상태는 바꾸지 않는다) / 정각 시작(`start_at`에 `MATCHED`→`STARTED`. 매칭·초대 방에만 걸고, 참가자 상태는 바꾸지 않는다) / 강제 종료(`start_at + 유예`에 남은 참가자와 방을 닫는다. 매칭·초대 방에만 건다) |
| delete_users.gender | MALE / FEMALE | 온보딩 스냅샷 — 온보딩 전에 탈퇴하면 null |
| delete_users.login_type | LOCAL / GOOGLE / KAKAO / APPLE | `oauth_users.provider`에 `LOCAL`을 더한 값 — 소셜 연동이 없는 계정도 표현해야 한다 |

---

## 7. 인덱스 (조회 성능)

> 복합 PK는 첫 컬럼 조회를 커버한다(`user_colors`·`feed_likes`·`comment_likes`는 별도 불필요하고, `running_room_invitations`의 방 단위 조회도 PK가 덮는다). `running_segments (running_record_id, segment_number)`·`running_splits (running_segment_id, split_number)` UNIQUE도 마찬가지고, `running_records (running_room_id, user_id)` UNIQUE가 방 결과 조회를 커버한다. `running_combos (running_room_id, first_user_id, second_user_id, segment_number)` UNIQUE가 방 안에서 `first_user_id`로 찾는 조회를 커버하고, 아래 `(running_room_id, second_user_id, segment_number)`가 `second_user_id` 쪽을 맡는다 — 참가자는 쌍의 어느 쪽에든 있을 수 있다. 여러 방에 걸친 콤보 조회가 생기면 `first_user_id`·`second_user_id`로 시작하는 인덱스를 각각 추가한다(Postgres가 BitmapOr로 합친다). `colors`는 마스터라 전체 조회만 하므로 인덱스가 없다. 구간은 기록 → 조각 → 구간 순으로 찾는다 — 두 UNIQUE가 각 단계의 FK 조회와 연쇄 삭제를 커버한다.

| 인덱스 대상 | 용도 |
|---|---|
| user_devices.user_id | 푸시 발송 — 유저의 활성 기기 조회 |
| user_relationships.(user_id, target_id) | UNIQUE 겸 조회 — 친구 수·관계 확인(`relationshipType`)이 `user_id` 선두로 탄다 |
| user_relationships.(user_id, type, updated_at, target_id) | 친구 목록(10-7) — 묶음(`type`)마다 따로 읽어 `updated_at`·`target_id` 역순 정렬과 커서 조건을 인덱스가 처리한다. 묶음을 `CASE`로 한 번에 정렬하면 인덱스 순서를 쓰지 못해 묶음 단위로 나눠 읽는다. 역방향 스캔이라 컬럼에 `DESC`를 붙이지 않는다 |
| user_relationships.target_id | 탈퇴 시 `target_id` 쪽 CASCADE 삭제 — 차단 여부 확인은 상대 행을 `(user_id, target_id)`로 찾아 이 인덱스가 필요 없다 |
| user_notifications.(user_id, user_notification_id) | 알림함 최신순 조회(2-4) — ID가 생성 순서라 역방향 스캔으로 최신순·커서 조건을 인덱스가 처리한다. 보관 기간 조건(`created_at`)은 읽은 행에 거는 필터다. 역방향 스캔이라 `DESC`를 붙이지 않는다 |
| user_notifications.user_id WHERE is_read = false | 안 읽은 수(2-4 `unreadCount`)·모두 읽음(2-5) — 부분 인덱스라 안 읽은 행만 담아 작다 |
| running_room_invitations.invitee_id | 받은 초대 목록 |
| user_onboardings.avg_pace | 추천 친구의 페이스 비슷한 사람 — 본인 페이스 ± 운영값 범위 조회 |
| feeds.user_id | **[MVP 제외]** 프로필 피드 그리드·내 피드 |
| feeds.created_at | **[MVP 제외]** 피드 타임라인 최신순 정렬 |
| feed_images.feed_id | **[MVP 제외]** 피드 이미지 조회 |
| comments.feed_id | **[MVP 제외]** 댓글 목록 |
| comments.parent_comment_id | **[MVP 제외]** 답글 지연 로딩 |
| running_records.(user_id, start_at) | 내 기록 조회 — 기간 조회(`start_at` 범위)와 시작 시각 정렬을 인덱스가 함께 처리한다 |
| running_room_sessions.(user_id, is_connected) | 유저의 현재 방 조회 — 복합 PK가 `running_room_id` 방향만 커버해 역방향이 미커버다. 활성 신청에서 배정된 방을 찾을 때 타며, 현재 방 조회는 `is_connected=true`를 항상 함께 본다 |
| running_rooms.(deleted_at, type, status, start_at, target_distance, avg_pace) | 매칭 후보 방 조회 — 같은 슬롯·거리에서 모집 중이고 자리가 남은 방(`type='MATCH' AND status='MATCHING'`). 솔로 방·초대방을 인덱스 단계에서 배제한다. **`avg_pace`는 거르는 조건이 아니라 순위 재료다** — 후보 자격에 페이스 조건이 없어(feature-spec 방 배정 기준) 조회가 값만 실어 나르고 정렬은 애플리케이션이 한다. **모집 마감은 이 인덱스를 타지 않는다** — 방을 훑는 대신 `scheduled_jobs`에 예약을 걸어 그 시각에만 깬다. 슬롯별 대기 인원 집계(`GET /match-rooms/slots`)도 같은 인덱스를 탄다 — 앞 세 컬럼은 등치, `start_at`은 그날 하루 범위, `target_distance`는 거르지 않고 인덱스에서 읽어 거리별로 묶으며 `current_player_count`만 테이블에서 읽는다 |
| running_combos.(running_room_id, second_user_id, segment_number) | 방 안에서 내 콤보 구간 조회의 `second_user_id` 쪽 — UNIQUE가 `first_user_id` 방향만 커버한다. 쌍을 정렬해 저장하므로 참가자가 어느 쪽에 있을지 미리 알 수 없다. `segment_number`까지 두어 구간 순서대로 읽힌다 |
| running_combos.(first_user_id, combo_count) · (second_user_id, combo_count) | 업적 최고 콤보 — 여러 방에 걸친 내 콤보의 최댓값. 쌍의 어느 쪽에든 있을 수 있어 둘 다 두고 Postgres가 각각 읽어 합친다 |
| scheduled_jobs.(is_sent, execute_at) | 부팅 복구 — 아직 실행되지 않은 예약 조회. `is_sent=false`가 선두라 실행이 끝난 대다수를 인덱스 단계에서 배제한다 |
| running_players.(user_id, deleted_at) | 활성 신청 조회 — 중복 신청 검사·내 매칭 상태·러닝 시작 |
| delete_users.(email, created_at) `WHERE email IS NOT NULL` | 보관 기간 만료 정리 — 아직 신원 정보가 남은 행 조회. **부분 인덱스다** — 엔티티 `@Index`로는 조건을 표현할 수 없어 운영 DDL에만 조건이 붙는다. `email`이 선두라 정리가 끝난 대다수를 인덱스 단계에서 배제한다. 행을 지우지 않는 테이블이라 시간이 갈수록 `created_at` 단독으로는 거의 전부를 읽게 된다 |
