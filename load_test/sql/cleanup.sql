-- 부하 테스트가 만든 데이터를 전부 지운다 — seed.sql의 식별자 범위만 건드린다.
-- 방·신청은 900000000 이상, 사용자는 00000000-0000-7000-8000- 접두어다.
-- Redis 키(running:track:9…, running:combo:9…)는 TTL(7h)로 사라지므로 여기서 지우지 않는다.

\set ON_ERROR_STOP on

BEGIN;

DELETE FROM running_combos WHERE running_room_id >= 900000000;

-- 구간은 경로 조각에, 조각은 기록에 붙는다 — 자식부터 지운다
DELETE FROM running_splits
WHERE running_segment_id IN (
    SELECT running_segment_id FROM running_segments
    WHERE running_record_id IN (
        SELECT running_record_id FROM running_records WHERE running_room_id >= 900000000
    )
);

DELETE FROM running_segments
WHERE running_record_id IN (
    SELECT running_record_id FROM running_records WHERE running_room_id >= 900000000
);

DELETE FROM running_records WHERE running_room_id >= 900000000;
DELETE FROM running_room_sessions WHERE running_room_id >= 900000000;
DELETE FROM running_rooms WHERE running_room_id >= 900000000;
DELETE FROM running_players WHERE running_player_id >= 900000000;

DELETE FROM user_onboardings WHERE user_id::text LIKE '00000000-0000-7000-8000-%';
DELETE FROM users WHERE user_id::text LIKE '00000000-0000-7000-8000-%';

COMMIT;
