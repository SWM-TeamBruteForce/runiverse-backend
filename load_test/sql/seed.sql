-- k6 러닝 부하 테스트용 시드 — 매칭이 끝난 4인 방을 rooms개 만든다.
-- 매칭 흐름(SSE·예약 작업)을 거치지 않고 "시작 시각이 지난 MATCHED 방"을 바로 만든다.
-- 사용 예: psql -v run_id=1 -v rooms=1 -f seed.sql
--
-- 식별자 규칙 — k6 스크립트(k6/lib/ids.js)와 반드시 같아야 한다.
--   사용자 u(0부터): user_id = 00000000-0000-7000-8000-{u를 12자리로}
--   방 r = u / 4:    running_room_id   = 900000000 + run_id * 10000  + r
--   참가 신청:       running_player_id = 900000000 + run_id * 100000 + u
-- run_id를 회차마다 바꾸면 방 번호가 달라져 이전 회차의 Redis 키(TTL 7h)와 겹치지 않는다.

\set ON_ERROR_STOP on

\if :{?run_id}
\else
  \set run_id 1
\endif
\if :{?rooms}
\else
  \set rooms 1
\endif
\if :{?target_distance}
\else
  \set target_distance 5000
\endif
\if :{?tz}
\else
  \set tz 'Asia/Seoul'
\endif

BEGIN;

-- 이전 회차가 중간에 끊겨 활성 신청이 남아 있으면 START가 그 신청을 집을 수 있다 — 먼저 닫는다
UPDATE running_players
SET status = 'RUNNING_LEFT_NO_PENALTY',
    deleted_at = now() AT TIME ZONE :'tz',
    updated_at = now() AT TIME ZONE :'tz'
WHERE user_id::text LIKE '00000000-0000-7000-8000-%'
  AND deleted_at IS NULL;

UPDATE running_room_sessions
SET is_connected = false,
    updated_at = now() AT TIME ZONE :'tz'
WHERE user_id::text LIKE '00000000-0000-7000-8000-%'
  AND is_connected;

-- 사용자·온보딩은 회차와 무관하게 재사용한다
INSERT INTO users (user_id, email, password_hash, alert_consent, profile_visibility,
                   profile_image_key, introduction, created_at, updated_at)
SELECT ('00000000-0000-7000-8000-' || lpad(u::text, 12, '0'))::uuid,
       'loadtest-' || u || '@loadtest.runiverse',
       NULL, false, 'PUBLIC', NULL, NULL,
       now() AT TIME ZONE :'tz', now() AT TIME ZONE :'tz'
FROM generate_series(0, :rooms * 4 - 1) AS u
ON CONFLICT DO NOTHING;

INSERT INTO user_onboardings (user_id, nickname, gender, birthday, avg_pace, weight, height,
                              created_at, updated_at)
SELECT ('00000000-0000-7000-8000-' || lpad(u::text, 12, '0'))::uuid,
       'lt' || lpad(u::text, 6, '0'),
       'MALE', DATE '1995-01-01', 360, 70.0, 175.0,
       now() AT TIME ZONE :'tz', now() AT TIME ZONE :'tz'
FROM generate_series(0, :rooms * 4 - 1) AS u
ON CONFLICT DO NOTHING;

-- 시작 시각을 1분 전으로 둬야 RUNNING_START의 시각 검사를 통과한다
INSERT INTO running_rooms (running_room_id, type, status, start_at, close_at, target_distance,
                           avg_pace, max_player_count, current_player_count,
                           created_at, updated_at, deleted_at)
OVERRIDING SYSTEM VALUE
SELECT 900000000 + :run_id * 10000 + r,
       'MATCH', 'MATCHED',
       now() AT TIME ZONE :'tz' - interval '1 minute', NULL,
       :target_distance, 360, 4, 4,
       now() AT TIME ZONE :'tz', now() AT TIME ZONE :'tz', NULL
FROM generate_series(0, :rooms - 1) AS r;

INSERT INTO running_players (running_player_id, user_id, status, start_at, target_distance,
                             avg_pace, desired_player_count, created_at, updated_at, deleted_at)
OVERRIDING SYSTEM VALUE
SELECT 900000000 + :run_id * 100000 + u,
       ('00000000-0000-7000-8000-' || lpad(u::text, 12, '0'))::uuid,
       'JOINED',
       now() AT TIME ZONE :'tz' - interval '1 minute',
       :target_distance, 360, 4,
       now() AT TIME ZONE :'tz', now() AT TIME ZONE :'tz', NULL
FROM generate_series(0, :rooms * 4 - 1) AS u;

INSERT INTO running_room_sessions (running_room_id, user_id, running_player_id, leave_count,
                                   is_connected, created_at, updated_at)
SELECT 900000000 + :run_id * 10000 + u / 4,
       ('00000000-0000-7000-8000-' || lpad(u::text, 12, '0'))::uuid,
       900000000 + :run_id * 100000 + u,
       0, true,
       now() AT TIME ZONE :'tz', now() AT TIME ZONE :'tz'
FROM generate_series(0, :rooms * 4 - 1) AS u;

COMMIT;

SELECT :run_id AS run_id, :rooms AS rooms, :rooms * 4 AS runners;
