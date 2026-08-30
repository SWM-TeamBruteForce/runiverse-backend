package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.PublishRunningProgressPort;
import com.runiverse.running_service.application.running.port.out.RunningProgress;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// RunningProgressRedisAdapter를 대신한다 — 인스턴스 밖으로 나가는 발행을 막고
// 무엇이 나갔는지 방별로 쌓아 둔다. 실제 어댑터처럼 실패해도 던지지 않는다
public class FakeRunningProgressPublisher implements PublishRunningProgressPort {

    private final Map<Long, List<RunningProgress>> published = new LinkedHashMap<>();

    @Override
    public void publish(Long runningRoomId, RunningProgress progress) {
        published.computeIfAbsent(runningRoomId, key -> new ArrayList<>()).add(progress);
    }

    // 검증 전용 — 그 방에 나간 진행 정보를 발행 순서대로 돌려준다
    public List<RunningProgress> publishedIn(Long runningRoomId) {
        return List.copyOf(published.getOrDefault(runningRoomId, List.of()));
    }
}
