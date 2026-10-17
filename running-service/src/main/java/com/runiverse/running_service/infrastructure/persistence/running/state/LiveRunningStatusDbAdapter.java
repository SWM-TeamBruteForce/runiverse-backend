package com.runiverse.running_service.infrastructure.persistence.running.state;

import com.runiverse.running_service.application.running.port.out.ChangeLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import com.runiverse.running_service.application.running.port.out.LoadLiveRunningStatusPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore.DB;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.STATUS;

// 실험용(state-db) — LiveRunningStatusRedisAdapter와 같은 일을 RDS에서 한다. 운영에 넣지 않는다.
// 프로필을 켜면 Redis 어댑터도 함께 뜬다 — @Primary로 이쪽이 주입된다
@Component
@Profile("state-db")
@Primary
@RequiredArgsConstructor
public class LiveRunningStatusDbAdapter implements ChangeLiveRunningStatusPort, LoadLiveRunningStatusPort {

    // Redis의 Lua와 같은 일을 한 문장으로 한다 — 바꾸기 전 값을 잠그고 읽어 돌려주고,
    // 허용된 이전 상태일 때만 덮는다. 상태가 없던 참가자는 '없음에서 바꿀 수 있는 상태'일 때만 만든다
    private static final String CHANGE = """
            WITH previous AS (
                SELECT status FROM running_state_status
                WHERE running_room_id = ? AND user_id = ?
                FOR UPDATE
            ), changed AS (
                INSERT INTO running_state_status (running_room_id, user_id, status)
                SELECT ?, ?, ?
                WHERE ? OR EXISTS (SELECT 1 FROM previous)
                ON CONFLICT (running_room_id, user_id) DO UPDATE SET status = EXCLUDED.status
                WHERE running_state_status.status = ANY (string_to_array(?, ','))
            )
            SELECT status FROM previous
            """;
    private static final String SELECT = """
            SELECT status FROM running_state_status WHERE running_room_id = ? AND user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final RunningStateMeter stateMeter;

    // DB 오류는 잡지 않는다 — Redis 어댑터와 같이 받는 쪽이 로그를 남기고 넘어간다
    @Override
    public LiveRunningStatusChange change(Long runningRoomId, UserId userId, LiveRunningStatus status) {
        RunningStatePhase phase = status == LiveRunningStatus.FINISHED
                ? RunningStatePhase.FINISH
                : RunningStatePhase.RUNNING;
        String allowedPrevious = Arrays.stream(LiveRunningStatus.values())
                .filter(status::canChangeFrom)
                .map(Enum::name)
                .collect(Collectors.joining(","));
        String previous = stateMeter.write(DB, STATUS, phase, runningRoomId, () -> jdbcTemplate.query(CHANGE,
                rs -> rs.next() ? rs.getString(1) : null,
                runningRoomId, userId.value(),
                runningRoomId, userId.value(), status.name(),
                status.canChangeFrom(null),
                allowedPrevious));
        return LiveRunningStatusChange.of(toStatus(previous), status);
    }

    @Override
    public Optional<LiveRunningStatus> load(Long runningRoomId, UserId userId) {
        return Optional.ofNullable(toStatus(stateMeter.read(DB, STATUS, RunningStatePhase.RUNNING,
                runningRoomId, () -> jdbcTemplate.query(SELECT,
                        rs -> rs.next() ? rs.getString(1) : null,
                        runningRoomId, userId.value()))));
    }

    private static LiveRunningStatus toStatus(String raw) {
        return raw == null || raw.isEmpty() ? null : LiveRunningStatus.valueOf(raw);
    }
}
