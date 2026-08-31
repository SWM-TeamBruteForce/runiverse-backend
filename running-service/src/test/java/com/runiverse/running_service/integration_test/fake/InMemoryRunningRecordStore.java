package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.CreateRunningRecordPort;
import com.runiverse.running_service.domain.common.vo.UserId;
import com.runiverse.running_service.domain.running.record.RunningRecord;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

// RunningRecordPersistenceAdapter를 대신한다
public class InMemoryRunningRecordStore implements CreateRunningRecordPort {

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

    // 검증 전용
    public Optional<RunningRecord> find(Long runningRoomId, UserId userId) {
        return Optional.ofNullable(records.get(new Key(runningRoomId, userId.value())));
    }

    public int size() {
        return records.size();
    }
}
