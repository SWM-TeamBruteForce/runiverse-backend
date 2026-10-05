package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadMyRunningRecordsPort;
import com.runiverse.running_service.application.running.port.out.RunningRecordRow;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.metric.vo.ElevationGain;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.room.RunningRoom;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

// RunningRecordPersistenceAdapter의 기록 목록 조회를 대신한다 — 방 숨김(deleted_at)은 도메인에 없어 걸러내지 않는다
public class InMemoryRunningRecordListStore implements LoadMyRunningRecordsPort {

    private final InMemoryRunningStore runningStore;
    private final InMemoryRunningRecordStore recordStore;

    public InMemoryRunningRecordListStore(InMemoryRunningStore runningStore,
                                          InMemoryRunningRecordStore recordStore) {
        this.runningStore = runningStore;
        this.recordStore = recordStore;
    }

    @Override
    public List<RunningRecordRow> loadByPeriod(UserId userId, LocalDateTime startInclusive,
                                               LocalDateTime endExclusive) {
        List<RunningRecord> all = recordStore.findAll();
        List<RunningRecordRow> rows = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            RunningRecord record = all.get(index);
            LocalDateTime startAt = record.getPeriod().startAt();
            if (!record.getUserId().equals(userId)
                    || startAt.isBefore(startInclusive) || !startAt.isBefore(endExclusive)) {
                continue;
            }
            // 기록 저장 포트가 id를 돌려주지 않아 저장 순서로 매긴다
            rows.add(toRow(index + 1L, record));
        }
        // 실제 쿼리의 ORDER BY start_at, running_record_id
        rows.sort(Comparator.comparing(RunningRecordRow::startedAt)
                .thenComparing(RunningRecordRow::runningRecordId));
        return rows;
    }

    private RunningRecordRow toRow(Long runningRecordId, RunningRecord record) {
        RunningRoom room = runningStore.findRoom(record.getRunningRoomId().value()).orElseThrow();
        return new RunningRecordRow(
                runningRecordId,
                record.getRunningRoomId().value(),
                room.getType(),
                startedPlayerCount(room),
                record.getPeriod().startAt(),
                record.getTotalDistance().meters(),
                record.getTotalDuration().seconds(),
                record.getAvgPace().secondsPerKm(),
                record.getTotalElevationGain().map(ElevationGain::meters).orElse(null),
                record.getRoutePolyline().value());
    }

    private long startedPlayerCount(RunningRoom room) {
        return room.getSessions().stream()
                .map(session -> runningStore.findPlayer(session.getRunningPlayerId().value()))
                .flatMap(Optional::stream)
                .filter(player -> player.getStatus().hasStartedRunning())
                .count();
    }
}
