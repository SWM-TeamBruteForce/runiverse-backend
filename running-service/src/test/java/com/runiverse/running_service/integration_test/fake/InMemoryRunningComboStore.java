package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningComboSnapshotsPort;
import com.runiverse.running_service.application.running.port.out.RunningComboPair;
import com.runiverse.running_service.application.running.port.out.RunningComboSnapshot;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboPairsPort;
import com.runiverse.running_service.application.running.port.out.SaveRunningComboSnapshotPort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// RunningComboRedisAdapter를 대신한다 — 방 단위 해시 두 개를 그대로 흉내 내
// 저장된 게 없으면 빈 목록을 돌려주고, 저장은 넘어온 항목만 덮어쓴다
public class InMemoryRunningComboStore implements
        LoadRunningComboSnapshotsPort,
        SaveRunningComboSnapshotPort,
        LoadRunningComboPairsPort,
        SaveRunningComboPairsPort {

    // 관계는 정렬된 두 참가자로 한 칸을 가리킨다 — RunningComboPair가 정렬을 보장한다
    private record PairKey(UserId first, UserId second) {

    }

    private final Map<Long, Map<UserId, RunningComboSnapshot>> snapshots = new LinkedHashMap<>();
    private final Map<Long, Map<PairKey, RunningComboPair>> pairs = new LinkedHashMap<>();

    @Override
    public List<RunningComboSnapshot> loadSnapshots(Long runningRoomId) {
        return List.copyOf(snapshots.getOrDefault(runningRoomId, Map.of()).values());
    }

    @Override
    public void saveSnapshot(Long runningRoomId, RunningComboSnapshot snapshot) {
        snapshots.computeIfAbsent(runningRoomId, key -> new LinkedHashMap<>())
                .put(snapshot.userId(), snapshot);
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
}
