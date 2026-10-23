package com.runiverse.running_service.infrastructure.persistence.running.state;

import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.metrics.running.RunningStateMeter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.FINISH;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStatePhase.RUNNING;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateStore.DB;
import static com.runiverse.running_service.infrastructure.metrics.running.RunningStateTarget.TRACK;

// 실험용(state-db) — RunningTrackRedisAdapter와 같은 일을 RDS에서 한다. 운영에 넣지 않는다.
// 좌표 문자열 형식(compact/parse)은 Redis 어댑터를 그대로 옮겼다 — 읽기·복원 비용을 같은 조건으로 비교한다
@Slf4j
@Component
@Profile("state-db")
@Primary
@RequiredArgsConstructor
public class RunningTrackDbAdapter implements AppendRunningTrackPort, LoadRunningTrackPort, DeleteRunningTrackPort {

    // 배치 하나를 한 문장으로 넣는다. 순번이 키라 재전송분은 ON CONFLICT가 거르고,
    // 갱신 행 수가 곧 새로 들어간 좌표 수다(Redis의 SETBIT 판정과 같은 결과)
    private static final String APPEND = """
            INSERT INTO running_state_track_point (running_room_id, user_id, sequence, point)
            SELECT ?, ?, t.sequence, t.point
            FROM unnest(?::bigint[], ?::text[]) AS t(sequence, point)
            WHERE t.sequence BETWEEN 0 AND ?
            ON CONFLICT (running_room_id, user_id, sequence) DO NOTHING
            """;
    private static final String SELECT = """
            SELECT point FROM running_state_track_point
            WHERE running_room_id = ? AND user_id = ?
            ORDER BY sequence
            """;
    private static final String DELETE = """
            DELETE FROM running_state_track_point WHERE running_room_id = ? AND user_id = ?
            """;
    private static final Pattern POINT = Pattern.compile("\\[([^\\[\\]]+)]");

    private final JdbcTemplate jdbcTemplate;
    private final RunningStateMeter stateMeter;

    @Override
    public int append(Long runningRoomId, UserId userId, List<TrackPoint> points) {
        Long[] sequences = points.stream().map(TrackPoint::sequence).toArray(Long[]::new);
        String[] compacted = points.stream().map(RunningTrackDbAdapter::compact).toArray(String[]::new);
        try {
            return stateMeter.write(DB, TRACK, RUNNING, runningRoomId, () -> jdbcTemplate.update(connection -> {
                PreparedStatement statement = connection.prepareStatement(APPEND);
                statement.setLong(1, runningRoomId);
                statement.setObject(2, userId.value());
                statement.setArray(3, connection.createArrayOf("bigint", sequences));
                statement.setArray(4, connection.createArrayOf("text", compacted));
                statement.setLong(5, TrackPoint.MAX_SEQUENCE);
                return statement;
            }));
        } catch (RuntimeException e) {
            // Redis 어댑터와 같다 — 원본은 클라 로컬 트랙에 남아 있어 재연결하면 다시 온다
            log.error("[러닝] 트랙 저장 실패: DB 오류 - roomId={}, userId={}", runningRoomId, userId.value(), e);
            throw new RunningTrackUnavailableException();
        }
    }

    // 종료 때 러닝 하나치를 순번 순으로 읽는다 — 가져오기와 복원을 함께 잰다(Redis 어댑터와 같은 범위)
    @Override
    public RunningTrack load(Long runningRoomId, UserId userId) {
        return stateMeter.load(DB, TRACK, runningRoomId,
                () -> fetch(runningRoomId, userId), track -> track.points().size());
    }

    private RunningTrack fetch(Long runningRoomId, UserId userId) {
        List<String> points;
        try {
            points = jdbcTemplate.queryForList(SELECT, String.class, runningRoomId, userId.value());
        } catch (RuntimeException e) {
            log.error("[러닝] 트랙 조회 실패: DB 오류 - roomId={}, userId={}", runningRoomId, userId.value(), e);
            throw new RunningTrackUnavailableException();
        }
        if (points.isEmpty()) {
            return new RunningTrack("[]", List.of());
        }
        // 순번 순으로 읽었으니 다시 정렬할 필요가 없다
        String raw = "[" + String.join(",", points) + "]";
        return new RunningTrack(raw, parse(raw));
    }

    @Override
    public void delete(Long runningRoomId, UserId userId) {
        try {
            stateMeter.write(DB, TRACK, FINISH, runningRoomId,
                    () -> jdbcTemplate.update(DELETE, runningRoomId, userId.value()));
        } catch (RuntimeException e) {
            log.error("[러닝] 트랙 삭제 실패: DB 오류 - roomId={}, userId={}", runningRoomId, userId.value(), e);
        }
    }

    // RunningTrackRedisAdapter.compact()와 같은 형식이다
    private static String compact(TrackPoint point) {
        return String.format(
                Locale.ROOT,
                "[%d,%.5f,%.5f,%s,%.1f,%s,%s,%s,%s,%d,%d]",
                point.sequence(),
                point.latitude(),
                point.longitude(),
                nullable(point.altitudeMeters(), "%.1f"),
                point.accuracyMeters(),
                nullable(point.speedMetersPerSecond(), "%.2f"),
                nullable(point.headingDegrees(), "%.1f"),
                nullable(point.cadenceSpm(), "%d"),
                nullable(point.currentPaceSecondsPerKm(), "%d"),
                point.recordedAt().atZone(ZoneId.systemDefault()).toEpochSecond(),
                point.pauseSegment());
    }

    private static String nullable(Number value, String format) {
        return value == null ? "null" : String.format(Locale.ROOT, format, value);
    }

    // RunningTrackRedisAdapter.parse()와 같은 형식이다
    private static List<TrackPoint> parse(String raw) {
        List<TrackPoint> points = new ArrayList<>();
        Matcher matcher = POINT.matcher(raw);
        while (matcher.find()) {
            String[] fields = matcher.group(1).split(",");
            points.add(new TrackPoint(
                    Long.parseLong(fields[0]),
                    Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]),
                    toDouble(fields[3]),
                    Double.parseDouble(fields[4]),
                    toDouble(fields[5]),
                    toDouble(fields[6]),
                    toInteger(fields[7]),
                    toInteger(fields[8]),
                    LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(Long.parseLong(fields[9])),
                            ZoneId.systemDefault()),
                    Integer.parseInt(fields[10])));
        }
        return points;
    }

    private static Double toDouble(String value) {
        return "null".equals(value) ? null : Double.valueOf(value);
    }

    private static Integer toInteger(String value) {
        return "null".equals(value) ? null : Integer.valueOf(value);
    }
}
