-- state-db 프로필(실험 브랜치)이 쓰는 러닝 중 상태 테이블 — Redis 어댑터 4개와 같은 일을 RDS에서 한다.
-- JPA 엔티티가 아니라 JdbcTemplate으로 다루므로 JPA_DDL=update가 만들지 않는다. 측정 전에 한 번 실행한다.
-- 시각은 Redis 저장 형식과 같게 epoch millis(bigint)로 둔다.
-- 지우기: 맨 아래 DROP 블록의 주석을 풀어 실행한다.

\set ON_ERROR_STOP on

-- 좌표 버퍼 — 좌표 하나가 한 행, (방, 사용자, 순번)이 키라 재전송 중복은 ON CONFLICT DO NOTHING이 거른다.
-- point는 Redis와 같은 압축 배열 문자열이다 — 종료 때 읽기·복원 비용을 같은 조건으로 비교한다
CREATE TABLE IF NOT EXISTS running_state_track_point (
    running_room_id bigint NOT NULL,
    user_id         uuid   NOT NULL,
    sequence        bigint NOT NULL,
    point           text   NOT NULL,
    PRIMARY KEY (running_room_id, user_id, sequence)
);

-- 누적 거리 — 참가자당 한 행 UPSERT
CREATE TABLE IF NOT EXISTS running_state_distance (
    running_room_id  bigint           NOT NULL,
    user_id          uuid             NOT NULL,
    meters           double precision NOT NULL,
    last_sequence    bigint           NOT NULL,
    last_latitude    double precision,
    last_longitude   double precision,
    last_pace        integer,
    anchor_latitude  double precision,
    anchor_longitude double precision,
    PRIMARY KEY (running_room_id, user_id)
);

-- 참가자 화면 상태 — 허용된 이전 상태일 때만 바꾼다
CREATE TABLE IF NOT EXISTS running_state_status (
    running_room_id bigint      NOT NULL,
    user_id         uuid        NOT NULL,
    status          varchar(20) NOT NULL,
    PRIMARY KEY (running_room_id, user_id)
);

-- 콤보 스냅샷 — 일시정지·종료 표시만 먼저 생길 수 있어 스냅샷 칸은 비어 있을 수 있다
CREATE TABLE IF NOT EXISTS running_state_combo_snapshot (
    running_room_id bigint NOT NULL,
    user_id         uuid   NOT NULL,
    meters          double precision,
    recorded_at     bigint,
    speed           double precision,
    moved_at        bigint,
    observed_at     bigint,
    paused_at       bigint,
    finished_at     bigint,
    PRIMARY KEY (running_room_id, user_id)
);


-- 콤보 관계 — 정렬된 쌍당 한 행
CREATE TABLE IF NOT EXISTS running_state_combo_pair (
    running_room_id bigint  NOT NULL,
    first_user_id   uuid    NOT NULL,
    second_user_id  uuid    NOT NULL,
    started_at      bigint,
    accrued_at      bigint,
    max_combo       integer NOT NULL,
    miss_left       integer NOT NULL,
    PRIMARY KEY (running_room_id, first_user_id, second_user_id)
);

-- 끊긴 콤보 구간 — 같은 쌍이 같은 시작 시각을 두 번 끊어도 한 행을 덮어쓴다
CREATE TABLE IF NOT EXISTS running_state_combo_segment (
    running_room_id bigint  NOT NULL,
    first_user_id   uuid    NOT NULL,
    second_user_id  uuid    NOT NULL,
    start_at        bigint  NOT NULL,
    end_at          bigint  NOT NULL,
    combo_count     integer NOT NULL,
    PRIMARY KEY (running_room_id, first_user_id, second_user_id, start_at)
);

-- DROP TABLE IF EXISTS running_state_track_point, running_state_distance, running_state_status,
--     running_state_combo_snapshot, running_state_combo_pair, running_state_combo_segment;
