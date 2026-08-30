package com.runiverse.running_service.infrastructure.redis.running;

import com.runiverse.running_service.application.running.exception.RunningTrackUnavailableException;
import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.infrastructure.redis.RedisKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RunningTrackRedisAdapter implements AppendRunningTrackPort, LoadRunningTrackPort, DeleteRunningTrackPort {

    // 순번마다 비트 한 칸으로 중복을 판정한다 - 커서와 달리 뒤늦게 온 좌표도 자기 자리에 들어간다.
    // SETBIT은 이전 비트를 돌려주므로 0이면 처음 보는 순번이다
    private static final RedisScript<Long> APPEND = RedisScript.of("""
            local max = tonumber(ARGV[2])
            local fresh = {}
            for i = 3, #ARGV, 2 do
                local sequence = tonumber(ARGV[i])
                -- 비트맵은 offset만큼 메모리를 잡는다 - 상한 밖 순번 하나가 러닝당 수백 MB를 문다
                if sequence >= 0 and sequence <= max
                        and redis.call('SETBIT', KEYS[2], sequence, 1) == 0 then
                    fresh[#fresh + 1] = ARGV[i + 1]
                end
            end
            if #fresh == 0 then
                return 0
            end
            redis.call('XADD', KEYS[1], '*', 'points', '[' .. table.concat(fresh, ',') .. ']')
            redis.call('EXPIRE', KEYS[1], ARGV[1])
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            return #fresh
            """, Long.class);
    private final StringRedisTemplate redisTemplate;
    private final RunningTrackProperties properties;

    // Lua의 XADD가 쓰는 필드명과 같아야 한다
    private static final String POINTS_FIELD = "points";
    // 중첩이 한 겹뿐이라 대괄호 안에 대괄호가 없는 덩어리가 좌표 하나다
    private static final Pattern POINT = Pattern.compile("\\[([^\\[\\]]+)]");

    @Override
    public int append(Long runningRoomId, UserId userId, List<TrackPoint> points) {
        List<String> args = new ArrayList<>(points.size() * 2 + 2);
        args.add(String.valueOf(properties.ttl().toSeconds()));
        args.add(String.valueOf(TrackPoint.MAX_SEQUENCE));
        // 정확성은 순번별 판정이 책임진다 - 정렬은 저장 순서를 순번 순서에 맞춰
        // load()의 정렬을 거의 공짜로 만들기 위한 것이다
        points.stream()
                .sorted(Comparator.comparingLong(TrackPoint::sequence))
                .forEach(point -> {
                    args.add(String.valueOf(point.sequence()));
                    args.add(compact(point));
                });
        try {
            Long appended = redisTemplate.execute(
                    APPEND,
                    List.of(trackKey(runningRoomId, userId), seenKey(runningRoomId, userId)),
                    args.toArray());
            return appended == null ? 0 : appended.intValue();
        } catch (RuntimeException e) {
            // 이 배치는 못 담았지만 원본은 클라 로컬 트랙에 남아 있다(api-spec 5-D).
            // 재연결하면 처음 sequence부터 다시 오므로 러닝을 끊지 않고 통지만 한다
            log.warn("러닝 트랙 저장 실패 — roomId={}, userId={}", runningRoomId, userId, e);
            throw new RunningTrackUnavailableException();
        }
    }

    // 필드명을 빼고 배열로 적는다 - 좌표 하나가 230B에서 60B가 된다.
    // 좌표는 소수점 5자리(약 1m)로 자른다: GPS 실측 오차가 수 m라 그 아래는 노이즈고,
    // route_polyline도 precision 5라 다운샘플 때 정밀도가 어긋나지 않는다(erd.md)
    private String compact(TrackPoint point) {
        return String.format(
                Locale.ROOT,
                "[%d,%.5f,%.5f,%s,%.1f,%s,%s,%s,%s,%d]",
                point.sequence(),
                point.latitude(),
                point.longitude(),
                nullable(point.altitudeMeters(), "%.1f"),
                point.accuracyMeters(),
                nullable(point.speedMetersPerSecond(), "%.2f"),
                nullable(point.headingDegrees(), "%.1f"),
                nullable(point.cadenceSpm(), "%d"),
                nullable(point.currentPaceSecondsPerKm(), "%d"),
                point.recordedAt().atZone(ZoneId.systemDefault()).toEpochSecond());
    }

    // 못 잰 값은 JSON null로 적어 자리를 지킨다 — 배열이라 칸을 비우면 뒤가 밀린다.
    // %.2f에 null을 넘기면 "null"이 정밀도에 잘려 "nu"가 되므로 %s로 받는다
    private static String nullable(Number value, String format) {
        return value == null ? "null" : String.format(Locale.ROOT, format, value);
    }

    @Override
    public RunningTrack load(Long runningRoomId, UserId userId) {
        List<MapRecord<String, Object, Object>> batches;
        try {
            batches = redisTemplate.opsForStream()
                    .range(trackKey(runningRoomId, userId), Range.unbounded());
        } catch (RuntimeException e) {
            log.warn("러닝 트랙 조회 실패 — roomId={}, userId={}", runningRoomId, userId, e);
            throw new RunningTrackUnavailableException();
        }
        if (batches == null || batches.isEmpty()) {
            // 좌표를 한 번도 못 받은 러닝 — 기록 없이 상태만 확정한다(api-spec 5-D)
            return new RunningTrack("[]", List.of());
        }
        // 배치마다 바깥 [ ]를 벗겨 잇는다 - 이미 압축 포맷이라 풀었다 다시 만들 이유가 없다.
        // 구멍을 나중에 메울 수 있어 저장 순서가 순번 순서가 아니다 - 아래에서 조각째 정렬한다
        String joined = batches.stream()
                .map(batch -> (String) batch.getValue().get(POINTS_FIELD))
                .map(points -> points.substring(1, points.length() - 1))
                .collect(Collectors.joining(","));
        String raw = sortedFragments("[" + joined + "]").stream()
                .map(fragment -> "[" + fragment + "]")
                .collect(Collectors.joining(",", "[", "]"));
        return new RunningTrack(raw, parse(raw));
    }

    // compact()의 역방향 — 자리 순서가 계약이다.
    // 저장 배열은 [순번,위도,경도,고도,정확도,...]인데 TrackPoint 생성자는 정확도가 고도보다 앞이다
    private List<TrackPoint> parse(String raw) {
        List<TrackPoint> points = new ArrayList<>();
        Matcher matcher = POINT.matcher(raw);
        while (matcher.find()) {
            String[] fields = matcher.group(1).split(",");
            points.add(new TrackPoint(
                    Long.parseLong(fields[0]),          // 순번
                    Double.parseDouble(fields[1]),      // 위도
                    Double.parseDouble(fields[2]),
                    toDouble(fields[3]),// 경도
                    Double.parseDouble(fields[4]),      // 정확도 ← 배열 5번째// 고도   ← 배열 4번째
                    toDouble(fields[5]),
                    toDouble(fields[6]),
                    toInteger(fields[7]),
                    toInteger(fields[8]),
                    LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(Long.parseLong(fields[9])),
                            ZoneId.systemDefault())));
        }
        return points;
    }

    // 구멍을 나중에 메울 수 있게 되면서 저장 순서가 곧 순번 순서가 아니다.
    // 압축 문자열을 풀었다 다시 만들지 않고 조각째 옮긴다
    private List<String> sortedFragments(String raw) {
        List<String> fragments = new ArrayList<>();
        Matcher matcher = POINT.matcher(raw);
        while (matcher.find()) {
            fragments.add(matcher.group(1));
        }
        fragments.sort(Comparator.comparingLong(
                fragment -> Long.parseLong(fragment.substring(0, fragment.indexOf(',')))));
        return fragments;
    }

    // 종료 확정 뒤 버퍼를 비운다. TTL이 있어 남겨도 새지는 않지만,
    // 끝난 러닝에 재연결 재전송이 다시 쌓이는 걸 막는다.
    // 실패해도 종료를 되돌리지 않는다 — 이미 기록은 DB에 있고 TTL이 결국 지운다
    @Override
    public void delete(Long runningRoomId, UserId userId) {
        try {
            redisTemplate.delete(List.of(
                    trackKey(runningRoomId, userId), seenKey(runningRoomId, userId)));
        } catch (RuntimeException e) {
            log.warn("러닝 트랙 삭제 실패 — roomId={}, userId={}", runningRoomId, userId, e);
        }
    }

    // 못 잰 값은 "null"로 적혀 있다 — nullable()의 역방향
    private static Double toDouble(String value) {
        return "null".equals(value) ? null : Double.valueOf(value);
    }

    private static Integer toInteger(String value) {
        return "null".equals(value) ? null : Integer.valueOf(value);
    }

    private String trackKey(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_TRACK.of(String.valueOf(runningRoomId), userId.value().toString());
    }

    // 순번 비트맵 — 예전 커서(:seq)와 값 형식이 달라 키를 갈라야 한다.
    // 숫자 문자열에 SETBIT을 걸면 그 글자의 비트가 이미 켜진 것으로 읽힌다
    private String seenKey(Long runningRoomId, UserId userId) {
        return RedisKey.RUNNING_TRACK.of(
                String.valueOf(runningRoomId), userId.value().toString(), "seen");
    }
}
