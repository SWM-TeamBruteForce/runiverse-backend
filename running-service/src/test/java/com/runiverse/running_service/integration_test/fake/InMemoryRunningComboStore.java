package com.runiverse.running_service.integration_test.fake;

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

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// RunningComboRedisAdapter를 대신한다 — 방 단위 해시 셋과 일시정지·종료 칸을 그대로 흉내 내
// 저장된 게 없으면 빈 목록을 돌려주고, 저장은 넘어온 항목만 덮어쓴다
public class InMemoryRunningComboStore implements
        LoadRunningComboSnapshotsPort,
        SaveRunningComboSnapshotPort,
        LoadRunningComboPairsPort,
        SaveRunningComboPairsPort,
        SaveRunningComboSegmentsPort,
        MarkRunningComboPausedPort,
        MarkRunningComboFinishedPort,
        LoadRunningComboHistoryPort,
        DeleteRunningComboStatePort {

    // 관계는 정렬된 두 참가자로 한 칸을 가리킨다 — RunningComboPair가 정렬을 보장한다
    private record PairKey(UserId first, UserId second) {

    }

    // 구간은 쌍과 시작 시각으로 한 칸을 가리킨다 — 같은 구간을 두 번 닫아도 한 칸이다
    private record SegmentKey(UserId first, UserId second, Instant startAt) {

    }

    private final Map<Long, Map<UserId, RunningComboSnapshot>> snapshots = new LinkedHashMap<>();
    private final Map<Long, Set<UserId>> paused = new LinkedHashMap<>();
    private final Map<Long, Set<UserId>> finished = new LinkedHashMap<>();
    private final Map<Long, Map<PairKey, RunningComboPair>> pairs = new LinkedHashMap<>();
    private final Map<Long, Map<SegmentKey, RunningComboSegment>> segments = new LinkedHashMap<>();

    // 일시정지·종료 여부는 스냅샷 값이 아니라 별도 칸에서 읽어 붙인다 — 실제 어댑터와 같다
    @Override
    public List<RunningComboSnapshot> loadSnapshots(Long runningRoomId) {
        Set<UserId> pausedUsers = paused.getOrDefault(runningRoomId, Set.of());
        Set<UserId> finishedUsers = finished.getOrDefault(runningRoomId, Set.of());
        return snapshots.getOrDefault(runningRoomId, Map.of()).values().stream()
                .map(snapshot -> new RunningComboSnapshot(snapshot.userId(), snapshot.meters(),
                        snapshot.recordedAt(), snapshot.speedMetersPerSecond(), snapshot.movedAt(),
                        snapshot.observedAt(), pausedUsers.contains(snapshot.userId()),
                        finishedUsers.contains(snapshot.userId())))
                .toList();
    }

    @Override
    public void saveSnapshot(Long runningRoomId, RunningComboSnapshot snapshot) {
        snapshots.computeIfAbsent(runningRoomId, key -> new LinkedHashMap<>())
                .put(snapshot.userId(), snapshot);
    }

    @Override
    public void markPaused(Long runningRoomId, UserId userId) {
        paused.computeIfAbsent(runningRoomId, key -> new LinkedHashSet<>()).add(userId);
    }

    @Override
    public void clearPaused(Long runningRoomId, UserId userId) {
        paused.getOrDefault(runningRoomId, new LinkedHashSet<>()).remove(userId);
    }

    @Override
    public void markFinished(Long runningRoomId, UserId userId) {
        finished.computeIfAbsent(runningRoomId, key -> new LinkedHashSet<>()).add(userId);
    }

    @Override
    public List<RunningComboPair> loadPairs(Long runningRoomId) {
        return List.copyOf(pairs.getOrDefault(runningRoomId, Map.of()).values());
    }

    @Override
    public void savePairs(Long runningRoomId, List<RunningComboPair> saved) {
        Map<PairKey, RunningComboPair> room =
                pairs.computeIfAbsent(runningRoomId, key -> new LinkedHashMap<>());
        // 넘어오지 않은 관계는 건드리지 않는다 — 실제 어댑터의 HSET도 그 필드만 덮는다
        saved.forEach(pair -> room.put(new PairKey(pair.first(), pair.second()), pair));
    }

    @Override
    public void saveSegments(Long runningRoomId, List<RunningComboSegment> saved) {
        Map<SegmentKey, RunningComboSegment> room =
                segments.computeIfAbsent(runningRoomId, key -> new LinkedHashMap<>());
        saved.forEach(segment -> room.put(
                new SegmentKey(segment.first(), segment.second(), segment.startAt()), segment));
    }

    // 이어지던 관계는 시작 시각이 있는 것만 담는다 — 실제 어댑터와 같다
    @Override
    public RunningComboHistory loadHistory(Long runningRoomId) {
        return new RunningComboHistory(
                segmentsOf(runningRoomId),
                loadPairs(runningRoomId).stream().filter(RunningComboPair::inCombo).toList());
    }

    @Override
    public void deleteComboState(Long runningRoomId) {
        snapshots.remove(runningRoomId);
        paused.remove(runningRoomId);
        finished.remove(runningRoomId);
        pairs.remove(runningRoomId);
        segments.remove(runningRoomId);
    }

    // 방의 콤보 상태가 남아 있는지 — 정리 확인용
    public boolean hasStateOf(Long runningRoomId) {
        return snapshots.containsKey(runningRoomId) || pairs.containsKey(runningRoomId)
                || segments.containsKey(runningRoomId);
    }

    // 테스트가 끊긴 구간을 확인할 때 쓴다
    public List<RunningComboSegment> segmentsOf(Long runningRoomId) {
        return List.copyOf(segments.getOrDefault(runningRoomId, Map.of()).values());
    }
}
