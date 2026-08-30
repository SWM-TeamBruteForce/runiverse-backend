# Runiverse ERD (러너버스 데이터 모델)

> 테이블·컬럼명은 PostgreSQL 표준 소문자 `snake_case`. API 표면은 `camelCase`로 매핑(백엔드 담당). 테이블명은 복수형(`users`·`feeds`·`comments` …), FK 컬럼은 참조 테이블의 단수 PK명 그대로 유지(`user_id`·`feed_id`). 자바 엔티티 클래스는 한 행을 표현하므로 단수(`UserJpaEntity`).

---

## 0. 공통 규칙

- **PK 타입**: `users.user_id`만 **UUID**, 그 외 자체 PK는 **bigint**(auto-increment). 연결·좋아요류(`friendships`·`user_colors`·`feed_likes`·`comment_likes`·`running_room_sessions`)는 **복합 PK**, 유저당 1 row(`user_onboardings`·`oauth_users`·`delete_users`)는 **참조 키가 곧 PK**. → API: `userId`만 UUID 문자열, 나머지 Long.
- **FK/참조 네이밍**: 참조 테이블 PK명 그대로(예: `running_records.running_room_id`). 같은 테이블 이중 참조는 역할명(`friendships.requester_id`/`receiver_id`). `feeds.running_record_id`는 논리 참조(아래 정책).
- **UNIQUE 표기**: 단일 컬럼 = 제약칸, 복합 UNIQUE = 표 아래 블록쿼트(`oauth_users`·`running_records`·`running_splits`·`colors`).
- **타임스탬프**: **접미사가 타입을 말한다** — 시점은 전부 `*_at`(`timestamp`, 시간대 없음, **KST 벽시계로 저장**). 앱이 JVM 기본 타임존을 `APP_TIME_ZONE`으로 고정한다(`TimeZoneConfig`). DB에 저장하는 달력 날짜 컬럼은 `user_onboardings.birthday`뿐이며 API에서는 `YYYY-MM-DD`로 표현한다.
- **감사 컬럼**: `created_at`·`updated_at`은 `NOT NULL`, 앱이 자동 세팅(Hibernate `@CreationTimestamp`/`@UpdateTimestamp`). **write-once 테이블은 `created_at`만 둔다**(`running_records`·`running_splits`·`feed_images`·좋아요류·`user_colors`) — 고치지 않으므로 `updated_at`이 늘 같은 값이다. 엔티티는 `BaseCreatedAtEntity`를 상속한다.
- **지표 컬럼 접두어**: 실적 합계는 `total_*`(`total_distance`·`total_duration`·`total_calories`·`total_elevation_gain`), 평균은 `avg_*`(`avg_pace`·`avg_cadence`), 목표는 `target_*`(`target_distance`). **구간(`running_splits`)은 부분값이라 접두어 없이 적는다**(`distance`·`duration`·`calories`) — 접두어의 유무가 전체와 구간을 가른다. 개수 컬럼은 `*_count`(`max_player_count`·`current_player_count`·`desired_player_count`·`leave_count`·`like_count`·`comment_count`)로 예외가 없다.
- **컬럼 순서**: `PK → FK → 분류·상태 → 조건·속성 → 결과·이력 → 감사 컬럼` 순으로 적는다. **PK와 FK는 붙여 쓰고**, FK가 여럿이면 상위 엔티티부터(`running_room_id` → `user_id`). `created_at`·`updated_at`·`deleted_at`은 **항상 맨 아래**다.
- **단위(컬럼에 단위 미표기 — 아래로 통일)**: 거리 = **미터**, 페이스(`avg_pace`) = **초/km**, 시간(`total_duration`·`duration`) = **초**, 칼로리 = **kcal**, 케이던스(`avg_cadence`) = **spm**, 누적 상승 고도(`total_elevation_gain`)·구간 순고도차(`elevation_change`) = **미터**, 기온(`temperature`) = **섭씨**. **좌표는 컬럼으로 두지 않는다** — 경로·지점은 전부 `route_polyline`(encoded polyline, precision 5)에서 뽑는다. PostGIS 미사용(위치 기반 기능 도입 시 검토).
- **enum**: DB도 API와 **동일한 영문 코드를 그대로 저장**(Java enum `@Enumerated(STRING)`) — 한글 값·변환 매핑 없음. 컬럼별 값 목록은 [§6 enum 사전](#6-enum-사전).
- **`deleted_at`**: `feeds`는 소프트 삭제, `comments`는 답글이 있을 때의 톰스톤, `running_rooms`는 **[MVP 제외]** 관리자 숨김에 쓴다. `delete_*` 테이블은 별도 용도다([§5](#5-delete_-스냅샷이력-테이블)).
  - **`running_players.deleted_at`은 "신청이 끝난 시각"이다** — 활성 신청과 쿨다운 판정에 쓰므로 시각 자체가 의미를 갖는다.
- **`user_id` FK 정책 (회원탈퇴 연동)**: 탈퇴 시 **CASCADE 삭제**되는 테이블(`oauth_users`·`user_devices`·`friendships`·`user_colors`)은 `user_id` **FK + ON DELETE CASCADE**. **유지**되는 테이블(`feeds`·`comments`·`running_records`·`feed_likes`·`comment_likes`)은 `user_id`를 **논리 참조**(FK 제약 없음 — `users` 하드delete 후 값 유지, 무결성은 앱 레벨). 표기 `→ users`
  - **`user_onboardings`만 FK를 걸되 `ON DELETE NO ACTION`이다** — DB가 연쇄 삭제하지 않으므로 탈퇴 유스케이스가 `users`를 지우기 전에 **앱이 명시적으로 DELETE**한다. 먼저 지우지 않으면 FK 위반으로 `users` 삭제가 실패한다.
- **`running_players`는 조건부 유지다.** 탈퇴 전 일반 취소·이탈·러닝 종료 처리를 적용한다. 시작 전 신청 row와 세션은 삭제하고, 이미 시작한 방의 참가 row와 세션은 기록 없는 참가자도 과거 결과에 남기기 위해 유지한다.
  - 논리 참조 `user_id` 컬럼은 전부 **NOT NULL**이다(nullable은 스냅샷 테이블 `delete_feeds`·`delete_comments`뿐).
- **`feeds.running_record_id` 참조 정책**: 별개 애그리거트라 하드 FK 없이 **ID로만 논리 참조**(DDD *Reference by Identity*). 표기 `→ running_records`. **무결성은 앱 레벨**: 저장 시 존재 검증, 조회 시 유령 참조 방어(기록 카드 미표시).

---

## 1. 도메인 A — 유저 · 인증

### users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK | |
| email | varchar | UNIQUE, NOT NULL | 로컬·소셜 공통 |
| password_hash | varchar | nullable | 소셜 전용 유저는 null. 원문 미보관 |
| alert_consent | boolean | NOT NULL, default true | 전체 알림 on/off 단일 토글 — 모든 푸시 관장 (설정 12-3/12-4) |
| profile_visibility | enum | NOT NULL, default PUBLIC | 지인 마스킹 on/off |
| profile_image_key | varchar | nullable | S3 key(Presigned 업로드). 미등록이면 null |
| introduction | varchar(100) | nullable | 소개글. 비우면 null |
| created_at / updated_at | timestamp | NOT NULL | |

### user_onboardings

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users | 참조 키가 곧 PK — 1:1 강제 (온보딩 1 row) |
| nickname | varchar | UNIQUE, NOT NULL | 중복 시 409. 프로필 표시명(닉네임 변경도 이 컬럼 갱신) |
| gender | enum | NOT NULL |  |
| birthday | date | NOT NULL | |
| avg_pace | int | NOT NULL | 초/km. 온보딩 입력이 초기값 → 이후 서버가 러닝 기록 기반 자동 갱신 |
| weight | numeric(4,1) | NOT NULL | kg |
| height | numeric(4,1) | NOT NULL | cm |
| created_at / updated_at | timestamp | NOT NULL | created_at = 온보딩 완료 시각 |

> `users`=계정/인증, `user_onboardings`=온보딩 프로필(온보딩 완료 = row 존재).

### oauth_users

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, FK → users | 참조 키가 곧 PK — 유저당 소셜 1개(1:1 확정) |
| provider | enum | NOT NULL |  |
| provider_id | varchar | NOT NULL | provider 내 유저 식별자 |
| created_at / updated_at | timestamp | NOT NULL | |

> UNIQUE (provider, provider_id) — 같은 소셜 계정 중복 연결 방지.

### user_devices

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_device_id | bigint | PK | |
| user_id | UUID | FK → users, NOT NULL | |
| push_token | varchar | NOT NULL | FCM/APNs 토큰 |
| platform | enum | NOT NULL |  |
| device_id | varchar | UNIQUE, NOT NULL | 기기 식별자 (`POST /devices` upsert 키) |
| app_version | varchar | nullable | |
| is_active | boolean | NOT NULL, default true | 재로그인 시 devices API가 true 갱신. 기기 단위 비활성화(로그아웃 시 false)는 **[MVP 제외]** — 로그아웃은 토큰 블랙리스트만 |
| created_at / updated_at | timestamp | NOT NULL | |

---

## 2. 도메인 B — 매칭 · 러닝

> **방은 항상 존재한다**: 솔로 러닝도 1인 방(`type='SOLO'`)을 만든다. 덕분에 `running_records.running_room_id`가 NOT NULL이고, 참가자 조회·기록 저장 경로가 매칭과 솔로에서 동일하다.

### running_rooms

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK | API `runningRoomId`(Long)가 이 값을 가리킴. **신규 방은 신청·개시 시 1인으로 생성** — 매칭은 `MATCHING`, 모집 단계가 없는 솔로는 `MATCHED`로 시작한다 |
| type | enum | NOT NULL | `SOLO` / `MATCH` / `INVITE` — [§6](#6-enum-사전). 생성 시 확정·불변. `INVITE`는 **[MVP 제외]** 예약값 |
| status | enum | NOT NULL, default MATCHING | 진행 단계 — [§6 enum 사전](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 예약 시작 시각 |
| close_at | timestamp | nullable | **방이 닫힌 시각.** `FINISHED`·`CANCELLED`로 갈 때 찍고, 그 전까지는 null이다 — 종류와 무관하게 열려 있는 방은 전부 null. 모집 마감 시각이 아니다(그건 `start_at - 오프셋`으로 계산한다) |
| target_distance | int | nullable | 방의 목표 거리(미터). 매칭 조건이라 **정해진 뒤에는 바뀌지 않는다**. 참가자에게서 유추하지 않고 방이 직접 가져 후보 방 조회가 단일 테이블에서 끝난다 |
| avg_pace | int | nullable | 참가자 평균 페이스(초/km). 참가·이탈마다 갱신. 배정 시 페이스가 가까운 방을 고르는 데 쓰고, `RoomInfo.teamAveragePaceSecondsPerKm`로도 나간다. **nullable인 이유는 참가자가 0이면 평균 낼 대상이 없기 때문이다** — 마지막 값을 남기지 않고 지운다(그 방은 같은 순간 닫힌다 — `FINISHED`·`CANCELLED` 판정은 아래 `current_player_count`) |
| max_player_count | int | NOT NULL | 자리 수 — 매칭 `4`, 솔로 `1`, **[MVP 제외]** 초대 `4`. 생성 시 확정·불변 |
| current_player_count | int | NOT NULL | 현재 인원. 생성 시 `1`, 참가·이탈마다 갱신한다. `current_player_count < max_player_count`면 들어갈 수 있다. **`1`은 정상 상태다** — 마감 전이면 계속 모집하고 마감 후면 혼자 뛴다. `0`이 되면 방을 닫는다. **시작 전이면 항상 `CANCELLED`**(빈 방이 후보로 남지 않게), **시작 후면 유효 기록 유무로 갈린다** — 저장된 기록이 하나라도 있으면 `FINISHED`, 없으면 `CANCELLED`. 이탈 페널티 면제 판정에도 쓴다 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **[MVP 제외]** 관리자 부정 방 숨김용 |

> **후보 방 배정**: 매칭 신청 시 `type='MATCH' AND status='MATCHING' AND current_player_count < max_player_count`인 방 중 `target_distance`·`start_at`이 맞고 `avg_pace`가 가까운 방을 고른다. 없으면 새 방을 만든다(1인 방).
> **마감 판정**: 모집 마감(`start_at - 운영 설정 오프셋`)에 도달하면 스케줄러가 **인원과 무관하게** `MATCHED`로 확정한다(1인이면 1인으로 확정돼 혼자 뛴다). `max_player_count` 도달 여부와도 무관하다. 마감 시각은 컬럼이 아니라 계산값이라 스케줄러가 `start_at`으로 찾는다.

### running_players

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_player_id | bigint | PK | 매칭 신청·솔로 참가 단위 |
| user_id | UUID | → users, NOT NULL | 논리 참조(FK 제약 없음). 탈퇴 시 조건부 유지·삭제 — [§0](#0-공통-규칙) |
| status | enum | NOT NULL, default JOINED | 참가·진행 상태 — [§6 enum 사전](#6-enum-사전) |
| start_at | timestamp | NOT NULL | 희망 시작 시각 |
| target_distance | int | NOT NULL | 목표 거리(미터, API `targetDistanceMeters`). `running_records.total_distance`(실제 이동 거리)와 이름으로 갈린다 |
| avg_pace | int | NOT NULL | 신청 시점의 사용자 평균 페이스(초/km). **입력받지 않는다** — 매칭 조건에 페이스 항목이 없어(5-A) 서버가 `user_onboardings.avg_pace`에서 복사한다. 배정 시 방 평균과의 근접도 판정에 쓴다 |
| desired_player_count | int | nullable | **[MVP 제외]** 향후 사용자가 선택할 희망 매칭 인원 |
| created_at / updated_at | timestamp | NOT NULL | |
| deleted_at | timestamp | nullable | **신청이 끝난 시각** — 대기 취소·이탈·완주 공통. 완주도 그 신청이 끝난 것이라 찍는다. 비우면 활성 신청으로 남아 다음 매칭을 신청할 수 없다. 한 번 찍히면 바뀌지 않는다 |

> **방과의 연결은 `running_room_sessions`가 갖는다** — 참가자가 여러 방을 거칠 수 있는 설계라(방 이동은 향후 매칭 알고리즘 몫) 단일 `running_room_id` 컬럼으로는 이력을 담을 수 없고, 현재 속한 방은 `is_connected`로 가린다.
> **`status`는 참가 의사와 진행 상태를 함께 표현한다** — 신청(`JOINED`)에서 러닝(`RUNNING`)·완주(`COMPLETED`)까지 한 축으로 간다. 이탈은 시점과 제재 여부로 네 값이 갈리며, `INVITED`는 **[MVP 제외]** 예약값이다.
> **`status`와 `deleted_at`은 축이 다르다** — `status`가 "어떻게 끝났나"(사유·제재 여부), `deleted_at`이 "언제 끝났나"다. `updated_at`을 이탈 시각으로 쓰지 않는다 — 그 row가 한 번만 더 갱신돼도 값이 밀려 쿨다운이 잘못 계산된다.
> **row 생명주기**: 생성 = 매칭 신청·솔로 개시 / 대기 취소 = `deleted_at` 기록 / 러닝 시작 = 각자의 WS `RUNNING_START`가 본인을 `RUNNING`으로 전환(일괄 전환 없음) / 이탈 = `status=*_LEFT_*` + `deleted_at` 기록 / 완주 = `status=COMPLETED` + `deleted_at` 기록. 대기 취소·이탈 시 배정 행은 `is_connected=false`로 바꾸고 방 인원을 하나 줄이며, 그 결과 인원이 `0`이면 방을 닫는다 — 시작 전이면 `CANCELLED`, 시작 후면 유효 기록이 있을 때만 `FINISHED`이고 없으면 `CANCELLED`다. 친구 초대 생명주기는 MVP에서 정의하지 않는다.
> **활성 신청 판정**: `deleted_at IS NULL AND status='JOINED'`.
> **러닝 종료 판정**: 목표 거리 도달은 `COMPLETED`, 미달은 실제 거리 비율에 따라 `RUNNING_LEFT_*`다. 종료 신호·타임아웃·러닝 중 탈퇴에 같은 규칙을 적용하고, 유효 러닝 판정(거리·시간·경로 산출 가능 + 최소 거리·최소 시간 통과)을 지난 트랙만 기록으로 만든다. 산출할 수 없으면 실제 거리를 0으로 판정한다. **미달이어도 `status`는 그대로 남는다** — 기록 유무와 개인 종료 상태는 별개다.

### running_room_sessions (참가자 ↔ 방 배정)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_room_id | bigint | PK1, FK → running_rooms | 배정된 방 |
| running_player_id | bigint | PK2, FK → running_players, ON DELETE CASCADE | |
| leave_count | int | NOT NULL, default 0 | 이 방에서 이탈한 **누적** 횟수 — 방 이동(향후 매칭 알고리즘)이 생기면 같은 방을 다시 거쳐 2 이상이 될 수 있다. 배정 시 **페이스가 같은 방들의 순위를 가르는 데 쓴다** — 사람들이 잘 떠나지 않은 방이 매칭 품질이 좋다는 신호다 |
| is_connected | boolean | NOT NULL, default true | 현재 방 배정 여부이며 WebSocket 연결 상태와 무관하다. 현재 배정 중인 참가자는 행 하나만 true이고, 취소·이탈 후에는 모두 false다 |
| created_at / updated_at | timestamp | NOT NULL | `updated_at` = 마지막 배정 변동 시각(`is_connected` 전환·`leave_count` 증가). **write-once가 아니라 두 컬럼 다 둔다** — 이탈, 그리고 향후 재배정·복귀로 갱신되는 테이블이다 |

> **지금은 배정이 신청당 한 번이다** — 신청하면 방 하나에 배정되고, 현재 구현·명세에는 배정을 바꾸는 흐름이 없다. 나가면 그 행이 `is_connected=false`로 남는다.
> **스키마는 방 이동을 담을 수 있게 미리 설계돼 있다** — 매칭 알고리즘이 고도화되면 서버가 활성 신청을 더 맞는 방으로 옮겨 다니게 한다. 그때 신청이 방을 옮기면 row가 쌓여 참여 이력이 되고, 거쳐 간 방으로 돌아오면 복합 PK가 같으므로 기존 행의 `is_connected`를 되살리고 `leave_count`만 누적한다(그래서 2 이상이 될 수 있다). 이동 시 두 방의 `current_player_count`는 한 트랜잭션에서 같이 갱신한다.
> **취소 후 재신청은 별개다** — 이전에 나갔던 방도 후보에서 막지 않는다. `leave_count`가 방 순위를 낮춰 되도록 피할 뿐이다. 러닝 구간의 "의도적 이탈은 복귀 불가"는 러닝 중인 같은 신청 얘기라 둘 다와 별개다.

### running_records

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_record_id | bigint | PK | |
| running_room_id | bigint | FK → running_rooms, NOT NULL | 솔로 러닝도 방을 만드므로 항상 값이 있다 |
| user_id | UUID | → users, NOT NULL | |
| avg_pace | int | NOT NULL | 초/km |
| total_distance | int | NOT NULL | 미터. **목표 거리에서 끊는다** — 목표를 넘겨 뛰어도 `running_rooms.target_distance` 지점을 보간해 그 값으로 확정하고 `end_at`·`total_duration`도 같은 지점 기준으로 맞춘다(거리만 자르면 페이스가 틀어진다). 참가자 전원이 같은 구간 경계를 갖게 하려는 것이다 — 6-2 응답은 구간 경계를 참가자별이 아니라 구간 레벨에 둔다. 목표에 못 미치면 실제 거리를 그대로 쓴다. **S3 원본 트랙에는 목표 이후 좌표도 전부 남긴다**(재계산용) |
| total_duration | int | NOT NULL | 초. 구간(`running_splits.duration`)의 합. **일시정지 시간은 빠진다** — 멈춘 동안은 어느 구간에도 쌓이지 않으므로 `end_at - start_at`보다 작을 수 있다 |
| avg_cadence | int | nullable | spm (선택). 러닝 전체 평균 — 점별 순간 케이던스(`cadenceSpm`)는 저장하지 않는다 |
| total_elevation_gain | int | nullable | 누적 상승 고도(미터). 기기 GPS 고도를 운영 임계값으로 필터링해 계산하며 유효 표본이 부족하면 null. 구간(`running_splits.elevation_change`)의 합과는 다르다 |
| total_calories | int | NOT NULL | 종료 시 서버가 확정 거리·시간과 사용자 체중으로 계산한 kcal |
| gps_track_key | varchar | NOT NULL | S3 key — 전체 좌표·시각·기기 GPS 고도를 담은 **원본 트랙**. 재계산·분석용이라 **API 응답에는 쓰지 않는다** |
| route_polyline | text | NOT NULL | 다운샘플 경로(encoded polyline, precision 5) — **API가 내려주는 유일한 경로 데이터**. 대시보드(6-1·6-2)·기록 목록(7-1)·기록 상세(7-2)·피드 카드가 전부 이 값을 쓴다. **다운샘플 시 구간 경계점을 반드시 보존한다** — `running_splits`의 `route_start_index`·`route_end_index`가 이 배열의 위치를 가리키므로 경계가 틀어지면 구간이 어긋난다 |
| weather_code | int | NOT NULL | WMO 4677 코드(0~99) — 날씨 API 원본값 그대로. 악조건 여부는 저장하지 않고 판정 시 계산한다 |
| temperature | numeric(3,1) | NOT NULL | 섭씨. 영하 포함 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | 종료 메시지·타임아웃·탈퇴로 기록을 확정할 때 일괄 INSERT. 진행 중 PATCH 없음(write-once) |

> UNIQUE (running_room_id, user_id) — 유저당 방별 1기록. 솔로도 방을 가지므로 부분 인덱스 조건이 필요 없다.
> **개인 단위 진행 상태는 `running_players.status`가 갖는다.** 기록은 `COMPLETED`뿐 아니라 `RUNNING_LEFT_*`와도 함께 생성될 수 있으며, 기록 생성 가능한 트랙이 없으면 종료 상태만 남는다.
> 결과 조회는 해당 방에서 러닝 단계에 들어간 참가자(`RUNNING`·`COMPLETED`·`RUNNING_LEFT_*`)의 세션에 `running_records`를 LEFT JOIN한다. 시작 전 이탈자는 제외하고 기록 없는 참가자는 유지한다.

### running_splits (구간별)

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| running_split_id | bigint | PK | |
| running_record_id | bigint | FK → running_records, NOT NULL | |
| split_number | int | NOT NULL | 구간 번호(1부터). API `splitNumber` |
| avg_pace | int | NOT NULL | 초/km |
| distance | int | NOT NULL | 구간 거리(미터) — **10m 고정**. 경계는 0-10, 10-20…으로 목표 거리까지 끊고, 정확히 10m가 되도록 경계 지점을 보간해 만든다. 목표 5,000m면 구간이 500개다. 목표 미달로 끝난 참가자는 도달한 구간까지만 행이 생긴다 |
| duration | int | NOT NULL | 구간 소요 시간(초) |
| avg_cadence | int | nullable | spm (선택). 구간 평균 |
| elevation_change | int | nullable | 필터링한 기기 GPS 고도의 **순고도차**(미터) — 끝 고도 − 시작 고도라 음수가 될 수 있다. 유효 표본이 부족하면 null이며 `total_elevation_gain`과는 다른 값이다. **10m 구간에서는 대체로 null이다** — 구간에 실측점이 3~4개뿐인데 GPS 수직 오차가 수 m라 노이즈 임계값을 넘는 표본이 거의 없다 |
| calories | int | NOT NULL | 종료 시 서버가 계산한 구간 kcal |
| route_start_index | int | NOT NULL | `running_records.route_polyline`에서 이 구간이 시작하는 점 번호(0부터). 구간 경로를 텍스트로 중복 저장하지 않고 위치만 가리킨다 |
| route_end_index | int | NOT NULL | 끝나는 점 번호(포함). 구간 N의 끝점은 구간 N+1의 시작점과 같아 값이 하나 겹친다 — 한 행만 읽어도 구간을 자를 수 있게 둘 다 저장한다 |
| start_at / end_at | timestamp | NOT NULL | |
| created_at | timestamp | NOT NULL | |

> UNIQUE (running_record_id, split_number) — 기록당 구간 번호 중복 방지.
> **구간 경계는 방 전체가 공유한다.** 참가자별 실제 거리가 아니라 `running_rooms.target_distance`를 10m로 나눈 고정 경계라, 같은 방 참가자의 `split_number` N은 언제나 같은 거리 구간을 가리킨다. 6-2가 구간 하나에 참가자 여럿을 묶어 내려줄 수 있는 근거다.
> **행 수가 방마다 수천 개다** — 목표 5,000m·4인 방이면 2,000행이다. 건별 INSERT가 아니라 배치로 넣는다.
> **성립 조건은 다운샘플이 구간 경계점을 보존하는 것이다.** 서버가 Redis 버퍼에서 구간을 나누며 만드는 값이라 경계는 이미 알고 있다 — 구간별로 나눠 다운샘플한 뒤 이으면 자연히 만족한다.
> **대가는 `running_records`와의 결합이다** — `route_polyline`을 재생성하면 점 개수가 달라져 그 기록의 모든 구간 인덱스가 무효가 되므로 항상 함께 갱신한다. 둘 다 write-once라 재생성 자체가 예외적 상황이다.

---

## 3. 도메인 C — 소셜 (친구 · 피드 · 댓글)

> **`[MVP 제외]` 표기**: 지금 만들지 않는 테이블. 정의는 그대로 두어 확장 시점에 재작성 없이 쓴다. 마커가 없으면 만드는 것이다.

### friendships

| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| requester_id | UUID | PK1, FK → users | 요청을 보낸 쪽 |
| receiver_id | UUID | PK2, FK → users | 요청을 받은 쪽 |
| status | enum | NOT NULL, default PENDING | `PENDING`(수락 대기) / `ACCEPTED`(친구 성립) |
| created_at / updated_at | timestamp | NOT NULL | `updated_at` = 수락 시각. 회원탈퇴 시 CASCADE 삭제 |

> **관계는 대칭이지만 저장은 방향을 갖는다.** 누가 요청했는지 알아야 "받은 요청 목록"을 만들 수 있어 두 컬럼을 구분한다. 성립한 뒤로는 방향에 의미가 없어 친구 목록 조회는 두 컬럼 모두를 본다.
> **역방향 중복은 앱에서 막는다.** 요청 전에 `(A,B)`와 `(B,A)`를 함께 조회해, 역방향에 `PENDING`이 있으면 새 요청을 만들지 않고 **수락으로 처리**한다.
> **거절·삭제는 행을 DELETE한다** — 이력을 보관하지 않는다.
> **친구 수는 집계 테이블 없이 `COUNT`로 구한다** — `status='ACCEPTED'`이면서 두 컬럼 중 하나가 본인인 행을 센다.

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

FK 강제 없는 독립 테이블(원본 삭제/수정된 row를 참조하므로 FK 미설정). 컬럼은 스냅샷 당시 값 그대로, `created_at`(NOT NULL) = 스냅샷 시각.

> **스냅샷은 앱이 남긴다.** `ON DELETE CASCADE`는 DB가 처리하므로 애플리케이션을 거치지 않는다 — 탈퇴로 지워지는 row를 남기려면 탈퇴 유스케이스에서 **명시적으로 INSERT**해야 한다.

### delete_users

회원탈퇴 스냅샷(최소 정보만).
| 컬럼 | 타입 | 제약 | 비고 |
|---|---|---|---|
| user_id | UUID | PK, → users | 탈퇴 유저 |
| email | varchar | | |
| alert_consent | boolean | | |
| created_at | timestamp | NOT NULL | 스냅샷 시각 |

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
| users.profile_visibility | FRIENDS / PUBLIC | 지인 마스킹 — FRIENDS는 `friendships`로 직접 판정 |
| friendships.status | PENDING / ACCEPTED | 수락 대기 / 친구 성립 — 거절은 값이 아니라 row DELETE |
| colors.category | DISTANCE / SPEED / ENDURANCE / CONSISTENCY / CADENCE / INTERVAL / EVEN_PACE / HILLS / RECOVERY / COMPANY / ADVERSITY / MILESTONE | 12범주 — 거리 / 속도 / 지구력 / 꾸준함 / 케이던스 / 인터벌 / 균등페이스 / 언덕 / 회복 / 동행 / 악조건극복 / 이정표 |
| user_onboardings.gender | MALE / FEMALE | |
| user_devices.platform | IOS / ANDROID | |
| running_players.status | INVITED / JOINED / MATCHED_LEFT_PENALTY / MATCHED_LEFT_NO_PENALTY / RUNNING / RUNNING_LEFT_PENALTY / RUNNING_LEFT_NO_PENALTY / COMPLETED | `INVITED`는 **[MVP 제외]** 예약값. 나머지는 참가 / 확정 후 이탈(제재·미제재) / 러닝 중 / 러닝 중 이탈(제재·미제재) / 완주 |
| running_rooms.type | SOLO / MATCH / INVITE | 솔로 러닝 / 랜덤 매칭 / 친구 초대. `INVITE`는 **[MVP 제외]** 예약값 |
| running_rooms.status | MATCHING / MATCHED / STARTED / FINISHED / CANCELLED | 모집 중(마감 전) / 마감 시점 확정(인원 무관, 1인도 확정) / 시작 / **유효 기록을 남기고** 종료 / 남길 기록 없이 방이 빔 — 시작 전이면 항상, 시작 후면 유효 기록이 하나도 없을 때 |
| oauth_users.provider | GOOGLE / KAKAO | |

---

## 7. 인덱스 (조회 성능)

> 복합 PK는 첫 컬럼 조회를 커버한다(`user_colors`·`feed_likes`·`comment_likes`는 별도 불필요). `running_splits (running_record_id, split_number)` UNIQUE도 마찬가지. `colors`는 마스터라 전체 조회만 하므로 인덱스가 없다.

| 인덱스 대상 | 용도 |
|---|---|
| user_devices.user_id | 푸시 발송 — 유저의 활성 기기 조회 |
| friendships.receiver_id | 받은 요청·친구 목록 (PK는 requester_id만 커버) |
| feeds.user_id | **[MVP 제외]** 프로필 피드 그리드·내 피드 |
| feeds.created_at | **[MVP 제외]** 피드 타임라인 최신순 정렬 |
| feed_images.feed_id | **[MVP 제외]** 피드 이미지 조회 |
| comments.feed_id | **[MVP 제외]** 댓글 목록 |
| comments.parent_comment_id | **[MVP 제외]** 답글 지연 로딩 |
| running_records.user_id | 내 기록 조회 |
| running_records.running_room_id | 방 결과 조회 |
| running_room_sessions.running_player_id | 참가자의 현재 방 조회 (복합 PK가 `running_room_id` 방향만 커버) |
| running_rooms.(deleted_at, type, status, start_at, target_distance, avg_pace) | 매칭 후보 방 조회 — 같은 슬롯·거리에서 모집 중인 방(`type='MATCH' AND status='MATCHING'`) + 페이스 근접(±30초/km) 판정. 솔로 방·초대방을 인덱스 단계에서 배제한다. 모집 마감 스케줄러도 앞 4개 컬럼을 그대로 탄다 |
| running_players.(user_id, deleted_at) | 활성 신청 조회 — 중복 신청 검사·내 매칭 상태·러닝 시작. 페널티 판정(최근 제재 이탈 조회)도 이 인덱스를 탄다 |
