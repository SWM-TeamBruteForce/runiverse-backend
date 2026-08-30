package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.LoadRunningDistancePort;
import com.runiverse.running_service.application.running.port.out.RunningDistance;
import com.runiverse.running_service.application.running.port.out.SaveRunningDistancePort;
import com.runiverse.running_service.domain.common.vo.UserId;

import java.util.LinkedHashMap;
import java.util.Map;

// RunningDistanceRedisAdapter를 대신한다 — 키가 (runningRoomId, userId)이고
// 저장된 게 없으면 빈 값을 돌려주는 것까지 실제와 맞춘다
public class InMemoryRunningDistanceStore
        implements LoadRunningDistancePort, SaveRunningDistancePort {

    private record Key(Long runningRoomId, UserId userId) {

    }

    private final Map<Key, RunningDistance> distances = new LinkedHashMap<>();

    @Override
    public RunningDistance loadDistance(Long runningRoomId, UserId userId) {
        return distances.getOrDefault(new Key(runningRoomId, userId), RunningDistance.empty());
    }

    @Override
    public void saveDistance(Long runningRoomId, UserId userId, RunningDistance distance) {
        distances.put(new Key(runningRoomId, userId), distance);
    }
}
