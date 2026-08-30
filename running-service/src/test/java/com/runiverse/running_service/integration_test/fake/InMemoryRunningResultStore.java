package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadRunningResultPlayersPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningSplitsPort;
import com.runiverse.running_service.application.running.port.out.LoadRunningResultRecordPort;
import com.runiverse.running_service.application.running.port.out.RunningResultPlayer;
import com.runiverse.running_service.application.running.port.out.RunningResultRecord;
import com.runiverse.running_service.application.running.port.out.RunningSplitRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.Cadence;
import com.runiverse.running_service.domain.running.metric.vo.ElevationChange;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.player.RunningPlayer;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.record.RunningSplit;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// RunningPersistenceAdapter의 조회 두 건을 대신한다 —
// 실제 어댑터처럼 참가자에 기록을 LEFT JOIN하고, 아직 안 끝난 참가자는 지표를 비워 준다
public class InMemoryRunningResultStore
        implements LoadRunningResultPlayersPort, LoadRunningResultRecordPort, LoadRunningSplitsPort {

    private final InMemoryRunningStore runningStore;
    private final InMemoryRunningRecordStore recordStore;

    public InMemoryRunningResultStore(InMemoryRunningStore runningStore,
                                      InMemoryRunningRecordStore recordStore) {
        this.runningStore = runningStore;
        this.recordStore = recordStore;
    }

    @Override
    public List<RunningResultPlayer> loadPlayers(RunningRoomId runningRoomId) {
        // 완주·이탈한 참가자도 남긴다 — deletedAt으로 거르면 대시보드가 통째로 빈다
        return runningStore.findRoom(runningRoomId.value()).stream()
                .flatMap(room -> room.getSessions().stream())
                .map(session -> runningStore.findPlayer(session.getRunningPlayerId().value()))
                .flatMap(Optional::stream)
                .map(player -> toResultPlayer(runningRoomId, player))
                .toList();
    }

    @Override
    public Optional<RunningResultRecord> loadRecord(RunningRoomId runningRoomId, UserId userId) {
        return recordStore.find(runningRoomId.value(), userId)
                .map(record -> new RunningResultRecord(
                        record.getRoutePolyline().value(),
                        record.getPeriod().startAt(),
                        record.getPeriod().endAt(),
                        record.getTotalDistance().meters(),
                        record.getTotalElevationGain()
                                .map(ElevationGain::meters).orElse(null)));
    }

    @Override
    public List<RunningSplitRow> loadSplits(RunningRoomId runningRoomId) {
        // 실제 어댑터처럼 방의 모든 기록에 딸린 구간을 한 번에 준다.
        // 구간 번호 → 참가자 순으로 정렬해 응답 순서를 고정한다
        return runningStore.findRoom(runningRoomId.value()).stream()
                .flatMap(room -> room.getSessions().stream())
                .map(session -> runningStore.findPlayer(session.getRunningPlayerId().value()))
                .flatMap(Optional::stream)
                .flatMap(player -> recordStore.find(runningRoomId.value(), player.getUserId())
                        .stream()
                        .flatMap(record -> record.getSplits().stream()
                                .map(split -> toSplitRow(player.getUserId().value(), split))))
                .sorted(Comparator.comparingInt(RunningSplitRow::splitNumber)
                        .thenComparing(RunningSplitRow::userId))
                .toList();
    }

    private RunningSplitRow toSplitRow(UUID userId, RunningSplit split) {
        return new RunningSplitRow(
                userId,
                split.getSplitNumber().value(),
                split.getDistance().meters(),
                split.getDuration().seconds(),
                split.getAvgPace().secondsPerKm(),
                split.getAvgCadence().map(Cadence::stepsPerMinute).orElse(null),
                split.getElevationChange().map(ElevationChange::meters).orElse(null),
                split.getCalories().kcal(),
                split.getRouteRange().startIndex(),
                split.getRouteRange().endIndex());
    }

    private RunningResultPlayer toResultPlayer(RunningRoomId runningRoomId, RunningPlayer player) {
        Optional<RunningRecord> record = recordStore.find(runningRoomId.value(), player.getUserId());
        if (record.isEmpty()) {
            return new RunningResultPlayer(player.getUserId().value(), player.getStatus(),
                    null, null, null, null, null, null);
        }
        RunningRecord found = record.get();
        return new RunningResultPlayer(
                player.getUserId().value(),
                player.getStatus(),
                found.getTotalDistance().meters(),
                found.getTotalDuration().seconds(),
                found.getTotalCalories().kcal(),
                found.getAvgPace().secondsPerKm(),
                found.getAvgCadence().map(Cadence::stepsPerMinute).orElse(null),
                found.getTotalElevationGain().map(ElevationGain::meters).orElse(null));
    }
}
