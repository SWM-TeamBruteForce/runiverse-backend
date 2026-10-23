package com.runiverse.running_service.infrastructure.persistence.running.state;

import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.RUNNING;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore.DB;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.DISTANCE;

// 실험용(state-db) — RunningDistanceRedisAdapter와 같은 일을 RDS에서 한다. 운영에 넣지 않는다
@Slf4j
@Component
@Profile("state-db")
@Primary
@RequiredArgsConstructor
public class RunningDistanceDbAdapter implements LoadRunningDistancePort, SaveRunningDistancePort {

    private static final String SELECT = """
            SELECT meters, last_sequence, last_pause_segment, last_latitude, last_longitude, last_pace,
                   anchor_latitude, anchor_longitude
            FROM running_state_distance
            WHERE running_room_id = ? AND user_id = ?
            """;
    private static final String UPSERT = """
            INSERT INTO running_state_distance (running_room_id, user_id, meters, last_sequence,
                                                last_pause_segment, last_latitude, last_longitude,
                                                last_pace, anchor_latitude, anchor_longitude)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (running_room_id, user_id) DO UPDATE SET
                meters = EXCLUDED.meters,
                last_sequence = EXCLUDED.last_sequence,
                last_pause_segment = EXCLUDED.last_pause_segment,
                last_latitude = EXCLUDED.last_latitude,
                last_longitude = EXCLUDED.last_longitude,
                last_pace = EXCLUDED.last_pace,
                anchor_latitude = EXCLUDED.anchor_latitude,
                anchor_longitude = EXCLUDED.anchor_longitude
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RunningStateMeter stateMeter;

    // Redis 어댑터와 같이 읽기 실패는 던진다 — 빈 값으로 위장하면 살아 있는 누적이 덮인다
    @Override
    public RunningDistance loadDistance(Long runningRoomId, UserId userId) {
        return stateMeter.read(DB, DISTANCE, RUNNING, runningRoomId, () -> jdbcTemplate.query(SELECT,
                rs -> rs.next()
                        ? new RunningDistance(
                        rs.getDouble("meters"),
                        rs.getLong("last_sequence"),
                        rs.getInt("last_pause_segment"),
                        rs.getObject("last_latitude", Double.class),
                        rs.getObject("last_longitude", Double.class),
                        rs.getObject("last_pace", Integer.class),
                        rs.getObject("anchor_latitude", Double.class),
                        rs.getObject("anchor_longitude", Double.class))
                        : RunningDistance.empty(),
                runningRoomId, userId.value()));
    }

    @Override
    public void saveDistance(Long runningRoomId, UserId userId, RunningDistance distance) {
        try {
            stateMeter.write(DB, DISTANCE, RUNNING, runningRoomId, () -> jdbcTemplate.update(UPSERT,
                    runningRoomId, userId.value(),
                    distance.meters(), distance.lastSequence(), distance.lastPauseSegment(),
                    distance.lastLatitude(), distance.lastLongitude(), distance.lastPaceSecondsPerKm(),
                    distance.anchorLatitude(), distance.anchorLongitude()));
        } catch (RuntimeException e) {
            // Redis 어댑터와 같다 — 다음 배치가 이전 누적에서 이어 간다
            log.error("[러닝] 누적 거리 저장 실패: DB 오류 - roomId={}, userId={}", runningRoomId, userId.value(), e);
        }
    }
}
