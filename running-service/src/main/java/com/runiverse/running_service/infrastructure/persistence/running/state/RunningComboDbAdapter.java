package com.runiverse.running_service.infrastructure.persistence.running.state;

import com.runiverse.running_service.application.running.port.out.DeleteRunningComboStatePort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboHistoryPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboFinishedPort;
import com.runiverse.running_service.application.running.port.out.MarkRunningComboPausedPort;
import com.runiverse.running_service.application.running.port.out.RunningComboHistory;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSegment;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSegmentsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.FINISH;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.RUNNING;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore.DB;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.COMBO_PAIR;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.COMBO_SEGMENT;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.COMBO_SNAPSHOT;

// 실험용(state-db) — RunningComboRedisAdapter와 같은 일을 RDS에서 한다. 운영에 넣지 않는다.
// Redis 해시 셋(스냅샷·관계·구간)이 테이블 셋이 된다. 시각은 Redis와 같게 epoch millis로 둔다
@Slf4j
@Component
@Profile("state-db")
@Primary
@RequiredArgsConstructor
public class RunningComboDbAdapter implements
        LoadRunningComboSnapshotsPort,
        SaveRunningComboSnapshotPort,
        LoadRunningComboPairsPort,
        SaveRunningComboPairsPort,
        SaveRunningComboSegmentsPort,
        MarkRunningComboPausedPort,
        MarkRunningComboFinishedPort,
        LoadRunningComboHistoryPort,
        DeleteRunningComboStatePort {

    // 일시정지·종료 표시만 있는 참가자는 아직 판정에 들어온 적이 없다 — Redis 어댑터처럼 비교 대상에서 뺀다
    private static final String SELECT_SNAPSHOTS = """
            SELECT user_id, meters, recorded_at, speed, moved_at, observed_at, paused_at, finished_at
            FROM running_state_combo_snapshot
            WHERE running_room_id = ? AND recorded_at IS NOT NULL
            """;
    // 일시정지·종료 칸은 건드리지 않는다 — 그 이벤트와 위치 배치가 같은 값을 고쳐 쓰지 않는다
    private static final String UPSERT_SNAPSHOT = """
            INSERT INTO running_state_combo_snapshot (running_room_id, user_id, meters, recorded_at,
                                                      speed, moved_at, observed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (running_room_id, user_id) DO UPDATE SET
                meters = EXCLUDED.meters,
                recorded_at = EXCLUDED.recorded_at,
                speed = EXCLUDED.speed,
                moved_at = EXCLUDED.moved_at,
                observed_at = EXCLUDED.observed_at
            """;
    private static final String MARK_PAUSED = """
            INSERT INTO running_state_combo_snapshot (running_room_id, user_id, paused_at)
            VALUES (?, ?, ?)
            ON CONFLICT (running_room_id, user_id) DO UPDATE SET paused_at = EXCLUDED.paused_at
            """;
    private static final String CLEAR_PAUSED = """
            UPDATE running_state_combo_snapshot SET paused_at = NULL
            WHERE running_room_id = ? AND user_id = ?
            """;
    // 지우는 짝이 없다 — 방이 닫힐 때 deleteComboState가 행째 지운다
    private static final String MARK_FINISHED = """
            INSERT INTO running_state_combo_snapshot (running_room_id, user_id, finished_at)
            VALUES (?, ?, ?)
            ON CONFLICT (running_room_id, user_id) DO UPDATE SET finished_at = EXCLUDED.finished_at
            """;
    private static final String SELECT_PAIRS = """
            SELECT first_user_id, second_user_id, started_at, accrued_at, max_combo, miss_left
            FROM running_state_combo_pair
            WHERE running_room_id = ?
            """;
    private static final String UPSERT_PAIR = """
            INSERT INTO running_state_combo_pair (running_room_id, first_user_id, second_user_id,
                                                  started_at, accrued_at, max_combo, miss_left)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (running_room_id, first_user_id, second_user_id) DO UPDATE SET
                started_at = EXCLUDED.started_at,
                accrued_at = EXCLUDED.accrued_at,
                max_combo = EXCLUDED.max_combo,
                miss_left = EXCLUDED.miss_left
            """;
    private static final String SELECT_SEGMENTS = """
            SELECT first_user_id, second_user_id, start_at, end_at, combo_count
            FROM running_state_combo_segment
            WHERE running_room_id = ?
            """;
    private static final String UPSERT_SEGMENT = """
            INSERT INTO running_state_combo_segment (running_room_id, first_user_id, second_user_id,
                                                     start_at, end_at, combo_count)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (running_room_id, first_user_id, second_user_id, start_at) DO UPDATE SET
                end_at = EXCLUDED.end_at,
                combo_count = EXCLUDED.combo_count
            """;
    private static final List<String> DELETE_ALL = List.of(
            "DELETE FROM running_state_combo_snapshot WHERE running_room_id = ?",
            "DELETE FROM running_state_combo_pair WHERE running_room_id = ?",
            "DELETE FROM running_state_combo_segment WHERE running_room_id = ?");

    private final JdbcTemplate jdbcTemplate;
    private final RunningStateMeter stateMeter;

    // 읽기 실패는 던진다 — 빈 목록으로 위장하면 살아 있던 콤보가 이번 배치의 저장으로 지워진다
    @Override
    public List<RunningComboSnapshot> loadSnapshots(Long runningRoomId) {
        return stateMeter.read(DB, COMBO_SNAPSHOT, RUNNING, runningRoomId,
                () -> jdbcTemplate.query(SELECT_SNAPSHOTS, SNAPSHOT, runningRoomId));
    }

    @Override
    public void saveSnapshot(Long runningRoomId, RunningComboSnapshot snapshot) {
        save(COMBO_SNAPSHOT, runningRoomId, () -> jdbcTemplate.update(UPSERT_SNAPSHOT,
                runningRoomId, snapshot.userId().value(),
                snapshot.meters(), millis(snapshot.recordedAt()), snapshot.speedMetersPerSecond(),
                millis(snapshot.movedAt()), millis(snapshot.observedAt())));
    }

    // 잡지 않고 던진다 — 부르는 쪽이 로그를 남긴다
    @Override
    public void markPaused(Long runningRoomId, UserId userId) {
        stateMeter.write(DB, COMBO_SNAPSHOT, RUNNING, runningRoomId, () -> jdbcTemplate.update(MARK_PAUSED,
                runningRoomId, userId.value(), Instant.now().toEpochMilli()));
    }

    @Override
    public void clearPaused(Long runningRoomId, UserId userId) {
        stateMeter.write(DB, COMBO_SNAPSHOT, RUNNING, runningRoomId,
                () -> jdbcTemplate.update(CLEAR_PAUSED, runningRoomId, userId.value()));
    }

    // 잡지 않고 던진다 — 부르는 쪽이 로그를 남긴다
    @Override
    public void markFinished(Long runningRoomId, UserId userId) {
        stateMeter.write(DB, COMBO_SNAPSHOT, RUNNING, runningRoomId, () -> jdbcTemplate.update(MARK_FINISHED,
                runningRoomId, userId.value(), Instant.now().toEpochMilli()));
    }

    @Override
    public List<RunningComboPair> loadPairs(Long runningRoomId) {
        return stateMeter.read(DB, COMBO_PAIR, RUNNING, runningRoomId,
                () -> jdbcTemplate.query(SELECT_PAIRS, PAIR, runningRoomId));
    }

    @Override
    public void savePairs(Long runningRoomId, List<RunningComboPair> pairs) {
        if (pairs.isEmpty()) {
            return;
        }
        // 여러 쌍을 한 번의 왕복(배치)으로 쓴다 — Redis의 HSET 여러 칸 + EXPIRE와 같은 한 건이다
        save(COMBO_PAIR, runningRoomId, () -> jdbcTemplate.batchUpdate(UPSERT_PAIR, pairs.stream()
                .map(pair -> new Object[]{
                        runningRoomId, pair.first().value(), pair.second().value(),
                        millis(pair.startedAt()), millis(pair.accruedAt()),
                        pair.maxComboCount(), pair.missLeft()})
                .toList()));
    }

    // 잡지 않고 던진다 — 판정 쪽이 관계 저장을 건너뛰고 로그를 남긴다
    @Override
    public void saveSegments(Long runningRoomId, List<RunningComboSegment> segments) {
        if (segments.isEmpty()) {
            return;
        }
        stateMeter.write(DB, COMBO_SEGMENT, RUNNING, runningRoomId, () -> jdbcTemplate.batchUpdate(
                UPSERT_SEGMENT, segments.stream()
                        .map(segment -> new Object[]{
                                runningRoomId, segment.first().value(), segment.second().value(),
                                millis(segment.startAt()), millis(segment.endAt()), segment.comboCount()})
                        .toList()));
    }

    // 방이 닫힐 때 한 번 — 구간과 관계를 함께 읽어 한 건으로 잰다(Redis 어댑터와 같은 범위)
    @Override
    public RunningComboHistory loadHistory(Long runningRoomId) {
        return stateMeter.load(DB, COMBO_SEGMENT, runningRoomId,
                () -> new RunningComboHistory(
                        jdbcTemplate.query(SELECT_SEGMENTS, SEGMENT, runningRoomId),
                        jdbcTemplate.query(SELECT_PAIRS, PAIR, runningRoomId).stream()
                                .filter(RunningComboPair::inCombo)
                                .toList()),
                history -> history.closedSegments().size() + history.openPairs().size());
    }

    @Override
    public void deleteComboState(Long runningRoomId) {
        try {
            stateMeter.write(DB, COMBO_SEGMENT, FINISH, runningRoomId,
                    () -> DELETE_ALL.forEach(sql -> jdbcTemplate.update(sql, runningRoomId)));
        } catch (RuntimeException e) {
            log.error("[러닝] 콤보 상태 삭제 실패: DB 오류 - roomId={}", runningRoomId, e);
        }
    }

    private void save(RunningStateTarget target, Long runningRoomId, Runnable access) {
        try {
            stateMeter.write(DB, target, RUNNING, runningRoomId, access);
        } catch (RuntimeException e) {
            // Redis 어댑터와 같다 — 다음 배치가 이전 상태에서 이어 판정한다
            log.error("[러닝] 콤보 상태 저장 실패: DB 오류 - roomId={}, target={}",
                    runningRoomId, target.name().toLowerCase(Locale.ROOT), e);
        }
    }

    private static final RowMapper<RunningComboSnapshot> SNAPSHOT = (rs, rowNum) -> new RunningComboSnapshot(
            userId(rs, "user_id"),
            rs.getDouble("meters"),
            instant(rs, "recorded_at"),
            rs.getDouble("speed"),
            instant(rs, "moved_at"),
            instant(rs, "observed_at"),
            rs.getObject("paused_at", Long.class) != null,
            rs.getObject("finished_at", Long.class) != null);

    private static final RowMapper<RunningComboPair> PAIR = (rs, rowNum) -> new RunningComboPair(
            userId(rs, "first_user_id"),
            userId(rs, "second_user_id"),
            instant(rs, "started_at"),
            instant(rs, "accrued_at"),
            rs.getInt("max_combo"),
            rs.getInt("miss_left"));

    private static final RowMapper<RunningComboSegment> SEGMENT = (rs, rowNum) -> new RunningComboSegment(
            userId(rs, "first_user_id"),
            userId(rs, "second_user_id"),
            instant(rs, "start_at"),
            instant(rs, "end_at"),
            rs.getInt("combo_count"));

    private static UserId userId(ResultSet rs, String column) throws SQLException {
        return new UserId(rs.getObject(column, UUID.class));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Long millis = rs.getObject(column, Long.class);
        return millis == null ? null : Instant.ofEpochMilli(millis);
    }

    private static Long millis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
