package com.runiverse.running_service.integration_test.fake;

import com.runiverse.running_service.application.running.port.out.PublishRunningComboPort;
import com.runiverse.running_service.application.running.port.out.RunningComboUpdate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 콤보 발행을 인스턴스 밖으로 내보내지 않고 방별로 쌓아 둔다.
// 실제 어댑터처럼 실패해도 던지지 않는다
public class FakeRunningComboPublisher implements PublishRunningComboPort {

    private final Map<Long, List<RunningComboUpdate>> published = new LinkedHashMap<>();

    @Override
    public void publish(Long runningRoomId, RunningComboUpdate update) {
        published.computeIfAbsent(runningRoomId, key -> new ArrayList<>()).add(update);
    }

    // 검증 전용 — 그 방에 나간 통을 발행 순서대로 돌려준다
    public List<RunningComboUpdate> publishedIn(Long runningRoomId) {
        return List.copyOf(published.getOrDefault(runningRoomId, List.of()));
    }
}
