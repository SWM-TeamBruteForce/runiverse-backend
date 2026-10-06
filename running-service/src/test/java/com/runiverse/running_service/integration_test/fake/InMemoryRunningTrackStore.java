package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.AppendRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.DeleteRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningTrackPort;
import com.runiverse.running_service.application.running.port.out.RunningTrack;
import com.runiverse.running_service.application.running.port.out.TrackPoint;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// RunningTrackRedisAdapter를 대신한다 — 키가 (runningRoomId, userId)이고
// 순번마다 독립으로 중복을 거르는 것까지 실제와 맞춘다(재연결 재전송이 트랙을 부풀리지 않는다).
// 실제 어댑터는 Redis 비트맵이 그 역할을 하고, 여기서는 Map이 대신한다
public class InMemoryRunningTrackStore implements AppendRunningTrackPort, LoadRunningTrackPort,
        DeleteRunningTrackPort {

    private record Key(Long runningRoomId, UserId userId) {

    }

    private final Map<Key, Map<Long, TrackPoint>> tracks = new LinkedHashMap<>();

    @Override
    public int append(Long runningRoomId, UserId userId, List<TrackPoint> points) {
        Map<Long, TrackPoint> stored = tracks.computeIfAbsent(
                new Key(runningRoomId, userId), key -> new LinkedHashMap<>());
        int added = 0;
        for (TrackPoint point : points) {
            if (stored.putIfAbsent(point.sequence(), point) == null) {
                added++;
            }
        }
        return added;
    }

    // 구멍을 나중에 메울 수 있으므로 저장 순서가 곧 순번 순서가 아니다 —
    // 실제 어댑터의 load()와 같이 순번으로 정렬해 돌려준다
    @Override
    public RunningTrack load(Long runningRoomId, UserId userId) {
        List<TrackPoint> points = tracks.getOrDefault(new Key(runningRoomId, userId), Map.of())
                .values().stream()
                .sorted(Comparator.comparingLong(TrackPoint::sequence))
                .toList();
        return new RunningTrack(compact(points), points);
    }

    @Override
    public void delete(Long runningRoomId, UserId userId) {
        tracks.remove(new Key(runningRoomId, userId));
    }

    // 어댑터가 Redis에 담는 압축 배열과 같은 자리 순서다(TrackPoint.COMPACT_FIELDS).
    // S3로 그대로 흘러가는 값이라 형태만 맞으면 된다
    private String compact(List<TrackPoint> points) {
        return points.stream()
                .map(point -> "[%d,%s,%s,%s,%s,%s,%s,%s,%s,%d]".formatted(
                        point.sequence(), point.latitude(), point.longitude(),
                        point.altitudeMeters(), point.accuracyMeters(),
                        point.speedMetersPerSecond(), point.headingDegrees(),
                        point.cadenceSpm(), point.currentPaceSecondsPerKm(),
                        point.recordedAt().toEpochSecond(ZoneOffset.UTC)))
                .collect(Collectors.joining(",", "[", "]"));
    }

    // 검증 전용 — 종료 뒤 버퍼가 비었는지 본다
    public boolean isEmpty(Long runningRoomId, UserId userId) {
        return !tracks.containsKey(new Key(runningRoomId, userId));
    }
}
