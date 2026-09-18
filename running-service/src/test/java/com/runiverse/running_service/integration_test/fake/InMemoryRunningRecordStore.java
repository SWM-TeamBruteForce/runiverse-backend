package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.ExistsRunningRecordPort;
import com.runiverse.running_service.application.running.port.out.LoadRecentRunningPacesPort;
import com.runiverse.running_service.application.running.port.out.RecentRunningPace;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.record.RunningRecord;
import com.runiverse.running_service.domain.running.room.vo.RunningRoomId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// RunningRecordPersistenceAdapter를 대신한다
public class InMemoryRunningRecordStore
        implements CreateRunningRecordPort, ExistsRunningRecordPort, LoadRecentRunningPacesPort {

    private record Key(Long runningRoomId, UUID userId) {

    }

    private final Map<Key, RunningRecord> records = new LinkedHashMap<>();

    @Override
    public void create(RunningRecord record) {
        if (!record.isNew()) {
            throw new IllegalStateException("이미 저장된 기록이다 — 기록은 write-once다");
        }
        Key key = new Key(record.getRunningRoomId().value(), record.getUserId().value());
        // UNIQUE (running_room_id, user_id) — 유저당 방별 1기록.
        // 종료가 두 번 확정되면 여기서 터진다
        if (records.putIfAbsent(key, record) != null) {
            throw new IllegalStateException("이미 이 방의 기록이 있다");
        }
    }

    @Override
    public List<RecentRunningPace> loadRecent(UserId userId, int limit) {
        // 어댑터는 ORDER BY start_at DESC, running_record_id DESC로 읽는다.
        // 삽입 순서를 뒤집어 두고 안정 정렬을 걸어야 start_at이 겹칠 때도 같은 순서가 된다 —
        // 나중에 저장한 기록이 곧 id가 큰 기록이다
        List<RunningRecord> mine = new ArrayList<>(records.values().stream()
                .filter(record -> record.getUserId().equals(userId))
                .toList());
        Collections.reverse(mine);
        return mine.stream()
                .sorted(Comparator.comparing(
                        (RunningRecord record) -> record.getPeriod().startAt()).reversed())
                .limit(limit)
                .map(record -> new RecentRunningPace(
                        record.getTotalDistance().meters(), record.getTotalDuration().seconds()))
                .toList();
    }

    @Override
    public boolean existsInRoom(RunningRoomId runningRoomId) {
        return records.keySet().stream()
                .anyMatch(key -> key.runningRoomId().equals(runningRoomId.value()));
    }

    // 검증 전용
    public Optional<RunningRecord> find(Long runningRoomId, UserId userId) {
        return Optional.ofNullable(records.get(new Key(runningRoomId, userId.value())));
    }

    public int size() {
        return records.size();
    }
}
