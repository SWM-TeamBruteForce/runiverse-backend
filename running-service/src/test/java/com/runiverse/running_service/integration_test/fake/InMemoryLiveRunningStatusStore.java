package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.ChangeLiveRunningStatusPort;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatus;
import com.runiverse.running_service.application.running.port.out.LiveRunningStatusChange;
import com.runiverse.running_service.application.running.port.out.LoadLiveRunningStatusPort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

// LiveRunningStatusRedisAdapter를 대신한다 — Lua가 하는 대조(같으면 그대로, 허용되면 쓰기)를
// 같은 규칙(canChangeFrom)으로 흉내 내고, 바꾸기 전 값으로 결과를 만드는 것까지 실제와 맞춘다
public class InMemoryLiveRunningStatusStore
        implements ChangeLiveRunningStatusPort, LoadLiveRunningStatusPort {

    private record Key(Long runningRoomId, UserId userId) {

    }

    private final Map<Key, LiveRunningStatus> statuses = new LinkedHashMap<>();

    @Override
    public LiveRunningStatusChange change(Long runningRoomId, UserId userId, LiveRunningStatus status) {
        Key key = new Key(runningRoomId, userId);
        LiveRunningStatus previous = statuses.get(key);
        LiveRunningStatusChange change = LiveRunningStatusChange.of(previous, status);
        if (change.changed()) {
            statuses.put(key, change.current());
        }
        return change;
    }

    @Override
    public Optional<LiveRunningStatus> load(Long runningRoomId, UserId userId) {
        return Optional.ofNullable(statuses.get(new Key(runningRoomId, userId)));
    }
}
